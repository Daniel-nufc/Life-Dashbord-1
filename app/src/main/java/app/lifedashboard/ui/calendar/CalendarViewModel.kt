package app.lifedashboard.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.repository.AgendaRange
import app.lifedashboard.data.repository.AgendaRepository
import app.lifedashboard.data.repository.TaskRepository
import app.lifedashboard.planner.mondayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Am Wochenende zeigt der Kalender schon die kommende Woche. */
fun planningMonday(today: LocalDate = LocalDate.now()): LocalDate =
    if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
        today.mondayOfWeek().plusWeeks(1)
    } else {
        today.mondayOfWeek()
    }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    agenda: AgendaRepository,
    private val tasks: TaskRepository,
) : ViewModel() {

    private val _weekStart = MutableStateFlow(planningMonday())
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    val week: StateFlow<AgendaRange?> = _weekStart
        .flatMapLatest { agenda.observe(it, 7) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousWeek() { _weekStart.value = _weekStart.value.minusWeeks(1) }
    fun nextWeek() { _weekStart.value = _weekStart.value.plusWeeks(1) }
    fun thisWeek() { _weekStart.value = planningMonday() }

    fun toggleTask(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task, done) }
    }

    companion object {
        fun weekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }
}
