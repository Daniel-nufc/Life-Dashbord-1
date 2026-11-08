package app.lifedashboard.data.repository

import app.lifedashboard.data.db.GoalDao
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.PlanDao
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.db.SportDao
import app.lifedashboard.data.db.StudyDao
import app.lifedashboard.data.db.TaskDao
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.planner.DemandType
import app.lifedashboard.planner.mondayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class WeekReview(
    val weekStart: LocalDate,
    val planned: Int,
    val done: Int,
    val missed: Int,
    val declined: Int,
    val studyMinutes: Int,
    val homeworkDone: Int,
    val workouts: Int,
    val workoutMinutes: Int,
    val habitRate: Int,
    val spentCents: Long,
    val etsyRevenueCents: Long,
    val good: List<String>,
    val bad: List<String>,
    val suggestions: List<String>,
)

/** Wochenrückblick: Was lief gut, was nicht – und konkrete Vorschläge für die neue Woche. */
@Singleton
class ReviewRepository @Inject constructor(
    private val plan: PlanDao,
    private val studyDao: StudyDao,
    private val taskDao: TaskDao,
    private val sportDao: SportDao,
    private val goalDao: GoalDao,
    private val finance: FinanceRepository,
    private val etsy: EtsyRepository,
    private val study: StudyRepository,
) {
    suspend fun week(anyDay: LocalDate = LocalDate.now()): WeekReview {
        val start = anyDay.mondayOfWeek()
        val end = start.plusDays(6)
        val zone = ZoneId.systemDefault()
        val fromMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val blocks = plan.between(start.toEpochDay(), end.toEpochDay())
        val done = blocks.count { it.status == PlanStatus.DONE }
        val missed = blocks.count { it.status == PlanStatus.MISSED }
        val declined = blocks.count { it.status == PlanStatus.DECLINED || it.status == PlanStatus.MOVED }
        val planned = blocks.count { it.status != PlanStatus.PROPOSED || it.dateEpochDay >= LocalDate.now().toEpochDay() }

        val sessions = studyDao.observeSessionsSince(fromMillis).first().filter { it.startedAtMillis < toMillis }
        val studyMinutes = sessions.sumOf { it.minutes }
        val tasks: List<TaskEntity> = taskDao.observeAll().first()
        val homeworkDone = tasks.count { it.kind == TaskKind.HOMEWORK && it.done && (it.doneAtMillis ?: 0) in fromMillis until toMillis }
        val workouts = sportDao.observeWorkouts().first().filter { it.dateEpochDay in start.toEpochDay()..end.toEpochDay() }
        val habits = goalDao.observeHabits().first().filter { it.active }
        val logs = goalDao.observeHabitLogs(start.toEpochDay()).first().filter { it.dateEpochDay <= end.toEpochDay() }
        val daysSoFar = (minOf(LocalDate.now(), end).toEpochDay() - start.toEpochDay() + 1).coerceIn(1, 7).toInt()
        val habitRate = if (habits.isEmpty()) 0 else (logs.size * 100) / (habits.size * daysSoFar)
        val fin = finance.overview().first()
        val spent = fin.transactions.filter { it.dateEpochDay in start.toEpochDay()..end.toEpochDay() && it.amountCents < 0 }.sumOf { -it.amountCents }
        val etsyWeek = etsy.overview().first().sales.filter { it.dateEpochDay in start.toEpochDay()..end.toEpochDay() }.sumOf { it.priceCents * it.quantity }

        // Welche Arten wurden oft abgelehnt/verpasst?
        val problemTypes = blocks.filter { it.status == PlanStatus.MISSED || it.status == PlanStatus.DECLINED || it.status == PlanStatus.MOVED }
            .groupingBy { it.type }.eachCount().filter { it.value >= 3 }.keys
        val nextWeekExams = study.overview(end.plusDays(1)).first().upcoming.filter { it.daysLeft(end.plusDays(1)) in 0..7 }

        val good = buildList {
            if (done >= 5) add("$done geplante Blöcke erledigt – stark!")
            if (studyMinutes >= 120) add("${studyMinutes / 60} Std ${studyMinutes % 60} Min konzentriert gelernt")
            if (homeworkDone > 0) add("$homeworkDone Hausaufgaben erledigt")
            if (workouts.size >= 2) add("${workouts.size} Trainings eingetragen")
            if (habitRate >= 70) add("Gewohnheiten zu $habitRate % geschafft")
            if (etsyWeek > 0) add("Etsy-Umsatz diese Woche: ${etsyWeek.euro()}")
            if (isEmpty()) add("Du bist dabeigeblieben – das zählt. Nächste Woche wird besser.")
        }
        val bad = buildList {
            if (missed + declined > done && missed + declined >= 3) add("Viele Vorschläge verpasst/abgelehnt ($missed verpasst, $declined abgelehnt)")
            if (studyMinutes < 60) add("Wenig Fokus-Zeit (${studyMinutes} Min)")
            if (habits.isNotEmpty() && habitRate < 50) add("Gewohnheiten nur zu $habitRate %")
            if (workouts.isEmpty()) add("Kein Training eingetragen")
        }
        val suggestions = buildList {
            if (nextWeekExams.isNotEmpty()) add("Klausurphase: ${nextWeekExams.joinToString { it.subject?.code ?: it.exam.title }} – Etsy wird automatisch reduziert, Lernblöcke haben Vorrang.")
            if (DemandType.STUDY in problemTypes || DemandType.HOMEWORK in problemTypes) add("Lernblöcke wurden oft verschoben – die App plant sie jetzt öfter zu deinen erfolgreichen Zeiten (nachmittags). Starte jeden Block mit nur 5 Minuten.")
            if (DemandType.ETSY in problemTypes) add("Etsy-Blöcke passen oft nicht – lieber 2 feste längere Blöcke am Wochenende?")
            if (habits.isNotEmpty() && habitRate < 50) add("Konzentrier dich nächste Woche auf EINE Gewohnheit: ${habits.first().emoji} ${habits.first().title}.")
            if (workouts.size < 2) add("2 kurze Läufe (20–30 Min) einplanen – Schritt Richtung 10 km.")
            if (studyMinutes < 120 && nextWeekExams.isEmpty()) add("Ziel: 4 Fokus-Runden à 25 Min in der Woche – das sind nur 100 Minuten.")
            if (fin.budgets.any { it.ratio >= 1f }) add("Ein Budget ist überzogen – nächste Woche bewusst weniger in dieser Kategorie.")
            if (isEmpty()) add("Weiter so – gleicher Rhythmus wie diese Woche.")
        }
        return WeekReview(start, planned, done, missed, declined, studyMinutes, homeworkDone, workouts.size, workouts.sumOf { it.durationMinutes }, habitRate, spent, etsyWeek, good, bad, suggestions)
    }

    /** Vorschlag als Aufgabe für nächste Woche übernehmen. */
    suspend fun adopt(suggestion: String) {
        taskDao.upsert(
            TaskEntity(
                title = suggestion.take(120), area = LifeArea.PRIVATE, kind = TaskKind.TODO, subjectId = null,
                dueEpochDay = LocalDate.now().mondayOfWeek().plusDays(7).toEpochDay(), estimatedMinutes = 15,
                done = false, doneAtMillis = null, note = "Aus dem Wochenrückblick", createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }
}
