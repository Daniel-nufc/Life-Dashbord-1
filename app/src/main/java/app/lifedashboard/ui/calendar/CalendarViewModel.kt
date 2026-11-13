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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import app.lifedashboard.data.db.PlanBlockEntity
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Am Wochenende zeigt der Kalender schon die kommende Woche. */
fun planningMonday(today: LocalDate = LocalDate.now()): LocalDate =
    if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
        today.mondayOfWeek().plusWeeks(1)
    } else {
        today.mondayOfWeek()
    }

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    agenda: AgendaRepository,
    private val tasks: TaskRepository,
    private val engine: app.lifedashboard.data.planning.PlanningEngine,
    private val training: app.lifedashboard.data.repository.TrainingRepository,
) : ViewModel() {

    private val _weekStart = MutableStateFlow(planningMonday())
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    val week: StateFlow<AgendaRange?> = _weekStart
        .flatMapLatest { agenda.observe(it, 7) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Vorschläge und angenommene Blöcke der angezeigten Woche */
    val plan: StateFlow<List<PlanBlockEntity>> = _weekStart
        .flatMapLatest { engine.observeRange(it, it.plusDays(6)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Diese und nächste Woche automatisch vorplanen; neu bei Änderungen (Termine, Aufgaben, Klausuren …)
        viewModelScope.launch {
            _weekStart.flatMapLatest { start -> agenda.observe(start, 7).map { start } }
                .debounce(1_500)
                .collect { start ->
                    val today = LocalDate.now()
                    val horizonEnd = planningMonday(today).plusDays(13)
                    if (!start.isAfter(horizonEnd) && !start.plusDays(6).isBefore(today)) {
                        runCatching { engine.planDays(maxOf(start, today), 7) }
                    }
                }
        }
    }

    /** Trainingsplan der angezeigten Woche – für jede Woche berechnet, auch weit in der Zukunft */
    val trainingWeek: StateFlow<List<app.lifedashboard.data.repository.PlannedTraining>> = _weekStart
        .flatMapLatest { start -> agenda.observe(start, 7).map { runCatching { training.week(start) }.getOrDefault(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun accept(block: PlanBlockEntity) { viewModelScope.launch { engine.accept(block) } }
    fun decline(block: PlanBlockEntity) { viewModelScope.launch { engine.decline(block) } }
    fun done(block: PlanBlockEntity) { viewModelScope.launch { engine.done(block) } }
    fun later(block: PlanBlockEntity) { viewModelScope.launch { engine.later(block) } }
    fun acceptWeek() { viewModelScope.launch { engine.acceptAll(_weekStart.value, _weekStart.value.plusDays(6)) } }
    fun replanWeek() {
        viewModelScope.launch { engine.planDays(maxOf(_weekStart.value, LocalDate.now()), 7) }
    }

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
