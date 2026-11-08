package app.lifedashboard.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.repository.RoutineRepository
import app.lifedashboard.data.repository.TimetableRepository
import app.lifedashboard.data.repository.forDay
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.components.GERMAN
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed interface TodayUiState {
    data object Loading : TodayUiState

    data class Ready(
        val dateLabel: String,
        val weekType: WeekType,
        val isSchoolDay: Boolean,
        val entries: List<TimelineEntry>,
        val current: TimelineEntry?,
        val next: TimelineEntry?,
        val freeTimeLeft: Duration,
        val now: LocalDateTime,
    ) : TodayUiState
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    timetable: TimetableRepository,
    routines: RoutineRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** Tickt jede Minute, damit „Jetzt“ und „Als Nächstes“ aktuell bleiben. */
    private val clock = flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay((60 - now.second) * 1000L)
        }
    }

    val state: StateFlow<TodayUiState> =
        combine(clock, timetable.lessons, routines.routines, settings.settings) { now, lessons, routineList, s ->
            val today = now.toLocalDate()
            val weekType = s.weekCalculator().weekTypeFor(today)
            val isWeekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
            val todaysLessons = if (isWeekend) emptyList() else lessons.forDay(today.dayOfWeek, weekType)

            val entries = DayTimelineBuilder.build(
                lessons = todaysLessons,
                routines = routineList.forDay(today.dayOfWeek),
                dayStart = s.dayStart,
                dayEnd = s.dayEnd,
                commuteMinutes = s.schoolCommuteMinutes,
            )
            val time = now.toLocalTime()
            val current = entries.firstOrNull { time in it.range }
            val next = entries.firstOrNull { it.range.start.isAfter(time) && it.kind != EntryKind.FREE }
            val freeLeft = entries.filter { it.kind == EntryKind.FREE && it.range.end.isAfter(time) }
                .fold(Duration.ZERO) { acc, e ->
                    val start = if (e.range.start.isAfter(time)) e.range.start else time
                    acc + Duration.between(start, e.range.end)
                }

            TodayUiState.Ready(
                dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", GERMAN)),
                weekType = weekType,
                isSchoolDay = todaysLessons.isNotEmpty(),
                entries = entries,
                current = current,
                next = next,
                freeTimeLeft = freeLeft,
                now = now,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)
}
