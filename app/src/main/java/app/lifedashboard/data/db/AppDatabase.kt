package app.lifedashboard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Die zentrale, verschlüsselte Datenbank. Alle Bereiche der App lesen und schreiben hier –
 * dadurch hängen Stundenplan, Kalender, Lernplan usw. automatisch zusammen.
 *
 * Wichtig für spätere Phasen: Bei Änderungen am Schema immer die [version] erhöhen und eine
 * Migration schreiben, damit deine Daten bei Updates erhalten bleiben.
 */
@Database(
    entities = [
        SubjectEntity::class,
        SchoolPeriodEntity::class,
        TimetableLessonEntity::class,
        RoutineBlockEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun routineDao(): RoutineDao

    companion object {
        const val NAME = "lifedashboard.db"
    }
}
