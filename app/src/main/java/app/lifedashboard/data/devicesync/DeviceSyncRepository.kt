package app.lifedashboard.data.devicesync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lifedashboard.core.security.PasswordCrypto
import app.lifedashboard.core.security.SecretStore
import app.lifedashboard.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class DeviceSyncSettings(
    val deviceId: String,
    val deviceName: String,
    val fileUri: String?,
    val lanEnabled: Boolean,
    val hasPassword: Boolean,
    val lastFileSync: Long,
    val lastFileMessage: String?,
    val lastLanSync: Long,
    val lastLanMessage: String?,
)

/** Rahmen für Abgleich-Daten (Datei & WLAN): „LDS1“ + Länge + verschlüsselte Daten. */
object SyncFrame {
    private val MAGIC = "LDS1".toByteArray()
    const val MAX_BYTES = 64 * 1024 * 1024

    fun write(out: OutputStream, payload: ByteArray) {
        val d = DataOutputStream(out)
        d.write(MAGIC)
        d.writeInt(payload.size)
        d.write(payload)
        d.flush()
    }

    /** null = leere/fremde Datei */
    fun read(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        val magic = ByteArray(4)
        try {
            d.readFully(magic)
        } catch (e: java.io.EOFException) {
            return null
        }
        if (!magic.contentEquals(MAGIC)) return null
        val len = d.readInt()
        require(len in 1..MAX_BYTES) { "Ungültige Abgleich-Daten" }
        return ByteArray(len).also { d.readFully(it) }
    }

    fun toBytes(payload: ByteArray): ByteArray = ByteArrayOutputStream().also { write(it, payload) }.toByteArray()
}

/**
 * Abgleich Handy ↔ Tablet:
 * 1. über eine verschlüsselte Datei in einem Cloud-Ordner (Google Drive, OneDrive …), automatisch
 * 2. direkt im WLAN (siehe [LanSyncManager])
 *
 * Beides nutzt dasselbe Abgleich-Passwort (im Android Keystore gespeichert, nie im Klartext).
 */
@Singleton
class DeviceSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val secrets: SecretStore,
) : SyncPeer {
    private val mutex = Mutex()

    @Volatile private var cachedDeviceId: String? = null

    /** Zeitpunkt des letzten Einarbeitens fremder Daten (damit sie nicht sofort zurückgeschickt werden) */
    @Volatile var lastMergeAt: Long = 0L
        private set

    private val engine = SyncEngine(db) { deviceIdBlocking() }

    val settings: Flow<DeviceSyncSettings> = dataStore.data.map { p ->
        DeviceSyncSettings(
            deviceId = p[DEVICE_ID] ?: "",
            deviceName = p[DEVICE_NAME] ?: defaultName(),
            fileUri = p[FILE_URI],
            lanEnabled = p[LAN_ENABLED] ?: true,
            hasPassword = secrets.get(PASSWORD) != null,
            lastFileSync = p[LAST_FILE] ?: 0L,
            lastFileMessage = p[LAST_FILE_MSG],
            lastLanSync = p[LAST_LAN] ?: 0L,
            lastLanMessage = p[LAST_LAN_MSG],
        )
    }

    private fun defaultName() = Build.MODEL ?: "Gerät"

    suspend fun deviceId(): String {
        cachedDeviceId?.let { return it }
        val existing = dataStore.data.first()[DEVICE_ID]
        val id = existing ?: UUID.randomUUID().toString().replace("-", "").take(16).also { new -> dataStore.edit { it[DEVICE_ID] = new } }
        cachedDeviceId = id
        return id
    }

    private fun deviceIdBlocking(): String = cachedDeviceId ?: runBlocking { deviceId() }

    suspend fun deviceName(): String = dataStore.data.first()[DEVICE_NAME] ?: defaultName()

    fun password(): String? = secrets.get(PASSWORD)

    suspend fun setPassword(value: String) {
        secrets.put(PASSWORD, value)
        dataStore.edit { it[PW_CHANGED] = System.currentTimeMillis() } // damit die Anzeige sich aktualisiert
    }

    suspend fun setDeviceName(name: String) = dataStore.edit { it[DEVICE_NAME] = name.trim().ifBlank { defaultName() } }

    suspend fun setLanEnabled(on: Boolean) = dataStore.edit { it[LAN_ENABLED] = on }

    /** Datei aus dem Auswahl-Dialog merken (Zugriff bleibt nach Neustart erhalten). */
    suspend fun setFile(uri: Uri?) {
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        dataStore.edit { if (uri == null) it.remove(FILE_URI) else it[FILE_URI] = uri.toString() }
    }

    // ------------------------------------------------------------------ verschlüsselte Schnappschüsse

    override suspend fun encodedSnapshot(): ByteArray = withContext(Dispatchers.IO) {
        val pw = password() ?: error("Kein Abgleich-Passwort gesetzt")
        deviceId()
        val json = mutex.withLock { engine.snapshot(deviceName()) }
        PasswordCrypto.encrypt(json.toString().toByteArray(), pw)
    }

    /** Fremde Daten entschlüsseln und einarbeiten. Liefert Ergebnis + Name des anderen Geräts. */
    override suspend fun mergeEncoded(bytes: ByteArray): Pair<MergeResult, String> = withContext(Dispatchers.IO) {
        val pw = password() ?: error("Kein Abgleich-Passwort gesetzt")
        deviceId()
        val json = JSONObject(String(PasswordCrypto.decrypt(bytes, pw)))
        val result = mutex.withLock { engine.merge(json) }
        if (result.changes > 0) lastMergeAt = System.currentTimeMillis()
        result to json.optString("deviceName", "anderes Gerät")
    }

    // ------------------------------------------------------------------ Cloud-Datei

    /**
     * Datei lesen → einarbeiten → eigenen Stand zurückschreiben. Die Datei enthält danach die
     * Daten beider Geräte. Gibt eine kurze Meldung zurück (auch bei Fehlern, nie Absturz).
     */
    suspend fun syncFile(): String = withContext(Dispatchers.IO) {
        val s = settings.first()
        val uri = s.fileUri?.let(Uri::parse) ?: return@withContext "Keine Abgleich-Datei gewählt."
        if (password() == null) return@withContext "Kein Abgleich-Passwort gesetzt."
        val message = try {
            val incoming = context.contentResolver.openInputStream(uri)?.use { SyncFrame.read(it) }
            val (result, from) = incoming?.let { mergeEncoded(it) } ?: (MergeResult() to "")
            val out = SyncFrame.toBytes(encodedSnapshot())
            val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: context.contentResolver.openOutputStream(uri, "w")
                ?: error("Datei kann nicht geschrieben werden")
            stream.use { it.write(out) }
            describe(result, from)
        } catch (e: PasswordCrypto.WrongPasswordException) {
            "Falsches Abgleich-Passwort – auf beiden Geräten muss dasselbe stehen."
        } catch (e: SyncVersionException) {
            e.message ?: "Unterschiedliche App-Versionen."
        } catch (e: SecurityException) {
            "Kein Zugriff mehr auf die Datei – bitte neu auswählen."
        } catch (e: Exception) {
            "Abgleich fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
        }
        dataStore.edit {
            it[LAST_FILE] = System.currentTimeMillis()
            it[LAST_FILE_MSG] = message
        }
        message
    }

    suspend fun recordLan(message: String) = dataStore.edit {
        it[LAST_LAN] = System.currentTimeMillis()
        it[LAST_LAN_MSG] = message
    }

    companion object {
        private const val PASSWORD = "device_sync_password"
        private val DEVICE_ID = stringPreferencesKey("sync_device_id")
        private val DEVICE_NAME = stringPreferencesKey("sync_device_name")
        private val FILE_URI = stringPreferencesKey("sync_file_uri")
        private val LAN_ENABLED = booleanPreferencesKey("sync_lan_enabled")
        private val LAST_FILE = longPreferencesKey("sync_last_file")
        private val LAST_FILE_MSG = stringPreferencesKey("sync_last_file_msg")
        private val LAST_LAN = longPreferencesKey("sync_last_lan")
        private val LAST_LAN_MSG = stringPreferencesKey("sync_last_lan_msg")
        private val PW_CHANGED = longPreferencesKey("sync_pw_changed")

        fun describe(r: MergeResult, from: String): String = when {
            r.changes == 0 -> "Alles aktuell ✓"
            else -> listOfNotNull(
                r.inserted.takeIf { it > 0 }?.let { "$it neu" },
                r.updated.takeIf { it > 0 }?.let { "$it geändert" },
                r.deleted.takeIf { it > 0 }?.let { "$it gelöscht" },
            ).joinToString(", ") + (if (from.isNotBlank()) " von $from" else "") + " ✓"
        }
    }
}
