package app.lifedashboard.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lifedashboard.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Verschlüsseltes Backup aller Daten – auch zum Übertragen aufs Tablet.
 *
 * Format: „LDB1“ + Salt (16) + IV (12) + AES-256-GCM( gzip( JSON ) ).
 * Der Schlüssel wird aus deinem Passwort abgeleitet (PBKDF2, 210.000 Runden).
 * Ohne Passwort ist die Datei wertlos – auch für Google Drive o. Ä.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val dataStore: DataStore<Preferences>,
) {
    class WrongPasswordException : Exception("Falsches Passwort oder beschädigte Datei.")
    class VersionMismatchException(val fileVersion: Int, val appVersion: Int) :
        Exception("Backup ist von Datenbank-Version $fileVersion, die App hat Version $appVersion. Bitte beide Geräte auf die gleiche App-Version bringen.")

    // sync_meta gehört zum Geräte-Abgleich und wird nach einem Restore automatisch neu aufgebaut
    private val skippedTables = setOf("room_master_table", "android_metadata", "sqlite_sequence", "sync_meta")
    private val skippedPrefs = setOf("sent_notifications")

    suspend fun export(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val bytes = encrypt(buildJson().toString().toByteArray(), password)
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Datei konnte nicht geschrieben werden.")
    }

    /** @return Anzahl importierter Datensätze */
    suspend fun import(uri: Uri, password: String): Int = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Datei konnte nicht gelesen werden.")
        val json = JSONObject(String(decrypt(bytes, password)))
        val fileVersion = json.getInt("dbVersion")
        val appVersion = db.openHelper.readableDatabase.version
        if (fileVersion != appVersion) throw VersionMismatchException(fileVersion, appVersion)
        val count = restoreTables(json.getJSONObject("tables"))
        restorePrefs(json.getJSONObject("prefs"))
        db.invalidationTracker.refreshVersionsAsync()
        count
    }

    private suspend fun buildJson(): JSONObject {
        val sqlite = db.openHelper.readableDatabase
        val tables = JSONObject()
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(0)) }.filter { it !in skippedTables }
            names.forEach { table ->
                val rows = JSONArray()
                sqlite.query("SELECT * FROM `$table`").use { rc ->
                    while (rc.moveToNext()) {
                        val row = JSONObject()
                        for (i in 0 until rc.columnCount) {
                            val name = rc.getColumnName(i)
                            when (rc.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                                Cursor.FIELD_TYPE_INTEGER -> row.put(name, rc.getLong(i))
                                Cursor.FIELD_TYPE_FLOAT -> row.put(name, rc.getDouble(i))
                                Cursor.FIELD_TYPE_STRING -> row.put(name, rc.getString(i))
                                else -> Unit // Blobs nutzen wir nicht
                            }
                        }
                        rows.put(row)
                    }
                }
                tables.put(table, rows)
            }
        }
        val prefs = JSONObject()
        dataStore.data.first().asMap().forEach { (key, value) ->
            // Abgleich-Einstellungen (Geräte-ID!) gehören nur zu diesem Gerät
            if (key.name in skippedPrefs || key.name.startsWith("sync_")) return@forEach
            val type = when (value) {
                is Boolean -> "b"
                is Int -> "i"
                is Long -> "l"
                is String -> "s"
                else -> null
            } ?: return@forEach
            prefs.put(key.name, JSONObject().put("t", type).put("v", value))
        }
        return JSONObject()
            .put("format", 1)
            .put("dbVersion", sqlite.version)
            .put("createdAt", System.currentTimeMillis())
            .put("tables", tables)
            .put("prefs", prefs)
    }

    private fun restoreTables(tables: JSONObject): Int {
        val sqlite = db.openHelper.writableDatabase
        var count = 0
        sqlite.beginTransaction()
        try {
            tables.keys().forEach { table ->
                if (table in skippedTables) return@forEach
                sqlite.execSQL("DELETE FROM `$table`")
                val rows = tables.getJSONArray(table)
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val values = ContentValues()
                    row.keys().forEach { col ->
                        when (val v = row.get(col)) {
                            JSONObject.NULL -> values.putNull(col)
                            is Int -> values.put(col, v.toLong())
                            is Long -> values.put(col, v)
                            is Double -> values.put(col, v)
                            is Boolean -> values.put(col, if (v) 1 else 0)
                            else -> values.put(col, v.toString())
                        }
                    }
                    sqlite.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                    count++
                }
            }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
        return count
    }

    private suspend fun restorePrefs(prefs: JSONObject) {
        dataStore.edit { store ->
            prefs.keys().forEach { name ->
                if (name.startsWith("sync_")) return@forEach
                val entry = prefs.getJSONObject(name)
                when (entry.getString("t")) {
                    "b" -> store[booleanPreferencesKey(name)] = entry.getBoolean("v")
                    "i" -> store[intPreferencesKey(name)] = entry.getInt("v")
                    "l" -> store[longPreferencesKey(name)] = entry.getLong("v")
                    "s" -> store[stringPreferencesKey(name)] = entry.getString("v")
                }
            }
        }
    }

    private fun encrypt(plain: ByteArray, password: String) = app.lifedashboard.core.security.PasswordCrypto.encrypt(plain, password)

    private fun decrypt(data: ByteArray, password: String): ByteArray = try {
        app.lifedashboard.core.security.PasswordCrypto.decrypt(data, password)
    } catch (e: app.lifedashboard.core.security.PasswordCrypto.WrongPasswordException) {
        throw WrongPasswordException()
    }
}
