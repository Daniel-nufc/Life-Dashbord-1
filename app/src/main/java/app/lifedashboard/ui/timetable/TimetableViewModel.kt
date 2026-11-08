package app.lifedashboard.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.SchoolPeriodEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TimetableLessonEntity
import app.lifedashboard.data.repository.LessonEdit
import app.lifedashboard.data.repository.LessonItem
import app.lifedashboard.data.repository.TimetableRepository
import app.lifedashboard.data.repository.forDay
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.WeekType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PeriodSlot(val period: SchoolPeriodEntity, val lesson: LessonItem?)

data class DayColumn(val day: DayOfWeek, val slots: List<PeriodSlot>)

data class TimetableUiState(
    val loading: Boolean = true,
    val selectedWeek: WeekType = WeekType.A,
    val currentWeek: WeekType = WeekType.A,
    val days: List<DayColumn> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: TimetableRepository,
    settings: SettingsRepository,
    private val schoolCalendar: app.lifedashboard.data.repository.SchoolCalendarRepository,
) : ViewModel() {

    private val selectedWeek = MutableStateFlow<WeekType?>(null)

    private val settingsWithHolidays = combine(settings.settings, schoolCalendar.ranges) { s, r -> s to r }

    val state: StateFlow<TimetableUiState> = combine(
        repository.lessons,
        repository.periods,
        repository.subjects,
        settingsWithHolidays,
        selectedWeek,
    ) { lessons, periods, subjects, (s, free), selected ->
        // Am Wochenende zeigen wir direkt die kommende Woche.
        val planningWeek = s.weekCalculator(free).weekTypeForPlanning(LocalDate.now())
        val week = selected ?: planningWeek
        val days = (1..5).map { DayOfWeek.of(it) }.map { day ->
            val dayLessons = lessons.forDay(day, week)
            DayColumn(day, periods.map { p -> PeriodSlot(p, dayLessons.firstOrNull { it.lesson.period == p.number }) })
        }
        TimetableUiState(
            loading = false,
            selectedWeek = week,
            currentWeek = planningWeek,
            days = days,
            subjects = subjects,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    fun selectWeek(week: WeekType) {
        selectedWeek.value = week
    }

    fun save(edit: LessonEdit) {
        viewModelScope.launch { repository.saveLesson(edit) }
    }

    fun delete(lesson: TimetableLessonEntity, week: WeekType) {
        viewModelScope.launch { repository.deleteLessonForWeek(lesson, week) }
    }
}
