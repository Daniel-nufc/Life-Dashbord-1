package app.lifedashboard.ui.editors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.repository.EventRepository
import app.lifedashboard.data.repository.JobRepository
import app.lifedashboard.data.repository.RoutineRepository
import app.lifedashboard.data.repository.TaskRepository
import app.lifedashboard.data.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Was gerade bearbeitet oder neu angelegt wird. */
sealed interface EditRequest {
    data class Event(val event: EventEntity?, val date: LocalDate) : EditRequest
    data class Homework(val task: TaskEntity?) : EditRequest
    data class Todo(val task: TaskEntity?, val date: LocalDate?) : EditRequest
    data class Shift(val shift: JobShiftEntity?, val date: LocalDate) : EditRequest
    data class Routine(val routine: RoutineBlockEntity?) : EditRequest
}

/** Speichert alles, was in den Bearbeiten-Dialogen eingegeben wird. */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val jobs: JobRepository,
    private val routines: RoutineRepository,
    timetable: TimetableRepository,
) : ViewModel() {

    val subjects: StateFlow<List<SubjectEntity>> =
        timetable.subjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun nextLessonDate(subjectId: Long, skip: Int): LocalDate? = tasks.nextLessonDate(subjectId, skip = skip)

    fun saveEvent(event: EventEntity) = launch { events.save(event) }
    fun deleteEvent(id: Long) = launch { events.delete(id) }

    fun saveTask(task: TaskEntity) = launch { tasks.save(task) }
    fun deleteTask(id: Long) = launch { tasks.delete(id) }
    fun setTaskDone(task: TaskEntity, done: Boolean) = launch { tasks.setDone(task, done) }

    fun saveShift(shift: JobShiftEntity) = launch { jobs.save(shift) }
    fun deleteShift(id: Long) = launch { jobs.delete(id) }

    fun saveRoutine(routine: RoutineBlockEntity) = launch { routines.save(routine) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
