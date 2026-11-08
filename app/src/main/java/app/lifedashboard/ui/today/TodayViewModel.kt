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
        val now: LocalDateTime,
    ) : TodayUiState
}

private data class Inputs(val range: AgendaRange, val plan: List<PlanBlockEntity>, val study: StudyOverview, val habits: List<HabitStatus>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    agenda: AgendaRepository,
    private val tasks: TaskRepository,
    private val engine: PlanningEngine,
    study: StudyRepository,
    private val goals: GoalRepository,
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

    private val inputs = date.flatMapLatest { d ->
        combine(agenda.observe(d, 2), engine.observeDay(d), study.overview(d), goals.habits(d)) { r, p, s, h -> Inputs(r, p, s, h) }
    }

    val state: StateFlow<TodayUiState> = inputs
        .combine(clock) { input, now -> build(input, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    init {
        // Plan automatisch aktualisieren, wenn sich Termine/Aufgaben/Klausuren ändern oder 15 Min vergehen
        viewModelScope.launch {
            date.flatMapLatest { d ->
                combine(agenda.observe(d, 1), study.overview(d), clock.map { it.minute / 15 }.distinctUntilChanged()) { _, _, _ -> d }
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
            overdue.isNotEmpty() -> "${overdue.size} ${if (overdue.size == 1) "Aufgabe ist" else "Aufgaben sind"} überfällig. Heute weg damit – fang mit 5 Minuten an."
            nextExam != null && nextExam.daysLeft(today.date) in 1..3 -> "${nextExam.subject?.name ?: "Klausur"} in ${nextExam.daysLeft(today.date)} ${if (nextExam.daysLeft(today.date) == 1) "Tag" else "Tagen"}. Heute zählt. 💪"
            homeworkTomorrow > 0 -> "$homeworkTomorrow ${if (homeworkTomorrow == 1) "Hausaufgabe" else "Hausaufgaben"} bis morgen. Jetzt 5 Minuten anfangen, nicht später. 💪"
            current != null && current.kind == EntryKind.LESSON -> "Konzentrier dich – um ${"%02d:%02d".format(current.range.end.hour, current.range.end.minute)} ist der Block vorbei."
            nextPlan != null -> "Als Nächstes geplant: ${nextPlan.title}. Nur anfangen – der Rest kommt von allein."
            freeLeft >= Duration.ofHours(1) -> "Noch ${freeLeft.pretty()} frei heute. Nutz sie für etwas, das dich weiterbringt."
            tomorrow != null && tomorrow.lessons.isNotEmpty() && time.hour >= 21 -> "Ab ins Bett – morgen ist Schule. 😴"
            else -> "Läuft. Bleib dran. 💪"
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
            now = now,
        )
    }
}
