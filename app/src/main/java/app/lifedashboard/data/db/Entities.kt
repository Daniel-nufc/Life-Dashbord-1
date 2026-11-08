package app.lifedashboard.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek

/** In welcher Woche eine Stunde stattfindet. */
enum class WeekPattern { EVERY, A, B }

/** Lebensbereiche – werden überall für Farben, Filter und Prioritäten verwendet. */
enum class LifeArea { SCHOOL, SPORT, ETSY, JOB, PRIVATE }

@Entity(tableName = "subjects", indices = [Index(value = ["code"], unique = true)])
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Kürzel wie im Stundenplan, z. B. „MA-B“. */
    val code: String,
    val name: String,
    val colorArgb: Long,
    val isAdvancedCourse: Boolean = false,
    /** 1 = leicht … 5 = schwer – wichtig für die Lernplan-Verteilung. */
    val difficulty: Int = 3,
)

/** Ein Unterrichtsblock mit Uhrzeit, z. B. Block 1 = 08:00–09:30. */
@Entity(tableName = "school_periods")
data class SchoolPeriodEntity(
    @PrimaryKey val number: Int,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(
    tableName = "timetable_lessons",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("subjectId"), Index(value = ["dayOfWeek", "period"])],
)
data class TimetableLessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    /** 1 = Montag … 7 = Sonntag (wie java.time.DayOfWeek). */
    val dayOfWeek: Int,
    val period: Int,
    val weekPattern: WeekPattern,
    val room: String,
)

/** Wiederkehrende feste Blöcke wie Floorball-Training. */
@Entity(tableName = "routine_blocks")
data class RoutineBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val area: LifeArea,
    /** Kommagetrennte Wochentage, z. B. „1,3“ = Montag und Mittwoch. */
    val daysOfWeek: String,
    val startMinute: Int,
    val endMinute: Int,
    val location: String? = null,
    val note: String? = null,
    val active: Boolean = true,
) {
    fun days(): Set<DayOfWeek> = daysOfWeek.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..7 }
        .map { DayOfWeek.of(it) }
        .toSet()
}

/** Eigener Termin im Kalender. Ohne Uhrzeit = ganztägig. */
@Entity(tableName = "events", indices = [Index("dateEpochDay"), Index("externalUid")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dateEpochDay: Long,
    val startMinute: Int?,
    val endMinute: Int?,
    val area: LifeArea,
    val location: String?,
    val note: String?,
    /** Gesetzt bei Terminen aus einem Kalender-Abo (iCal) – verhindert Doppelungen. */
    val externalUid: String? = null,
)

enum class TaskKind { HOMEWORK, TODO }

/** Hausaufgabe oder To-do. Hausaufgaben gehören zu einem Fach. */
@Entity(tableName = "tasks", indices = [Index("dueEpochDay"), Index("done"), Index("externalUid")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val area: LifeArea,
    val kind: TaskKind,
    val subjectId: Long?,
    val dueEpochDay: Long?,
    val estimatedMinutes: Int,
    val done: Boolean,
    val doneAtMillis: Long?,
    val note: String?,
    val createdAtMillis: Long,
    /** Gesetzt bei Abgaben aus einem Kalender-Abo (z. B. Lernraum). */
    val externalUid: String? = null,
)

/** Schicht im Minijob. */
@Entity(tableName = "job_shifts", indices = [Index("dateEpochDay")])
data class JobShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val note: String?,
    val hourlyWageCents: Int?,
)

/** Kalender-Abo per Link (iCal), z. B. Lernraum Berlin, IServ oder WebUntis. */
@Entity(tableName = "ical_sources")
data class IcalSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    /** true = Einträge werden Aufgaben (Abgaben), false = Termine im Kalender */
    val importAsTasks: Boolean,
    val lastSyncMillis: Long?,
    val lastError: String?,
)
