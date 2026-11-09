package app.lifedashboard.data.devicesync

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Welche Tabellen zwischen Handy und Tablet abgeglichen werden – und wie man einen Eintrag
 * auf beiden Geräten als „denselben“ erkennt.
 *
 * - [naturalKey]: Merkmale, an denen derselbe Eintrag auf beiden Geräten erkannt wird
 *   (z. B. Fach-Kürzel). Daraus entsteht die geräteübergreifende Kennung (gid). So werden
 *   Startdaten, die auf beiden Geräten angelegt wurden, nicht doppelt.
 * - [refs]: Verweise auf andere Tabellen (lokale IDs werden beim Abgleich übersetzt).
 *
 * Nicht abgeglichen: Tagesplan-Vorschläge (rechnet jedes Gerät selbst), Feedback, Kalender-Abos.
 */
data class SyncTable(
    val name: String,
    val naturalKey: List<String>?,
    val refs: Map<String, String> = emptyMap(),
    /** Alternativer Schlüssel, wenn gesetzt (z. B. externe ID aus Health Connect / Kalender-Abo) */
    val preferKey: String? = null,
    /** Spalte mit automatischer ID (wird beim Einfügen neu vergeben) */
    val autoId: String? = "id",
)

object SyncSchema {
    /** Reihenfolge = Abhängigkeiten (Eltern vor Kindern). */
    val tables: List<SyncTable> = listOf(
        SyncTable("subjects", listOf("code")),
        SyncTable("school_periods", listOf("number"), autoId = null),
        SyncTable("timetable_lessons", listOf("dayOfWeek", "period", "weekPattern"), refs = mapOf("subjectId" to "subjects")),
        SyncTable("routine_blocks", listOf("title")),
        SyncTable("events", listOf("title", "dateEpochDay", "startMinute"), preferKey = "externalUid"),
        SyncTable("accounts", listOf("name")),
        SyncTable("recurring", listOf("title"), refs = mapOf("accountId" to "accounts")),
        SyncTable(
            "transactions", listOf("accountId", "dateEpochDay", "amountCents", "counterparty", "description"),
            refs = mapOf("accountId" to "accounts", "recurringId" to "recurring"), preferKey = "importHash",
        ),
        SyncTable("tasks", listOf("title", "kind", "dueEpochDay", "subjectId"), refs = mapOf("subjectId" to "subjects"), preferKey = "externalUid"),
        SyncTable("job_shifts", listOf("dateEpochDay", "startMinute")),
        SyncTable("exams", listOf("subjectId", "dateEpochDay", "kind"), refs = mapOf("subjectId" to "subjects")),
        SyncTable("topics", listOf("examId", "title"), refs = mapOf("examId" to "exams")),
        SyncTable("grades", listOf("subjectId", "dateEpochDay", "points", "kind"), refs = mapOf("subjectId" to "subjects")),
        SyncTable(
            "study_sessions", listOf("startedAtMillis", "minutes"),
            refs = mapOf("subjectId" to "subjects", "examId" to "exams", "topicId" to "topics"),
        ),
        SyncTable("workouts", listOf("dateEpochDay", "type", "durationMinutes", "note"), preferKey = "externalId"),
        SyncTable("workout_sets", null, refs = mapOf("workoutId" to "workouts")),
        SyncTable("training_results", listOf("dateEpochDay", "type"), refs = mapOf("workoutId" to "workouts")),
        SyncTable("habits", listOf("title")),
        SyncTable("habit_logs", listOf("habitId", "dateEpochDay"), refs = mapOf("habitId" to "habits")),
        SyncTable("goals", listOf("title")),
        SyncTable("milestones", listOf("goalId", "title"), refs = mapOf("goalId" to "goals")),
        SyncTable("category_rules", listOf("keyword")),
        SyncTable("budgets", listOf("category"), autoId = null),
        SyncTable("savings_goals", listOf("title")),
        SyncTable("etsy_designs", listOf("setName", "title")),
        SyncTable("etsy_sales", listOf("dateEpochDay", "title", "priceCents"), preferKey = "orderRef"),
        SyncTable("etsy_stats", listOf("monthKey")),
        SyncTable("school_holidays", listOf("startEpochDay", "name")),
    )

    val byName = tables.associateBy { it.name }

    /** Aktuelle Zeit in ms als SQL-Ausdruck (für die Trigger). */
    private const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /**
     * Trigger: Jede Änderung an einer Tabelle wird mit Zeitstempel in sync_meta vermerkt –
     * ganz ohne die restliche App anzupassen. Wird bei jedem Öffnen angelegt (IF NOT EXISTS).
     */
    fun createTriggers(db: SupportSQLiteDatabase) {
        tables.forEach { t ->
            val n = t.name
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS `sync_ins_$n` AFTER INSERT ON `$n` BEGIN
                    INSERT INTO sync_meta(tbl, localId, gid, updatedAt, deleted) VALUES('$n', NEW.rowid, NULL, $NOW, 0)
                    ON CONFLICT(tbl, localId) DO UPDATE SET updatedAt = excluded.updatedAt, deleted = 0,
                        gid = CASE WHEN sync_meta.deleted = 1 THEN NULL ELSE sync_meta.gid END;
                END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS `sync_upd_$n` AFTER UPDATE ON `$n` BEGIN
                    INSERT INTO sync_meta(tbl, localId, gid, updatedAt, deleted) VALUES('$n', NEW.rowid, NULL, $NOW, 0)
                    ON CONFLICT(tbl, localId) DO UPDATE SET updatedAt = excluded.updatedAt, deleted = 0;
                END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS `sync_del_$n` AFTER DELETE ON `$n` BEGIN
                    UPDATE sync_meta SET deleted = 1, updatedAt = $NOW WHERE tbl = '$n' AND localId = OLD.rowid;
                END""",
            )
        }
    }

    /** Für bestehende Daten (Update von Version 11): alle Einträge einmal registrieren. */
    fun backfill(db: SupportSQLiteDatabase) {
        tables.forEach { t ->
            db.execSQL("INSERT OR IGNORE INTO sync_meta(tbl, localId, gid, updatedAt, deleted) SELECT '${t.name}', rowid, NULL, 0, 0 FROM `${t.name}`")
        }
    }
}
