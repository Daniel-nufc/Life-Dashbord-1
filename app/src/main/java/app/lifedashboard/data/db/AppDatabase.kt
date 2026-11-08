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
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun routineDao(): RoutineDao
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun jobShiftDao(): JobShiftDao

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
    }
}
