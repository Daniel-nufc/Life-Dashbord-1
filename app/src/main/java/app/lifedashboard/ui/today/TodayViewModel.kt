package app.lifedashboard.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.planning.PlanningEngine
import app.lifedashboard.data.repository.AgendaRange
import app.lifedashboard.data.repository.AgendaRepository
import app.lifedashboard.data.repository.ExamWithTopics
import app.lifedashboard.data.repository.GoalRepository
import app.lifedashboard.data.repository.nextStepHint
import app.lifedashboard.data.repository.HabitStatus
import app.lifedashboard.data.repository.StudyOverview
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.data.repository.TaskRepository
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.components.GERMAN
import app.lifedashboard.ui.components.pretty
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Ready(
        val greeting: String,
        val dateLabel: String,
        val weekType: WeekType,
        val isSchoolDay: Boolean,
        val holiday: String?,
        val examsToday: List<String>,
        val coachLine: String,
        val entries: List<TimelineEntry>,
        val allDayEvents: List<EventEntity>,
        val externalAllDay: List<String>,
        val current: TimelineEntry?,
        val next: TimelineEntry?,
        val freeTimeLeft: Duration,
        val freeTimeTotal: Duration,
        val focusTasks: List<TaskEntity>,
        val openTaskCount: Int,
        val subjects: Map<Long, SubjectEntity>,
        val events: List<EventEntity>,
        val shifts: List<JobShiftEntity>,
        val routines: List<RoutineBlockEntity>,
        val proposals: Int,
        val doneBlocks: Int,
        val plannedBlocks: Int,
        val nextExam: ExamWithTopics?,
        val habits: List<HabitStatus>,
        val cards: List<String>,
        val monthSpentCents: Long,
        val budgetTotalCents: Long,
        val availableCents: Long,
        val etsyRevenueCents: Long,
        val etsyOrders: Int,
        val etsyNextStep: String?,
        val now: LocalDateTime,
        /** Nächster oder laufender Block aus dem Tagesplan */
        val nextBlock: PlanBlockEntity? = null,
        /** Heute schon verpasste Schul-Blöcke */
        val missedToday: Int = 0,
        val schoolBlocksLeft: Int = 0,
        /** Abend-Check fällig (ab 18 Uhr, Energie für morgen noch nicht angegeben) */
        val askEnergy: Boolean = false,
        /** Heute leichter/mehr geplant, weil … (Abend-Check, Schlaf) */
        val wellbeingNote: String? = null,
        /** Nach der Schule: die Fächer von heute – Hausaufgaben mit einem Tipp erfassen */
        val homeworkSubjects: List<SubjectEntity> = emptyList(),
    ) : TodayUiState
}

private data class Inputs(
    val range: AgendaRange,
    val plan: List<PlanBlockEntity>,
    val study: StudyOverview,
    val habits: List<HabitStatus>,
    val extra: Extra,
)

private data class Extra(val money: app.lifedashboard.data.repository.FinanceOverview?, val etsy: app.lifedashboard.data.repository.EtsyOverview?)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    agenda: AgendaRepository,
    private val tasks: TaskRepository,
    private val engine: PlanningEngine,
    private val studyRepo: StudyRepository,
    private val goals: GoalRepository,
    private val finance: app.lifedashboard.data.repository.FinanceRepository,
    private val etsy: app.lifedashboard.data.repository.EtsyRepository,
    private val automation: app.lifedashboard.data.repository.AutomationRepository,
    progressRepository: app.lifedashboard.data.repository.ProgressRepository,
    private val settings: app.lifedashboard.data.settings.SettingsRepository,
    private val usage: app.lifedashboard.data.sync.UsageRepository,
) : ViewModel() {



    /** Tickt jede Minute, damit „Jetzt“ und „Als Nächstes“ aktuell bleiben. */
    private val clock = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay((60 - now.second) * 1000L)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val date = clock.map { it.toLocalDate() }.distinctUntilChanged()

    /** Plan für morgen – wird abends bestätigt */
    val tomorrowPlan: StateFlow<List<PlanBlockEntity>> = date.flatMapLatest { engine.observeDay(it.plusDays(1)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Spiegel: Social-Media-Zeit heute (null = keine Erlaubnis) – alle 15 Minuten neu */
    val socialToday: StateFlow<Duration?> = clock.map { it.minute / 15 }.distinctUntilChanged()
        .map { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { usage.socialTime(LocalDate.now()) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun redeemGaming(minutes: Int) = launch { settings.redeemGaming(minutes) }
    fun acceptTomorrow() = launch { engine.acceptDay(LocalDate.now().plusDays(1)) }
    fun miniSport(block: PlanBlockEntity) = launch { engine.miniSport(block) }

    private val inputs = date.flatMapLatest { d ->
        val extra = combine(finance.overview(d), etsy.overview(d)) { m, e -> Extra(m, e) }
        combine(agenda.observe(d, 2), engine.observeDay(d), studyRepo.overview(d), goals.habits(d), extra) { r, p, s, h, x -> Inputs(r, p, s, h, x) }
    }

    /** Level, Serie und Wochenziele für die Fortschritts-Karte */
    val progress: StateFlow<app.lifedashboard.planner.ProgressState?> = date.flatMapLatest { progressRepository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<TodayUiState> = inputs
        .combine(clock) { input, now -> build(input, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    init {
        viewModelScope.launch { runCatching { automation.autoCheckHabits() } }
        // Plan automatisch aktualisieren, wenn sich Termine/Aufgaben/Klausuren ändern oder 15 Min vergehen
        viewModelScope.launch {
            date.flatMapLatest { d ->
                combine(agenda.observe(d, 1), studyRepo.overview(d), clock.map { it.minute / 15 }.distinctUntilChanged()) { _, _, _ -> d }
            }.debounce(800).collect { runCatching { engine.replan(it) } }
        }
    }

    fun toggleTask(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task, done) }
    }

    fun accept(block: PlanBlockEntity) = launch { engine.accept(block) }
    fun done(block: PlanBlockEntity) = launch { engine.done(block) }
    fun decline(block: PlanBlockEntity) = launch { engine.decline(block) }
    fun later(block: PlanBlockEntity) = launch { engine.later(block) }

    /** Abend-Check: Energie für morgen (1 = platt … 5 = fit) – morgen wird danach geplant. */
    fun setEnergy(level: Int) = launch {
        val tomorrow = LocalDate.now().plusDays(1)
        settings.setEnergy(tomorrow, level)
        engine.replan(tomorrow)
    }

    /** Thema zum Lern-Block (für „Wie gut sitzt es jetzt?“ nach dem Fokus) */
    suspend fun topicFor(block: PlanBlockEntity): app.lifedashboard.data.db.TopicEntity? {
        if (!block.demandKey.startsWith("study:")) return null
        val topicId = block.demandKey.split(":").getOrNull(3)?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return studyRepo.topic(topicId)
    }

    /** Verständnis nach dem Lernen – senkt die restliche Lernzeit für das Thema. */
    fun setUnderstanding(topic: app.lifedashboard.data.db.TopicEntity, progress: Int) = launch { studyRepo.setProgress(topic, progress) }

    /** Selbst auf eine Uhrzeit gelegt: bleibt fest, der Rest des Tages plant drumherum. */
    fun move(block: PlanBlockEntity, time: java.time.LocalTime) =
        launch { engine.pin(block, LocalDate.ofEpochDay(block.dateEpochDay), time.hour * 60 + time.minute) }
    fun replanNow() = launch { engine.replan(LocalDate.now()) }
    fun toggleHabit(id: Long, done: Boolean) = launch { goals.toggleHabit(id, LocalDate.now(), done) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    private fun build(input: Inputs, now: LocalDateTime): TodayUiState {
        val range = input.range
        val today = range.days.first()
        val tomorrow = range.days.getOrNull(1)
        val entries = today.timeline(input.plan)
        val time = now.toLocalTime()
        val current = entries.firstOrNull { time in it.range && it.kind != EntryKind.FREE } ?: entries.firstOrNull { time in it.range }
        val next = entries.firstOrNull { it.range.start.isAfter(time) && it.kind != EntryKind.FREE }
        val freeEntries = entries.filter { it.kind == EntryKind.FREE }
        val freeTotal = freeEntries.fold(Duration.ZERO) { acc, e -> acc + e.range.duration }
        val freeLeft = freeEntries.filter { it.range.end.isAfter(time) }.fold(Duration.ZERO) { acc, e ->
            val start = if (e.range.start.isAfter(time)) e.range.start else time
            acc + Duration.between(start, e.range.end)
        }

        val todayEpoch = today.date.toEpochDay()
        val open = range.allTasks.filter { !it.done }
        val overdue = open.filter { (it.dueEpochDay ?: Long.MAX_VALUE) < todayEpoch }
        val dueToday = open.filter { it.dueEpochDay == todayEpoch }
        val dueTomorrow = open.filter { it.dueEpochDay == todayEpoch + 1 }
        val homeworkTomorrow = dueTomorrow.count { it.kind == TaskKind.HOMEWORK }
        val focus = (overdue + dueToday + dueTomorrow).distinctBy { it.id }.ifEmpty {
            open.filter { it.dueEpochDay != null }.take(3).ifEmpty { open.take(3) }
        }
        val proposals = input.plan.count { it.status == PlanStatus.PROPOSED }
        val nextPlan = input.plan.firstOrNull { (it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED) && it.startMinute >= time.hour * 60 + time.minute }
        val nextExam = input.study.upcoming.firstOrNull()

        val coach = when {
            overdue.isNotEmpty() -> "${overdue.size} ${if (overdue.size == 1) "Aufgabe ist" else "Aufgaben sind"} überfällig. Keine Ausreden – heute ist sie weg."
            nextExam != null && nextExam.daysLeft(today.date) in 1..3 -> "${nextExam.subject?.name ?: "Klausur"} in ${nextExam.daysLeft(today.date)} ${if (nextExam.daysLeft(today.date) == 1) "Tag" else "Tagen"}. Heute zählt."
            homeworkTomorrow > 0 -> "$homeworkTomorrow ${if (homeworkTomorrow == 1) "Hausaufgabe" else "Hausaufgaben"} bis morgen. Jetzt 5 Minuten anfangen, nicht später."
            current != null && current.kind == EntryKind.LESSON -> "Konzentrier dich – um ${"%02d:%02d".format(current.range.end.hour, current.range.end.minute)} ist der Block vorbei."
            nextPlan != null -> "Nicht nachdenken, anfangen. Die ersten 5 Minuten sind die schwersten."
            freeLeft >= Duration.ofHours(1) -> "Noch ${freeLeft.pretty()} frei heute. Nutz sie für etwas, das dich weiterbringt."
            tomorrow != null && tomorrow.lessons.isNotEmpty() && time.hour >= 21 -> "Ab ins Bett – morgen ist Schule."
            else -> "Läuft. Bleib dran."
        }

        val hour = now.hour
        val greeting = when {
            hour < 11 -> "Guten Morgen"
            hour < 17 -> "Hallo"
            hour < 22 -> "Guten Abend"
            else -> "Gute Nacht"
        }

        return TodayUiState.Ready(
            greeting = "$greeting, ${range.settings.userName}",
            dateLabel = today.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", GERMAN)),
            weekType = today.weekType,
            isSchoolDay = today.lessons.isNotEmpty(),
            holiday = today.holiday,
            examsToday = today.exams.map { e -> range.subjects[e.subjectId]?.name ?: e.title },
            coachLine = coach,
            entries = entries,
            allDayEvents = today.events.filter { it.startMinute == null },
            externalAllDay = today.externalEvents.filter { it.startMinute == null }.map { it.title },
            current = current,
            next = next,
            freeTimeLeft = freeLeft,
            freeTimeTotal = freeTotal,
            focusTasks = focus.take(6),
            openTaskCount = open.size,
            subjects = range.subjects,
            events = today.events,
            shifts = today.shifts,
            routines = today.routines,
            proposals = proposals,
            doneBlocks = input.plan.count { it.status == PlanStatus.DONE },
            plannedBlocks = input.plan.count { it.status != PlanStatus.DECLINED && it.status != PlanStatus.MOVED },
            nextExam = nextExam,
            habits = input.habits,
            cards = range.settings.todayCards.filter { it !in range.settings.hiddenTodayCards },
            monthSpentCents = input.extra.money?.monthSpentCents ?: 0,
            budgetTotalCents = input.extra.money?.budgets?.sumOf { it.limitCents } ?: 0,
            availableCents = input.extra.money?.availableCents ?: 0,
            etsyRevenueCents = input.extra.etsy?.thisMonth?.revenueCents ?: 0,
            etsyOrders = input.extra.etsy?.thisMonth?.orders ?: 0,
            etsyNextStep = input.extra.etsy?.nextDesign?.let { "${it.title}: ${it.status.nextStepHint()}" },
            now = now,
            nextBlock = input.plan.filter { (it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED) && it.endMinute > time.hour * 60 + time.minute }
                .minByOrNull { it.startMinute },
            missedToday = input.plan.count { it.status == PlanStatus.MISSED && it.type.isSchoolType() },
            schoolBlocksLeft = input.plan.count { (it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED) && it.type.isSchoolType() },
            askEnergy = hour >= 18 && today.date.plusDays(1).toEpochDay() !in range.settings.energyLog,
            wellbeingNote = today.wellbeingAdjust().takeIf { it != 0 && today.schoolCapMinutes() > 0 }?.let { delta ->
                val why = listOfNotNull(
                    when (range.settings.energyLog[today.date.toEpochDay()]) {
                        1, 2 -> "du warst gestern Abend platt"
                        4, 5 -> "du warst gestern Abend fit"
                        else -> null
                    },
                    if (today.date.toEpochDay() in range.settings.shortSleepDays) "du hast wenig geschlafen" else null,
                ).joinToString(" und ")
                if (delta < 0) "Heute ${-delta} Min leichter geplant – $why." else "Heute $delta Min mehr drin – $why."
            },
            homeworkSubjects = run {
                val lastLessonEnd = today.lessons.mapNotNull { it.timeRange?.end }.maxOrNull()
                if (lastLessonEnd == null || time.isBefore(lastLessonEnd) || hour >= 22) emptyList()
                else today.lessons.map { it.subject }.distinctBy { it.id }
            },
        )
    }
}

fun app.lifedashboard.planner.DemandType.isSchoolType() =
    this == app.lifedashboard.planner.DemandType.STUDY || this == app.lifedashboard.planner.DemandType.HOMEWORK || this == app.lifedashboard.planner.DemandType.REVIEW
