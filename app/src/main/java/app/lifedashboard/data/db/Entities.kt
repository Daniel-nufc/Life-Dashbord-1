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

enum class ExamKind { KLAUSUR, TEST, REFERAT, PRAESENTATION }

/** Klausur, Test oder Referat. */
@Entity(tableName = "exams", indices = [Index("dateEpochDay")])
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val dateEpochDay: Long,
    val title: String,
    val kind: ExamKind,
    val note: String?,
)

/** Thema einer Klausur mit Lernfortschritt und Wiederholungs-Termin (Spaced Repetition). */
@Entity(tableName = "topics", indices = [Index("examId")])
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val title: String,
    val effortMinutes: Int,
    val progress: Int,
    val intervalDays: Int,
    val nextReviewEpochDay: Long?,
    val lastReviewedEpochDay: Long?,
)

enum class GradeKind { KLAUSUR, MUENDLICH, TEST, SONSTIGES }

/** Note in Punkten (0–15). */
@Entity(tableName = "grades", indices = [Index("subjectId")])
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val points: Int,
    val kind: GradeKind,
    val weight: Double,
    val dateEpochDay: Long,
    val note: String?,
)

/** Eine abgeschlossene Fokus-Einheit (Pomodoro). */
@Entity(tableName = "study_sessions", indices = [Index("startedAtMillis")])
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val examId: Long?,
    val topicId: Long?,
    val startedAtMillis: Long,
    val minutes: Int,
)

enum class PlanStatus { PROPOSED, ACCEPTED, DONE, DECLINED, MOVED, MISSED }

/** Ein Vorschlag bzw. geplanter Block im Tagesplan. */
@Entity(tableName = "plan_blocks", indices = [Index("dateEpochDay")])
data class PlanBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val type: app.lifedashboard.planner.DemandType,
    val demandKey: String,
    val title: String,
    val refId: Long?,
    val status: PlanStatus,
    val createdAtMillis: Long,
)

enum class FeedbackAction { ACCEPTED, DONE, DECLINED, MOVED, MISSED }

/** Woraus die App lernt: Wie hast du auf Vorschläge reagiert? */
@Entity(tableName = "plan_feedback")
data class PlanFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: app.lifedashboard.planner.DemandType,
    val weekend: Boolean,
    val bucket: Int,
    val action: FeedbackAction,
    val atMillis: Long,
)

enum class WorkoutType { FLOORBALL, RUN, STRENGTH, SWIM, BIKE, OTHER }

/** Ein Training im Log. */
@Entity(tableName = "workouts", indices = [Index("dateEpochDay"), Index("externalId")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val type: WorkoutType,
    val durationMinutes: Int,
    val distanceMeters: Int?,
    val note: String?,
    /** Gesetzt, wenn das Training automatisch aus Health Connect kam */
    val externalId: String? = null,
    val avgHeartRate: Int? = null,
)

/** Ein Satz beim Krafttraining. */
@Entity(tableName = "workout_sets", indices = [Index("workoutId")])
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val reps: Int,
    val weightKg: Double,
)

/** Tägliche Gewohnheit. */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String,
    val area: LifeArea,
    val active: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "habit_logs", indices = [Index(value = ["habitId", "dateEpochDay"], unique = true)])
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dateEpochDay: Long,
)

/** Langfristiges Ziel mit Zwischenschritten. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val area: LifeArea,
    val title: String,
    val description: String?,
    val deadlineEpochDay: Long?,
    val done: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "milestones", indices = [Index("goalId")])
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val sortOrder: Int,
    val done: Boolean,
)

enum class AccountKind { BANK, CASH, SAVINGS }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    val initialBalanceCents: Long,
    val sortOrder: Int,
)

/** Umsatz in Cent (negativ = Ausgabe). Kategorie als Name, z. B. „Lebensmittel“. */
@Entity(
    tableName = "transactions",
    indices = [Index("dateEpochDay"), Index("accountId"), Index("importHash")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val dateEpochDay: Long,
    val amountCents: Long,
    val counterparty: String,
    val description: String,
    val category: String?,
    val importHash: String?,
    val recurringId: Long?,
)

/** Gelernte Kategorie-Regel aus deinen Korrekturen. */
@Entity(tableName = "category_rules", indices = [Index(value = ["keyword"], unique = true)])
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val category: String,
    val hits: Int,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimitCents: Long,
)

/** Feste Buchung jeden Monat (Taschengeld, Abo …) – wird automatisch eingetragen. */
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val title: String,
    val amountCents: Long,
    val dayOfMonth: Int,
    val category: String?,
    val active: Boolean,
    val lastGeneratedEpochDay: Long?,
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadlineEpochDay: Long?,
)

/** Station eines Motivs auf dem Weg in den Shop. */
enum class DesignStatus { IDEA, CREATED, CHECKED, TEST_PRINT, MOCKUP, LISTING, ONLINE }

@Entity(tableName = "etsy_designs")
data class EtsyDesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setName: String,
    val title: String,
    val status: DesignStatus,
    val canvaRef: String?,
    val note: String?,
    val createdAtMillis: Long,
)

@Entity(tableName = "etsy_sales", indices = [Index("dateEpochDay")])
data class EtsySaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val orderRef: String,
    val title: String,
    val priceCents: Long,
    val quantity: Int,
)

/** Aufrufe und Favoriten pro Monat (aus der Etsy-Statistik abgeschrieben, später per API). */
@Entity(tableName = "etsy_stats", indices = [Index(value = ["monthKey"], unique = true)])
data class EtsyStatsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val monthKey: String,
    val views: Int,
    val favorites: Int,
)

enum class HolidayKind { SCHOOL_HOLIDAY, PUBLIC_HOLIDAY, SCHOOL_FREE }

/** Ferien, Feiertag oder eigener schulfreier Tag (z. B. Studientag). */
@Entity(tableName = "school_holidays", indices = [Index("startEpochDay")])
data class SchoolHolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val kind: HolidayKind,
)

/** Ergebnis einer geplanten Trainingseinheit (erledigt, ausgelassen, Bewertung). Der Plan selbst wird berechnet. */
@Entity(tableName = "training_results", indices = [Index(value = ["dateEpochDay", "type"], unique = true)])
data class TrainingResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val type: app.lifedashboard.planner.SessionType,
    val done: Boolean,
    val skipped: Boolean,
    val effort: app.lifedashboard.planner.Effort?,
    val distanceMeters: Int?,
    val reduced: Boolean,
    val workoutId: Long?,
)
