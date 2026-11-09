package app.lifedashboard.data.devicesync

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lifedashboard.data.db.AppDatabase
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Ergebnis eines Abgleichs. */
data class MergeResult(val inserted: Int = 0, val updated: Int = 0, val deleted: Int = 0, val skipped: Int = 0) {
    val changes: Int get() = inserted + updated + deleted
    operator fun plus(o: MergeResult) = MergeResult(inserted + o.inserted, updated + o.updated, deleted + o.deleted, skipped + o.skipped)
}

class SyncVersionException(val remote: Int, val local: Int) :
    Exception("Das andere Gerät hat eine andere App-Version (Datenbank $remote statt $local). Bitte auf beiden Geräten dieselbe Version installieren.")

/**
 * Abgleich Handy ↔ Tablet ohne Server: Jedes Gerät erzeugt einen „Schnappschuss“ seiner Daten
 * (mit geräteübergreifenden Kennungen und Änderungszeitpunkten) und führt fremde Schnappschüsse
 * Eintrag für Eintrag zusammen:
 *
 * - neu auf dem anderen Gerät → wird angelegt
 * - auf beiden vorhanden → die NEUERE Änderung gewinnt
 * - auf dem anderen Gerät gelöscht (und dort neuer) → wird auch hier gelöscht
 *
 * Verweise (z. B. Thema → Klausur) werden über die Kennungen übersetzt, weil jedes Gerät
 * eigene interne Nummern hat. Reine SQL-Ebene – die restliche App merkt nichts davon.
 */
class SyncEngine(private val db: AppDatabase, private val deviceId: () -> String) {
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    // ------------------------------------------------------------------ Kennungen

    /** Vergibt allen Einträgen ohne Kennung eine (aus ihren Merkmalen → auf beiden Geräten gleich). */
    fun assignGids() {
        val s = sql
        s.beginTransaction()
        try {
            for (t in SyncSchema.tables) {
                // Sicherheitsnetz: jeder Eintrag hat einen Meta-Datensatz
                s.execSQL("INSERT OR IGNORE INTO sync_meta(tbl, localId, gid, updatedAt, deleted) SELECT '${t.name}', rowid, NULL, 0, 0 FROM `${t.name}`")
                val missing = s.query("SELECT localId FROM sync_meta WHERE tbl = ? AND gid IS NULL AND deleted = 0 ORDER BY localId", arrayOf(t.name))
                    .use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
                if (missing.isEmpty()) continue
                val used = s.query("SELECT gid FROM sync_meta WHERE tbl = ? AND gid IS NOT NULL", arrayOf(t.name))
                    .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }.toMutableSet()
                val parentGids = t.refs.values.toSet().associateWith { gidByLocalId(it) }
                for (localId in missing) {
                    val row = readRow(t.name, localId) ?: continue
                    val base = gidFor(t, localId, row, parentGids)
                    var gid = base
                    var n = 2
                    while (gid in used) gid = "$base#${n++}"
                    used += gid
                    s.execSQL("UPDATE sync_meta SET gid = ? WHERE tbl = ? AND localId = ?", arrayOf(gid, t.name, localId))
                }
            }
            s.setTransactionSuccessful()
        } finally {
            s.endTransaction()
        }
    }

    private fun gidFor(t: SyncTable, localId: Long, row: Map<String, Any?>, parentGids: Map<String, Map<Long, String>>): String {
        t.preferKey?.let { k -> (row[k] as? String)?.takeIf { it.isNotBlank() }?.let { return "p" + hash("${t.name}|$it") } }
        val key = t.naturalKey ?: return "d:${deviceId()}:$localId"
        val parts = key.map { col ->
            val v = row[col]
            val parent = t.refs[col]
            if (parent != null && v is Long) parentGids[parent]?.get(v) ?: "?$v" else v?.toString()?.trim() ?: "∅"
        }
        return "n" + hash(t.name + "|" + parts.joinToString("|"))
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).take(16).joinToString("") { "%02x".format(it) }

    private fun gidByLocalId(table: String): Map<Long, String> =
        sql.query("SELECT localId, gid FROM sync_meta WHERE tbl = ? AND gid IS NOT NULL AND deleted = 0", arrayOf(table))
            .use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) } }

    private fun localIdByGid(table: String): Map<String, Long> = gidByLocalId(table).entries.associate { (k, v) -> v to k }

    // ------------------------------------------------------------------ Lesen

    private fun readRow(table: String, rowid: Long): Map<String, Any?>? =
        sql.query("SELECT * FROM `$table` WHERE rowid = ?", arrayOf(rowid)).use { c -> if (c.moveToFirst()) c.toMap() else null }

    private fun Cursor.toMap(skip: String? = null): Map<String, Any?> = buildMap {
        for (i in 0 until columnCount) {
            val name = getColumnName(i)
            if (name == skip) continue
            put(
                name,
                when (getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                    else -> getString(i)
                },
            )
        }
    }

    private data class ColumnInfo(val name: String, val notNull: Boolean)

    private fun columns(table: String): List<ColumnInfo> = sql.query("PRAGMA table_info(`$table`)").use { c ->
        val n = c.getColumnIndexOrThrow("name")
        val nn = c.getColumnIndexOrThrow("notnull")
        buildList { while (c.moveToNext()) add(ColumnInfo(c.getString(n), c.getInt(nn) == 1)) }
    }

    // ------------------------------------------------------------------ Schnappschuss

    /** Alle abgeglichenen Daten dieses Geräts inkl. Lösch-Markierungen. */
    fun snapshot(deviceName: String = ""): JSONObject {
        assignGids()
        val s = sql
        val tables = JSONObject()
        for (t in SyncSchema.tables) {
            val parentGids = t.refs.values.toSet().associateWith { gidByLocalId(it) }
            val rows = s.query("SELECT rowid AS __rid, * FROM `${t.name}`").use { c ->
                buildMap { while (c.moveToNext()) put(c.getLong(0), c.toMap(skip = "__rid")) }
            }
            val out = JSONArray()
            s.query("SELECT localId, gid, updatedAt, deleted FROM sync_meta WHERE tbl = ? AND gid IS NOT NULL", arrayOf(t.name)).use { c ->
                while (c.moveToNext()) {
                    val localId = c.getLong(0)
                    val rec = JSONObject().put("g", c.getString(1)).put("u", c.getLong(2))
                    if (c.getInt(3) == 1) {
                        out.put(rec.put("d", 1))
                        continue
                    }
                    val row = rows[localId] ?: continue
                    val v = JSONObject()
                    row.forEach { (col, value) ->
                        if (col == t.autoId) return@forEach
                        val parent = t.refs[col]
                        val mapped: Any? = if (parent != null && value is Long) parentGids[parent]?.get(value) else value
                        v.put(col, mapped ?: JSONObject.NULL)
                    }
                    out.put(rec.put("v", v))
                }
            }
            tables.put(t.name, out)
        }
        return JSONObject()
            .put("format", 1)
            .put("dbVersion", s.version)
            .put("device", deviceId())
            .put("deviceName", deviceName)
            .put("createdAt", System.currentTimeMillis())
            .put("tables", tables)
    }

    // ------------------------------------------------------------------ Zusammenführen

    fun merge(remote: JSONObject): MergeResult {
        val remoteVersion = remote.optInt("dbVersion", -1)
        val localVersion = sql.version
        if (remoteVersion != localVersion) throw SyncVersionException(remoteVersion, localVersion)
        if (remote.optString("device") == deviceId()) return MergeResult()
        assignGids()
        val tables = remote.getJSONObject("tables")
        var result = MergeResult()
        val s = sql
        s.beginTransaction()
        try {
            for (t in SyncSchema.tables) {
                val records = tables.optJSONArray(t.name) ?: continue
                result += mergeTable(s, t, records)
            }
            s.setTransactionSuccessful()
        } finally {
            s.endTransaction()
        }
        if (result.changes > 0) db.invalidationTracker.refreshVersionsAsync()
        return result
    }

    private data class Meta(val localId: Long, val updatedAt: Long, val deleted: Boolean)

    private fun mergeTable(s: SupportSQLiteDatabase, t: SyncTable, records: JSONArray): MergeResult {
        var ins = 0
        var upd = 0
        var del = 0
        var skip = 0
        val meta = s.query("SELECT gid, localId, updatedAt, deleted FROM sync_meta WHERE tbl = ? AND gid IS NOT NULL", arrayOf(t.name)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), Meta(c.getLong(1), c.getLong(2), c.getInt(3) == 1)) }
        }
        val cols = columns(t.name).associateBy { it.name }
        val parentIds = t.refs.values.toSet().associateWith { localIdByGid(it) }

        fun setMeta(localId: Long, gid: String, updatedAt: Long) {
            s.execSQL("UPDATE sync_meta SET gid = ?, updatedAt = ? WHERE tbl = ? AND localId = ?", arrayOf(gid, updatedAt, t.name, localId))
        }

        /** Werte des fremden Eintrags in lokale Werte übersetzen; null = nicht übernehmbar. */
        fun values(v: JSONObject): ContentValues? {
            val cv = ContentValues()
            for (col in v.keys()) {
                val info = cols[col] ?: continue
                if (col == t.autoId) continue
                val raw = v.get(col)
                val parent = t.refs[col]
                if (parent != null) {
                    val id = (raw as? String)?.let { parentIds[parent]?.get(it) }
                    when {
                        id != null -> cv.put(col, id)
                        info.notNull -> return null // Eltern-Eintrag fehlt hier (z. B. inzwischen gelöscht)
                        else -> cv.putNull(col)
                    }
                    continue
                }
                when (raw) {
                    JSONObject.NULL -> cv.putNull(col)
                    is Int -> cv.put(col, raw.toLong())
                    is Long -> cv.put(col, raw)
                    is Double -> cv.put(col, raw)
                    is Boolean -> cv.put(col, if (raw) 1 else 0)
                    else -> cv.put(col, raw.toString())
                }
            }
            return cv
        }

        fun insert(cv: ContentValues, gid: String, u: Long): Boolean = try {
            val rowid = s.insert(t.name, SQLiteDatabase.CONFLICT_ABORT, cv)
            if (rowid < 0) false else {
                setMeta(rowid, gid, u)
                true
            }
        } catch (e: SQLiteConstraintException) {
            false
        }

        for (i in 0 until records.length()) {
            val rec = records.getJSONObject(i)
            val gid = rec.getString("g")
            val u = rec.getLong("u")
            val local = meta[gid]
            if (rec.optInt("d", 0) == 1) {
                if (local != null && !local.deleted && u > local.updatedAt) {
                    s.execSQL("DELETE FROM `${t.name}` WHERE rowid = ?", arrayOf(local.localId))
                    s.execSQL("UPDATE sync_meta SET updatedAt = ?, deleted = 1 WHERE tbl = ? AND localId = ?", arrayOf(u, t.name, local.localId))
                    del++
                }
                continue
            }
            val cv = values(rec.getJSONObject("v"))
            if (cv == null) {
                skip++
                continue
            }
            when {
                local == null -> if (insert(cv, gid, u)) ins++ else skip++
                local.deleted -> if (u > local.updatedAt) {
                    // Auf dem anderen Gerät NACH dem Löschen geändert → wiederherstellen
                    s.execSQL("DELETE FROM sync_meta WHERE tbl = ? AND localId = ?", arrayOf(t.name, local.localId))
                    if (insert(cv, gid, u)) ins++ else skip++
                }
                u > local.updatedAt -> {
                    val rows = try {
                        s.update(t.name, SQLiteDatabase.CONFLICT_ABORT, cv, "rowid = ?", arrayOf(local.localId))
                    } catch (e: SQLiteConstraintException) {
                        0
                    }
                    if (rows > 0) {
                        setMeta(local.localId, gid, u)
                        upd++
                    } else {
                        skip++
                    }
                }
            }
        }
        return MergeResult(ins, upd, del, skip)
    }
}
