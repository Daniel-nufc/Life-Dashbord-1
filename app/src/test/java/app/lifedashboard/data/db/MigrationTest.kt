package app.lifedashboard.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft, dass alle Migrationen (1 → aktuell) genau die Datenbank-Struktur erzeugen,
 * die Room für die aktuelle Version erwartet. So gehen bei Updates keine Daten verloren
 * und die App stürzt nach einem Update nicht ab.
 */
class MigrationTest {

    private val v1Tables = listOf("subjects", "school_periods", "timetable_lessons", "routine_blocks")

    private fun findSchema(): File {
        val candidates = listOf(File("schemas"), File("build")).filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "json" && it.parentFile.name == "app.lifedashboard.data.db.AppDatabase" }.toList() }
        // Neuestes exportiertes Schema = aktuelle Datenbank-Version
        return candidates.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: -1 }
            ?: error("Kein Room-Schema gefunden (schemas/ oder build/).")
    }

    /** (Tabelle → Create-SQL) und Index-SQLs aus der exportierten Room-Schema-Datei. */
    private fun parseSchema(json: String): Pair<Map<String, String>, Map<String, List<String>>> {
        val tables = linkedMapOf<String, String>()
        val indices = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        Regex("\"tableName\"\\s*:\\s*\"(\\w+)\"|\"createSql\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json).forEach { m ->
            if (m.groupValues[1].isNotEmpty()) {
                current = m.groupValues[1]
            } else {
                val table = current ?: return@forEach
                val sql = m.groupValues[2].replace("\${TABLE_NAME}", table).replace("\\\"", "\"")
                if (sql.startsWith("CREATE TABLE")) tables[table] = sql else indices.getOrPut(table) { mutableListOf() } += sql
            }
        }
        return tables to indices
    }

    /** Fängt die SQL-Befehle einer Migration ab (ohne Android). */
    private fun recordingDatabase(statements: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL") statements += args!![0] as String
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

    private fun describe(conn: Connection): Map<String, String> {
        val result = sortedMapOf<String, String>()
        val tables = conn.createStatement().executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table','android_metadata')",
        ).let { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        for (table in tables) {
            val cols = conn.createStatement().executeQuery("PRAGMA table_info(`$table`)").let { rs ->
                buildList { while (rs.next()) add("${rs.getString("name")}:${rs.getString("type")}:nn=${rs.getInt("notnull")}:pk=${rs.getInt("pk")}") }
            }.sorted()
            val idx = conn.createStatement().executeQuery("PRAGMA index_list(`$table`)").let { rs ->
                buildList { while (rs.next()) if (!rs.getString("name").startsWith("sqlite_")) add("${rs.getString("name")}:unique=${rs.getInt("unique")}") }
            }.sorted()
            result[table] = "cols=$cols idx=$idx"
        }
        return result
    }

    @Test
    fun alleMigrationenErgebenDasAktuelleSchema() {
        val schemaFile = findSchema()
        val version = schemaFile.nameWithoutExtension.toInt()
        val (tables, indices) = parseSchema(schemaFile.readText())
        assertTrue("Schema enthält keine Tabellen", tables.isNotEmpty())

        // Erwartet: frisch angelegte Datenbank der aktuellen Version
        val expected = DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            tables.forEach { (t, sql) -> conn.createStatement().execute(sql); indices[t].orEmpty().forEach { conn.createStatement().execute(it) } }
            describe(conn)
        }

        // Tatsächlich: Version 1 anlegen und alle Migrationen ausführen
        val actual = DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            v1Tables.forEach { t ->
                conn.createStatement().execute(tables.getValue(t))
                indices[t].orEmpty().forEach { conn.createStatement().execute(it) }
            }
            AppDatabase.ALL_MIGRATIONS.sortedBy { it.startVersion }.forEach { migration ->
                val statements = mutableListOf<String>()
                migration.migrate(recordingDatabase(statements))
                statements.forEach { conn.createStatement().execute(it) }
            }
            describe(conn)
        }

        assertEquals(expected.keys, actual.keys)
        expected.forEach { (table, desc) -> assertEquals("Tabelle $table weicht ab", desc, actual[table]) }

        assertEquals("Migrationen müssen bis zur aktuellen Version reichen", version, AppDatabase.ALL_MIGRATIONS.maxOf { it.endVersion })
    }
}
