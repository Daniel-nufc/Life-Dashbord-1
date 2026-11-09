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
        WorkoutEntity::class,
        WorkoutSetEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        GoalEntity::class,
        MilestoneEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        CategoryRuleEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        SavingsGoalEntity::class,
        EtsyDesignEntity::class,
        EtsySaleEntity::class,
        EtsyStatsEntity::class,
        SchoolHolidayEntity::class,
        TrainingResultEntity::class,
        SyncMetaEntity::class,
    ],
    version = 12,
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
    abstract fun sportDao(): SportDao
    abstract fun goalDao(): GoalDao
    abstract fun financeDao(): FinanceDao
    abstract fun etsyDao(): EtsyDao
    abstract fun holidayDao(): HolidayDao

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

        /** Phase 5: Training-Log, Gewohnheiten, Ziele. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workouts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                        "`distanceMeters` INTEGER, `note` TEXT)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_dateEpochDay` ON `workouts` (`dateEpochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`workoutId` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `reps` INTEGER NOT NULL, `weightKg` REAL NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_workoutId` ON `workout_sets` (`workoutId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `area` TEXT NOT NULL, `active` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_habitId_dateEpochDay` ON `habit_logs` (`habitId`, `dateEpochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`area` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `deadlineEpochDay` INTEGER, " +
                        "`done` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `milestones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `title` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `done` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_milestones_goalId` ON `milestones` (`goalId`)")
            }
        }

        /** Phase 6: Finanzen. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `kind` TEXT NOT NULL, `initialBalanceCents` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, " +
                        "`counterparty` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT, `importHash` TEXT, `recurringId` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_dateEpochDay` ON `transactions` (`dateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_importHash` ON `transactions` (`importHash`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`keyword` TEXT NOT NULL, `category` TEXT NOT NULL, `hits` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_rules_keyword` ON `category_rules` (`keyword`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `monthlyLimitCents` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`category`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, `title` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `dayOfMonth` INTEGER NOT NULL, " +
                        "`category` TEXT, `active` INTEGER NOT NULL, `lastGeneratedEpochDay` INTEGER)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `savings_goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, `savedCents` INTEGER NOT NULL, `deadlineEpochDay` INTEGER)",
                )
            }
        }

        /** Phase 7: Etsy-Motive, Verkäufe, Statistik. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `etsy_designs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`setName` TEXT NOT NULL, `title` TEXT NOT NULL, `status` TEXT NOT NULL, `canvaRef` TEXT, `note` TEXT, " +
                        "`createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `etsy_sales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `orderRef` TEXT NOT NULL, `title` TEXT NOT NULL, `priceCents` INTEGER NOT NULL, " +
                        "`quantity` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_etsy_sales_dateEpochDay` ON `etsy_sales` (`dateEpochDay`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `etsy_stats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`monthKey` TEXT NOT NULL, `views` INTEGER NOT NULL, `favorites` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_etsy_stats_monthKey` ON `etsy_stats` (`monthKey`)")
            }
        }

        /** Update 2 / Teil 1: Ferien, Feiertage und schulfreie Tage. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `school_holidays` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_holidays_startEpochDay` ON `school_holidays` (`startEpochDay`)")
            }
        }

        /** Update 2 / Teil 2A: Trainingsplan-Ergebnisse, Trainings aus Health Connect. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `externalId` TEXT")
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `avgHeartRate` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_externalId` ON `workouts` (`externalId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `training_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, `done` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, " +
                        "`effort` TEXT, `distanceMeters` INTEGER, `reduced` INTEGER NOT NULL, `workoutId` INTEGER)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_results_dateEpochDay_type` ON `training_results` (`dateEpochDay`, `type`)")
            }
        }

        /** Update 2 / Teil 2B: Gewohnheiten automatisch abhaken. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habits` ADD COLUMN `autoRule` TEXT")
            }
        }

        /** Update 21: Geräte-Abgleich Handy ↔ Tablet (Kennungen + Änderungszeitpunkte). */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_meta` (`tbl` TEXT NOT NULL, `localId` INTEGER NOT NULL, `gid` TEXT, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`tbl`, `localId`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_meta_tbl_gid` ON `sync_meta` (`tbl`, `gid`)")
                app.lifedashboard.data.devicesync.SyncSchema.backfill(db)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
        )

        /** Legt beim Öffnen die Abgleich-Trigger an (neue Installation und nach Updates). */
        val SYNC_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                app.lifedashboard.data.devicesync.SyncSchema.createTriggers(db)
            }
        }
    }
}
