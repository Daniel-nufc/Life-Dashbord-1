package app.lifedashboard.data.planning

import app.lifedashboard.data.db.FeedbackAction
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.PlanDao
import app.lifedashboard.data.db.PlanFeedbackEntity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.repository.AgendaRepository
import app.lifedashboard.data.repository.DayAgenda
import app.lifedashboard.data.repository.StudyOverview
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.data.repository.TaskRepository
import app.lifedashboard.data.settings.AppSettings
import app.lifedashboard.planner.DayPlanner
import app.lifedashboard.planner.Demand
import app.lifedashboard.planner.DemandType
import app.lifedashboard.planner.PlannerConfig
import app.lifedashboard.planner.PreferenceModel
import app.lifedashboard.planner.TimeRange
import app.lifedashboard.planner.freeSlots
import app.lifedashboard.planner.minuteToTime
import app.lifedashboard.planner.minuteOfDay
import app.lifedashboard.ui.today.EntryKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Das Herzstück: erstellt den Tagesplan aus allen Bereichen.
 *
 * Feste Termine bleiben fest, Vorschläge kommen in freie Zeit. Angenommene und erledigte Blöcke
 * bleiben stehen; verpasste werden als „verpasst“ markiert und ihr Inhalt neu eingeplant –
 * ohne Strafe. Aus Annehmen/Ablehnen lernt die App, welche Tageszeiten dir liegen.
 */
@Singleton
class PlanningEngine @Inject constructor(
    private val dao: PlanDao,
    private val agenda: AgendaRepository,
    private val study: StudyRepository,
    private val tasks: TaskRepository,
) {
    private val mutex = Mutex()

    fun observeDay(date: LocalDate): Flow<List<PlanBlockEntity>> = dao.observeDay(date.toEpochDay())

    suspend fun replan(date: LocalDate = LocalDate.now()) = mutex.withLock {
        val today = LocalDate.now()
        if (date.isBefore(today)) return@withLock
        val epoch = date.toEpochDay()
        val now = LocalTime.now()
        val nowMinute = if (date == today) now.minuteOfDay() else 0

        val existing = dao.forDay(epoch)
        // Vergangene, nicht erledigte Blöcke → verpasst (ohne Strafe, Inhalt wird neu geplant)
        existing.filter { (it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED) && it.endMinute <= nowMinute }
            .forEach {
                dao.setStatus(it.id, PlanStatus.MISSED)
                if (it.status == PlanStatus.ACCEPTED) feedback(it, FeedbackAction.MISSED)
            }
        // Offene Vorschläge ab jetzt neu berechnen
        dao.delete(existing.filter { it.status == PlanStatus.PROPOSED && it.endMinute > nowMinute }.map { it.id })

        val current = dao.forDay(epoch)
        val kept = current.filter { it.status == PlanStatus.ACCEPTED || it.status == PlanStatus.DONE }
        val blockedRanges = current.filter { it.status == PlanStatus.MOVED || it.status == PlanStatus.ACCEPTED || it.status == PlanStatus.DONE }
            .map { TimeRange.ofMinutes(it.startMinute, it.endMinute) }
        val declinedKeys = current.filter { it.status == PlanStatus.DECLINED }.map { it.demandKey }.toSet()

        val range = agenda.observe(date, 1).first()
        val day = range.days.first()
        val overview = study.overview(today).first()
        val settings = range.settings

        val fixed = day.timeline().filter { it.kind != EntryKind.FREE }.map { it.range } + blockedRanges
        val window = TimeRange(settings.dayStartFor(date), settings.dayEnd)
        var free = freeSlots(window, fixed, Duration.ofMinutes(15))
        if (date == today) {
            val start = minuteToTime(((nowMinute + 4) / 5) * 5)
            free = DayPlanner.from(free, start)
        }

        val alreadyPlanned = kept.groupBy { it.demandKey }.mapValues { (_, l) -> l.sumOf { it.endMinute - it.startMinute } }
        val demands = buildDemands(date, day, range.allTasks, overview, settings)
            .filter { it.key !in declinedKeys }
            .map { it.copy(minutes = it.minutes - (alreadyPlanned[it.key] ?: 0)) }
            .filter { it.minutes >= 10 }

        val config = PlannerConfig(typeWeight = typeWeights(settings, overview, date), preferences = preferenceModel())
        val planned = DayPlanner.plan(date, free, demands, config)
        dao.insertAll(
            planned.map {
                PlanBlockEntity(
                    dateEpochDay = epoch,
                    startMinute = it.range.start.minuteOfDay(),
                    endMinute = it.range.end.minuteOfDay(),
                    type = it.type,
                    demandKey = it.demandKey,
                    title = it.title,
                    refId = it.refId,
                    status = PlanStatus.PROPOSED,
                    createdAtMillis = System.currentTimeMillis(),
                )
            },
        )
    }

    suspend fun accept(block: PlanBlockEntity) {
        dao.setStatus(block.id, PlanStatus.ACCEPTED)
        feedback(block, FeedbackAction.ACCEPTED)
    }

    suspend fun done(block: PlanBlockEntity) {
        dao.setStatus(block.id, PlanStatus.DONE)
        feedback(block, FeedbackAction.DONE)
        val minutes = block.endMinute - block.startMinute
        when (block.type) {
            DemandType.HOMEWORK, DemandType.TASK -> block.refId?.let { id ->
                tasks.tasks.first().firstOrNull { it.id == id }?.let { task ->
                    // Erledigt, wenn die geplante Zeit die Schätzung abdeckt
                    val plannedSoFar = dao.forDay(block.dateEpochDay).filter { it.demandKey == block.demandKey && it.status == PlanStatus.DONE }
                        .sumOf { it.endMinute - it.startMinute }
                    if (plannedSoFar >= task.estimatedMinutes) tasks.setDone(task, true)
                }
            }
            DemandType.STUDY, DemandType.REVIEW -> study.recordSession(null, minutes, System.currentTimeMillis() - minutes * 60_000L)
            else -> Unit
        }
    }

    suspend fun decline(block: PlanBlockEntity) {
        dao.setStatus(block.id, PlanStatus.DECLINED)
        feedback(block, FeedbackAction.DECLINED)
        replan(LocalDate.ofEpochDay(block.dateEpochDay))
    }

    /** „Später“ – dieser Zeitpunkt passt nicht, Inhalt wird woanders eingeplant. */
    suspend fun later(block: PlanBlockEntity) {
        dao.setStatus(block.id, PlanStatus.MOVED)
        feedback(block, FeedbackAction.MOVED)
        replan(LocalDate.ofEpochDay(block.dateEpochDay))
    }

    private suspend fun feedback(block: PlanBlockEntity, action: FeedbackAction) {
        val date = LocalDate.ofEpochDay(block.dateEpochDay)
        val time = minuteToTime(block.startMinute)
        dao.insertFeedback(
            PlanFeedbackEntity(
                type = block.type,
                weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
                bucket = PreferenceModel.bucketOf(time),
                action = action,
                atMillis = System.currentTimeMillis(),
            ),
        )
    }

    /** Vorlieben der letzten 8 Wochen. */
    private suspend fun preferenceModel(): PreferenceModel {
        val since = System.currentTimeMillis() - 56L * 24 * 3600 * 1000
        val stats = dao.feedbackSince(since).groupBy { PreferenceModel.Key(it.type, it.weekend, it.bucket) }
            .mapValues { (_, list) ->
                PreferenceModel.Counts(
                    accepted = list.count { it.action == FeedbackAction.ACCEPTED || it.action == FeedbackAction.DONE },
                    declined = list.count { it.action == FeedbackAction.DECLINED || it.action == FeedbackAction.MOVED || it.action == FeedbackAction.MISSED },
                )
            }
        return PreferenceModel(stats)
    }

    private fun isExamPhase(overview: StudyOverview, date: LocalDate) =
        overview.upcoming.any { it.daysLeft(date) in 0..14 }

    /** Prioritäten aus den Einstellungen (Standard Schule > Sport > Etsy), in der Klausurphase verstärkt. */
    private fun typeWeights(settings: AppSettings, overview: StudyOverview, date: LocalDate): Map<DemandType, Double> {
        val rankWeights = listOf(1.3, 0.9, 0.6)
        fun weightOf(area: String) = rankWeights.getOrElse(settings.priorityOrder.indexOf(area).takeIf { it >= 0 } ?: 2) { 0.6 }
        val school = weightOf("SCHOOL") * if (isExamPhase(overview, date)) 1.2 else 1.0
        return mapOf(
            DemandType.HOMEWORK to school * 1.1,
            DemandType.STUDY to school,
            DemandType.REVIEW to school * 0.9,
            DemandType.SPORT to weightOf("SPORT"),
            DemandType.ETSY to weightOf("ETSY"),
            DemandType.TASK to 0.8,
        )
    }

    private fun buildDemands(
        date: LocalDate,
        day: DayAgenda,
        allTasks: List<TaskEntity>,
        overview: StudyOverview,
        settings: AppSettings,
    ): List<Demand> {
        val epoch = date.toEpochDay()
        val demands = mutableListOf<Demand>()
        val examPhase = isExamPhase(overview, date)

        // 1) Hausaufgaben & Aufgaben, die bald fällig sind
        allTasks.filter { !it.done }.forEach { task ->
            val due = task.dueEpochDay
            val soon = due != null && due <= epoch + 3
            val isEtsy = task.area == LifeArea.ETSY
            if (!soon && !(isEtsy && due == null)) return@forEach
            if (isEtsy && due == null) return@forEach // Etsy ohne Datum läuft über den Etsy-Block
            val overdue = due != null && due < epoch
            val type = when {
                task.kind == TaskKind.HOMEWORK -> DemandType.HOMEWORK
                task.area == LifeArea.ETSY -> DemandType.ETSY
                task.area == LifeArea.SPORT -> DemandType.SPORT
                else -> DemandType.TASK
            }
            demands += Demand(
                key = "task:${task.id}",
                type = type,
                title = if (task.kind == TaskKind.HOMEWORK) "Hausaufgabe: ${task.title}" else task.title,
                minutes = task.estimatedMinutes,
                priority = if (overdue) 1.3 else if (task.kind == TaskKind.HOMEWORK) 1.0 else 0.7,
                // Hausaufgaben sollen am Vortag fertig sein
                deadline = due?.let { LocalDate.ofEpochDay(if (task.kind == TaskKind.HOMEWORK) it - 1 else it) },
                minChunk = minOf(25, task.estimatedMinutes),
                maxChunk = 50,
                refId = task.id,
            )
        }

        // 2) Lernplan für diesen Tag (rückwärts von Klausuren)
        overview.plan.filter { it.date == date }.forEach { alloc ->
            demands += Demand(
                key = "study:${alloc.examId}:${alloc.topicId ?: 0}:${if (alloc.isReview) "r" else "l"}",
                type = if (alloc.isReview) DemandType.REVIEW else DemandType.STUDY,
                title = alloc.title,
                minutes = alloc.minutes,
                priority = 1.0,
                deadline = overview.exams.firstOrNull { it.exam.id == alloc.examId }?.date,
                minChunk = 25,
                maxChunk = 50,
                refId = alloc.examId,
            )
        }

        // 3) Fällige Wiederholungen (Spaced Repetition) – kurz, gut für die Bahn
        overview.dueReviews.forEach { (exam, topic) ->
            demands += Demand(
                key = "review:${topic.id}",
                type = DemandType.REVIEW,
                title = "Wiederholen: ${exam.subject?.code ?: ""} ${topic.title}".trim(),
                minutes = 10,
                priority = 0.8,
                minChunk = 10,
                maxChunk = 15,
                refId = topic.id,
            )
        }

        // 4) Etsy: >5 Std/Woche, in der Klausurphase reduziert
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val etsyMinutes = when {
            examPhase -> 25
            weekend -> 75
            else -> 45
        }
        val nextEtsy = allTasks.firstOrNull { !it.done && it.area == LifeArea.ETSY }
        demands += Demand(
            key = "etsy:daily",
            type = DemandType.ETSY,
            title = "Etsy: " + (nextEtsy?.title ?: "Motive & Listings"),
            minutes = etsyMinutes,
            priority = 0.6,
            minChunk = 25,
            maxChunk = 50,
            refId = nextEtsy?.id,
        )

        // 5) Sport: Lauftraining Richtung 10 km an Tagen ohne Floorball und ohne Job
        val hasTraining = day.routines.any { it.area == LifeArea.SPORT }
        val hasJob = day.shifts.isNotEmpty()
        if (!hasTraining && !hasJob && date.dayOfWeek in setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)) {
            demands += Demand(
                key = "sport:run",
                type = DemandType.SPORT,
                title = "Laufen (Ziel 10 km) – locker, 30 Min",
                minutes = if (examPhase) 25 else 35,
                priority = 0.8,
                minChunk = 25,
                maxChunk = 45,
            )
        }
        return demands
    }
}
