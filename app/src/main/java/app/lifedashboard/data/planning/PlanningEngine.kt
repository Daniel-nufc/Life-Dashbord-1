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
import app.lifedashboard.planner.mondayOfWeek
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
    private val training: app.lifedashboard.data.repository.TrainingRepository,
    private val etsyDao: app.lifedashboard.data.db.EtsyDao,
    private val goalDao: app.lifedashboard.data.db.GoalDao,
) {
    private val mutex = Mutex()

    private companion object {
        /** So weit im Voraus wird verteilt (heute + 13 Tage) */
        const val HORIZON_DAYS = 14
    }

    fun observeDay(date: LocalDate): Flow<List<PlanBlockEntity>> = dao.observeDay(date.toEpochDay())

    /**
     * Plant einen Tag neu. Für heute werden die nächsten zwei Tage gleich mitgeplant, weil sich die
     * gleichmäßige Verteilung der Aufgaben verschieben kann, wenn heute weniger Zeit übrig ist.
     */
    suspend fun replan(date: LocalDate = LocalDate.now()) = mutex.withLock {
        val balance = balance()
        replanLocked(date, balance)
        if (date == LocalDate.now()) (1L..2L).forEach { replanLocked(date.plusDays(it), balance) }
    }

    /** Plant mehrere Tage nacheinander (z. B. die ganze Woche). Vergangene Tage werden übersprungen. */
    suspend fun planDays(from: LocalDate, days: Int) = mutex.withLock {
        val today = LocalDate.now()
        val balance = balance()
        (0 until days).map { from.plusDays(it.toLong()) }.filter { !it.isBefore(today) }.forEach { replanLocked(it, balance) }
    }

    /** „Woche übernehmen“: alle Vorschläge im Zeitraum annehmen. */
    suspend fun acceptAll(from: LocalDate, to: LocalDate) = mutex.withLock {
        dao.between(from.toEpochDay(), to.toEpochDay()).filter { it.status == PlanStatus.PROPOSED }.forEach {
            dao.setStatus(it.id, PlanStatus.ACCEPTED)
            feedback(it, FeedbackAction.ACCEPTED)
        }
    }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlanBlockEntity>> = dao.observeBetween(from.toEpochDay(), to.toEpochDay())

    /**
     * Verteilt Hausaufgaben und Aufgaben mit Termin gleichmäßig auf die nächsten zwei Wochen:
     * Jeder Block kommt auf den Tag (bis zur Abgabe), der danach am wenigsten ausgelastet ist.
     * Lernplan, Sport-Zeitfenster und schon angenommene Blöcke zählen als vorhandene Last.
     * Ergebnis: Schlüssel „task:ID“ → Minuten je Tag.
     */
    private suspend fun balance(): Map<String, Map<LocalDate, Int>> {
        val today = LocalDate.now()
        val range = agenda.observe(today, HORIZON_DAYS).first()
        val overview = study.overview(today).first()
        val settings = range.settings
        val nowMinute = LocalTime.now().minuteOfDay()
        val kept = dao.between(today.toEpochDay(), today.toEpochDay() + HORIZON_DAYS - 1)
            .filter { it.status == PlanStatus.ACCEPTED || it.status == PlanStatus.DONE }
        val keptByKey = kept.groupBy { it.demandKey }.mapValues { (_, l) -> l.sumOf { it.endMinute - it.startMinute } }

        val days = range.days.map { day ->
            val window = TimeRange(settings.dayStartFor(day.date, day.holiday != null), settings.dayEnd)
            var free = freeSlots(window, day.timeline().filter { it.kind != EntryKind.FREE }.map { it.range }, Duration.ofMinutes(15))
            if (day.date == today) free = DayPlanner.from(free, minuteToTime(((nowMinute + 4) / 5) * 5))
            val totalFree = free.sumOf { it.duration.toMinutes() }.toInt()
            val capacity = minOf(dailyLimit(day), totalFree - 60).coerceAtLeast(0)
            val studyLoad = overview.plan.filter { it.date == day.date }.sumOf { it.minutes }
            val sportLoad = runCatching { training.sessionsFor(day.date) }.getOrDefault(emptyList())
                .filter { !it.done && !it.skipped }.sumOf { it.session.minutes }
            val keptLoad = kept.filter { it.dateEpochDay == day.date.toEpochDay() && !it.demandKey.startsWith("study:") && !it.demandKey.startsWith("train:") }
                .sumOf { it.endMinute - it.startMinute }
            app.lifedashboard.planner.BalanceDay(day.date, capacity, studyLoad + sportLoad + keptLoad)
        }
        val items = range.allTasks.filter { !it.done && it.dueEpochDay != null && it.dueEpochDay <= today.toEpochDay() + 7 }
            .mapNotNull { task ->
                val remaining = task.estimatedMinutes - (keptByKey["task:${task.id}"] ?: 0)
                if (remaining < 10) return@mapNotNull null
                val due = LocalDate.ofEpochDay(task.dueEpochDay!!)
                // Hausaufgaben sollen am Vortag fertig sein
                val deadline = if (task.kind == TaskKind.HOMEWORK) due.minusDays(1) else due
                app.lifedashboard.planner.BalanceItem("task:${task.id}", remaining, deadline)
            }
        return app.lifedashboard.planner.LoadBalancer.assign(days, items)
    }

    /** Tageslimit: werktags max. 3 Std, am Wochenende/in den Ferien 4 Std. */
    private fun dailyLimit(day: DayAgenda) = if (day.isSchoolFree) 240 else 180

    private suspend fun replanLocked(date: LocalDate, balance: Map<String, Map<LocalDate, Int>>) {
        val today = LocalDate.now()
        if (date.isBefore(today)) return
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
        val window = TimeRange(settings.dayStartFor(date, day.holiday != null), settings.dayEnd)
        var free = freeSlots(window, fixed, Duration.ofMinutes(15))
        if (date == today) {
            val start = minuteToTime(((nowMinute + 4) / 5) * 5)
            free = DayPlanner.from(free, start)
        }

        // Was ist schon an ANDEREN Tagen geplant? (z. B. Hausaufgabe am Montag → nicht nochmal am Dienstag)
        val windowBlocks = dao.between(today.toEpochDay(), today.toEpochDay() + 14)
            .filter { it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED || it.status == PlanStatus.DONE }
        val alreadyPlanned = (windowBlocks.filter { it.dateEpochDay != epoch } + kept)
            .groupBy { it.demandKey }.mapValues { (_, l) -> l.sumOf { it.endMinute - it.startMinute } }

        val trainings = runCatching { training.sessionsFor(date) }.getOrDefault(emptyList()).filter { !it.done && !it.skipped }
        val core = buildDemands(date, day, range.allTasks, overview, settings, balance)
        // Etsy und Ziel-Schritte füllen nur leichte Tage auf – an vollen Tagen bleiben sie weg (gleichmäßige Wochen)
        val coreMinutes = core.sumOf { it.minutes } + trainings.sumOf { it.session.minutes } + kept.sumOf { it.endMinute - it.startMinute }
        val comfort = if (day.isSchoolFree) 150 else 100
        val extra = if (coreMinutes >= comfort) emptyList() else etsyDemands(date, day, range.allTasks, overview) + goalDemands(date, day)
        val demands = (core + extra + trainings.map { t ->
            Demand(
                key = t.session.key,
                type = DemandType.SPORT,
                title = "Sport · ${t.session.minutes} Min (du entscheidest was)",
                minutes = t.session.minutes,
                priority = 0.9,
                minChunk = t.session.minutes,
                maxChunk = t.session.minutes,
            )
        })
            .filter { it.key !in declinedKeys }
            // Verteilte Aufgaben haben ihre Minuten für genau diesen Tag schon bekommen
            .map { if (it.key in balance) it else it.copy(minutes = it.minutes - (alreadyPlanned[it.key] ?: 0)) }
            .filter { it.minutes >= 10 }

        // Tageslimit: werktags max. 3 Std, am Wochenende/in den Ferien 4 Std – und mind. 1 Std echte Freizeit bleibt
        val totalFree = free.sumOf { it.duration.toMinutes() }.toInt()
        val alreadyToday = kept.sumOf { it.endMinute - it.startMinute }
        val cap = dailyLimit(day) - alreadyToday
        val budget = minOf(cap, totalFree - 60).coerceAtLeast(0)

        val config = PlannerConfig(typeWeight = typeWeights(settings, overview, date), preferences = preferenceModel())
        val planned = DayPlanner.plan(date, free, demands, config, maxTotalMinutes = budget)
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
            DemandType.SPORT -> trainingFor(block)?.let { training.markDone(it.session) }
            DemandType.ETSY -> if (block.demandKey.startsWith("etsy:design:")) {
                // Schritt erledigt → Motiv rückt in der Pipeline eine Station weiter
                etsyDao.observeDesigns().first().firstOrNull { it.id == block.refId }?.let { d ->
                    app.lifedashboard.data.db.DesignStatus.entries.getOrNull(d.status.ordinal + 1)?.let { etsyDao.upsertDesign(d.copy(status = it)) }
                }
            }
            else -> Unit
        }
    }

    /**
     * Etsy: aus der Motiv-Pipeline die nächsten Schritte mit Dauer; fehlen Motive für die nächste Saison,
     * kommt „Neue Motive erstellen“ dazu. Tagesbudget werktags 45, am Wochenende 75, Klausurphase 25 Min.
     */
    private suspend fun etsyDemands(date: LocalDate, day: app.lifedashboard.data.repository.DayAgenda, tasks: List<app.lifedashboard.data.db.TaskEntity>, overview: StudyOverview): List<Demand> {
        val epoch = date.toEpochDay()
        var budget = when {
            isExamPhase(overview, date) -> 25
            day.isSchoolFree -> 75
            else -> 45
        }
        val (season, deadline) = app.lifedashboard.planner.EtsyMath.nextSeason(date)
        val result = mutableListOf<Demand>()
        val designs = etsyDao.observeDesigns().first().filter { it.status != app.lifedashboard.data.db.DesignStatus.ONLINE }
            .sortedByDescending { it.status.ordinal }
        for (d in designs) {
            val (minutes, step) = when (d.status) {
                app.lifedashboard.data.db.DesignStatus.IDEA -> 60 to "Motiv in Canva erstellen"
                app.lifedashboard.data.db.DesignStatus.CREATED -> 20 to "Qualität prüfen"
                app.lifedashboard.data.db.DesignStatus.CHECKED -> 30 to "Probedruck mit Oma"
                app.lifedashboard.data.db.DesignStatus.TEST_PRINT -> 45 to "Mockup + Titelbild"
                app.lifedashboard.data.db.DesignStatus.MOCKUP -> 30 to "Listing schreiben"
                app.lifedashboard.data.db.DesignStatus.LISTING -> 20 to "Hochladen (KI-Häkchen!)"
                app.lifedashboard.data.db.DesignStatus.ONLINE -> continue
            }
            if (budget < minutes) continue
            result += Demand("etsy:design:${d.id}:${d.status.name}", DemandType.ETSY, "Etsy: $step – ${d.title}", minutes, 0.65, deadline, minChunk = minutes, maxChunk = minutes, refId = d.id)
            budget -= minutes
        }
        // Neue Motive für die nächste Saison (Ziel: 3 pro Woche)
        val monday = date.mondayOfWeek()
        val newThisWeek = etsyDao.observeDesigns().first().count {
            java.time.Instant.ofEpochMilli(it.createdAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() >= monday
        }
        if (newThisWeek < 3 && budget >= 30) {
            result += Demand("etsy:new:$epoch", DemandType.ETSY, "Etsy: neue Motive fürs $season-Set (bis ${deadline.dayOfMonth}.${deadline.monthValue}. online)", minOf(budget, 60), 0.6, deadline, minChunk = 30, maxChunk = 60)
        }
        // Etsy-To-dos ohne Datum (z. B. Account erstellen) nach vorne holen, solange Budget übrig
        tasks.filter { !it.done && it.area == app.lifedashboard.data.db.LifeArea.ETSY && it.dueEpochDay == null }.take(1).forEach { t ->
            if (budget >= t.estimatedMinutes.coerceAtMost(60)) {
                result += Demand("task:${t.id}", DemandType.ETSY, t.title, t.estimatedMinutes, 0.6, deadline, minChunk = minOf(25, t.estimatedMinutes), maxChunk = 50, refId = t.id)
            }
        }
        return result
    }

    /** Ziele: an freien Tagen den nächsten Schritt jedes Ziels vorschlagen (Ironman & Abi laufen über Training/Lernen). */
    private suspend fun goalDemands(date: LocalDate, day: app.lifedashboard.data.repository.DayAgenda): List<Demand> {
        if (!day.isSchoolFree) return emptyList()
        val milestones = goalDao.observeMilestones().first()
        return goalDao.observeGoals().first()
            .filter { !it.done && !it.title.contains("Ironman", true) && !it.title.contains("Abi", true) }
            .mapNotNull { g ->
                val next = milestones.filter { it.goalId == g.id && !it.done }.minByOrNull { it.sortOrder } ?: return@mapNotNull null
                Demand("goal:${next.id}", DemandType.TASK, "Ziel ${g.title}: ${next.title}", 30, 0.55, minChunk = 30, maxChunk = 30, refId = next.id)
            }
    }

    private suspend fun trainingFor(block: PlanBlockEntity) =
        if (block.demandKey.startsWith("train:")) training.sessionsFor(LocalDate.ofEpochDay(block.dateEpochDay)).firstOrNull { it.session.key == block.demandKey } else null

    suspend fun decline(block: PlanBlockEntity) {
        dao.setStatus(block.id, PlanStatus.DECLINED)
        feedback(block, FeedbackAction.DECLINED)
        // Training heute nicht → fällt aus, wird nicht nachgeholt (Plan rutscht sinnvoll nach)
        trainingFor(block)?.let { training.skip(it.session) }
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
        balance: Map<String, Map<LocalDate, Int>>,
    ): List<Demand> {
        val epoch = date.toEpochDay()
        val demands = mutableListOf<Demand>()
        val examPhase = isExamPhase(overview, date)

        // 1) Hausaufgaben & Aufgaben mit Termin – nur der Anteil, den die Wochen-Verteilung diesem Tag gibt
        allTasks.filter { !it.done }.forEach { task ->
            val minutes = balance["task:${task.id}"]?.get(date) ?: return@forEach
            val due = task.dueEpochDay
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
                minutes = minutes,
                priority = if (overdue) 1.3 else if (task.kind == TaskKind.HOMEWORK) 1.0 else 0.7,
                // Hausaufgaben sollen am Vortag fertig sein
                deadline = due?.let { LocalDate.ofEpochDay(if (task.kind == TaskKind.HOMEWORK) it - 1 else it) },
                minChunk = minOf(25, minutes),
                maxChunk = 60,
                refId = task.id,
            )
        }

        // 2) Lernplan für diesen Tag (rückwärts von Klausuren)
        overview.plan.filter { it.date == date }.forEach { alloc ->
            demands += Demand(
                key = "study:$epoch:${alloc.examId}:${alloc.topicId ?: 0}:${if (alloc.isReview) "r" else "l"}",
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

        // 3) Fällige Wiederholungen (Spaced Repetition): nur heute und als EIN kurzer Block statt vieler kleiner
        if (date == LocalDate.now() && overview.dueReviews.isNotEmpty()) {
            val topics = overview.dueReviews
            demands += Demand(
                key = "review:$epoch",
                type = DemandType.REVIEW,
                title = if (topics.size == 1) {
                    "Wiederholen: ${topics[0].first.subject?.code ?: ""} ${topics[0].second.title}".trim()
                } else {
                    "Wiederholen: ${topics.size} Themen (${topics.take(2).joinToString { it.second.title }}${if (topics.size > 2) " …" else ""})"
                },
                minutes = (topics.size * 10).coerceAtMost(30),
                priority = 0.8,
                minChunk = 10,
                maxChunk = 30,
                refId = topics[0].second.id,
            )
        }

        return demands
    }
}
