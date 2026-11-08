package app.lifedashboard.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.repository.AgendaRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        val now: LocalDateTime,
    ) : TodayUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    agenda: AgendaRepository,
    private val tasks: TaskRepository,
) : ViewModel() {

    /** Tickt jede Minute, damit „Jetzt“ und „Als Nächstes“ aktuell bleiben. */
    private val clock = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay((60 - now.second) * 1000L)
        }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val state: StateFlow<TodayUiState> = clock
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { date -> agenda.observe(date, 2) }
        .combine(clock) { range, now -> build(range, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    fun toggleTask(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task, done) }
    }

    private fun build(range: app.lifedashboard.data.repository.AgendaRange, now: LocalDateTime): TodayUiState {
        val today = range.days.first()
        val tomorrow = range.days.getOrNull(1)
        val entries = today.timeline()
        val time = now.toLocalTime()
        val current = entries.firstOrNull { time in it.range }
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

        val coach = when {
            overdue.isNotEmpty() -> "${overdue.size} ${if (overdue.size == 1) "Aufgabe ist" else "Aufgaben sind"} überfällig. Heute weg damit – fang mit 5 Minuten an."
            homeworkTomorrow > 0 -> "$homeworkTomorrow ${if (homeworkTomorrow == 1) "Hausaufgabe" else "Hausaufgaben"} bis morgen. Jetzt 5 Minuten anfangen, nicht später. 💪"
            current != null && current.kind == EntryKind.LESSON -> "Konzentrier dich – um ${"%02d:%02d".format(current.range.end.hour, current.range.end.minute)} ist der Block vorbei."
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
            now = now,
        )
    }

    companion object {
        fun todayDate(): LocalDate = LocalDate.now()
    }
}
