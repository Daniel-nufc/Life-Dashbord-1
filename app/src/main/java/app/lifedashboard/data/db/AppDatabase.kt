package app.lifedashboard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Die zentrale, verschlüsselte Datenbank. Alle Bereiche der App lesen und schreiben hier –
 * dadurch hängen Stundenplan, Kalender, Aufgaben usw. automatisch zusammen.
 *
 * Wichtig: Bei Änderungen am Schema immer die [version] erhöhen und eine Migration schreiben,
 * damit deine Daten bei Updates erhalten bleiben.
 */
@Database(
    entities = [
        SubjectEntity::class,
        SchoolPeriodEntity::class,
        TimetableLessonEntity::class,
        RoutineBlockEntity::class,
        EventEntity::class,
        TaskEntity::class,
        JobShiftEntity::class,
        IcalSourceEntity::class,
        ExamEntity::class,
        TopicEntity::class,
        GradeEntity::class,
        StudySessionEntity::class,
        PlanBlockEntity::class,
        PlanFeedbackEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun routineDao(): RoutineDao
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun jobShiftDao(): JobShiftDao
    abstract fun icalSourceDao(): IcalSourceDao
    abstract fun studyDao(): StudyDao
    abstract fun planDao(): PlanDao

    companion object {
        const val NAME = "lifedashboard.db"

        /** Phase 2: Termine, Aufgaben/Hausaufgaben und Job-Schichten. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `startMinute` INTEGER, " +
                        "`endMinute` INTEGER, `area` TEXT NOT NULL, `location` TEXT, `note` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_dateEpochDay` ON `events` (`dateEpochDay`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `area` TEXT NOT NULL, `kind` TEXT NOT NULL, `subjectId` INTEGER, " +
                        "`dueEpochDay` INTEGER, `estimatedMinutes` INTEGER NOT NULL, `done` INTEGER NOT NULL, " +
                        "`doneAtMillis` INTEGER, `note` TEXT, `createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueEpochDay` ON `tasks` (`dueEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_done` ON `tasks` (`done`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `job_shifts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, " +
                        "`note` TEXT, `hourlyWageCents` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_job_shifts_dateEpochDay` ON `job_shifts` (`dateEpochDay`)")
            }
        }

        /** Phase 2b: Kalender-Abos (iCal) und Kennung für importierte Einträge. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `events` ADD COLUMN `externalUid` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_externalUid` ON `events` (`externalUid`)")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `externalUid` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_externalUid` ON `tasks` (`externalUid`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ical_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `url` TEXT NOT NULL, `importAsTasks` INTEGER NOT NULL, " +
                        "`lastSyncMillis` INTEGER, `lastError` TEXT)",
                )
            }
        }

        /** Phase 3: Klausuren, Themen, Noten, Fokus-Einheiten. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `note` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_dateEpochDay` ON `exams` (`dateEpochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `topics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`examId` INTEGER NOT NULL, `title` TEXT NOT NULL, `effortMinutes` INTEGER NOT NULL, " +
                        "`progress` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL, `nextReviewEpochDay` INTEGER, " +
                        "`lastReviewedEpochDay` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_examId` ON `topics` (`examId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `grades` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, `points` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`weight` REAL NOT NULL, `dateEpochDay` INTEGER NOT NULL, `note` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grades_subjectId` ON `grades` (`subjectId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `study_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subjectId` INTEGER, `examId` INTEGER, `topicId` INTEGER, `startedAtMillis` INTEGER NOT NULL, " +
                        "`minutes` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_startedAtMillis` ON `study_sessions` (`startedAtMillis`)")
            }
        }

        /** Phase 4: Tagesplan-Vorschläge und Feedback zum Lernen. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `demandKey` TEXT NOT NULL, `title` TEXT NOT NULL, `refId` INTEGER, " +
                        "`status` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_blocks_dateEpochDay` ON `plan_blocks` (`dateEpochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_feedback` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `weekend` INTEGER NOT NULL, `bucket` INTEGER NOT NULL, " +
                        "`action` TEXT NOT NULL, `atMillis` INTEGER NOT NULL)",
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
