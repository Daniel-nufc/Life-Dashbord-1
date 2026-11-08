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
    data class Exam(val exam: app.lifedashboard.data.db.ExamEntity) : EditRequest
    data class Expense(val tx: app.lifedashboard.data.db.TransactionEntity) : EditRequest
}

/** Speichert alles, was in den Bearbeiten-Dialogen eingegeben wird. */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val jobs: JobRepository,
    private val routines: RoutineRepository,
    timetable: TimetableRepository,
    private val study: app.lifedashboard.data.repository.StudyRepository,
    private val finance: app.lifedashboard.data.repository.FinanceRepository,
    private val planDao: app.lifedashboard.data.db.PlanDao,
) : ViewModel() {

    val subjects: StateFlow<List<SubjectEntity>> =
        timetable.subjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun nextLessonDate(subjectId: Long, skip: Int): LocalDate? = tasks.nextLessonDate(subjectId, skip = skip)
    suspend fun estimateMinutes(subjectId: Long): Int = tasks.estimateHomeworkMinutes(subjectId, planDao)

    fun saveEvent(event: EventEntity) = launch { events.save(event) }
    fun deleteEvent(id: Long) = launch { events.delete(id) }

    fun saveTask(task: TaskEntity) = launch { tasks.save(task) }
    fun deleteTask(id: Long) = launch { tasks.delete(id) }
    fun setTaskDone(task: TaskEntity, done: Boolean) = launch { tasks.setDone(task, done) }

    fun saveShift(shift: JobShiftEntity) = launch { jobs.save(shift) }
    fun deleteShift(id: Long) = launch { jobs.delete(id) }

    fun saveRoutine(routine: RoutineBlockEntity) = launch { routines.save(routine) }
    fun saveExam(exam: app.lifedashboard.data.db.ExamEntity, topics: List<String>) = launch { study.saveExam(exam, topics) }
    fun deleteExam(id: Long) = launch { study.deleteExam(id) }
    fun saveExpense(tx: app.lifedashboard.data.db.TransactionEntity) = launch { finance.saveTransaction(tx, learn = false) }

    /**
     * Schnelleingabe → vorausgefüllter Dialog zum Bestätigen.
     * Gibt null zurück, wenn der Satz nicht verstanden wurde.
     */
    suspend fun parseQuick(text: String): EditRequest? {
        val subjectList = subjects.value
        val today = LocalDate.now()
        val intent = app.lifedashboard.planner.QuickParser.parse(
            text, today, subjectList.map { app.lifedashboard.planner.QuickSubject(it.code, it.name) },
        ) ?: return null
        val now = System.currentTimeMillis()
        fun subjectId(code: String?) = subjectList.firstOrNull { it.code.equals(code, true) || it.code.substringBefore('-').equals(code, true) }?.id
        return when (intent) {
            is app.lifedashboard.planner.QuickIntent.Event -> EditRequest.Event(
                EventEntity(
                    title = intent.title, dateEpochDay = intent.date.toEpochDay(),
                    startMinute = intent.start?.let { it.hour * 60 + it.minute }, endMinute = intent.end?.let { it.hour * 60 + it.minute },
                    area = app.lifedashboard.data.db.LifeArea.PRIVATE, location = null, note = null,
                ),
                intent.date,
            )
            is app.lifedashboard.planner.QuickIntent.Homework -> {
                val sid = subjectId(intent.subjectCode)
                val due = intent.due ?: sid?.let { tasks.nextLessonDate(it) }
                EditRequest.Homework(
                    TaskEntity(
                        title = intent.title, area = app.lifedashboard.data.db.LifeArea.SCHOOL, kind = app.lifedashboard.data.db.TaskKind.HOMEWORK,
                        subjectId = sid, dueEpochDay = due?.toEpochDay(), estimatedMinutes = 30, done = false, doneAtMillis = null, note = null, createdAtMillis = now,
                    ),
                )
            }
            is app.lifedashboard.planner.QuickIntent.Exam -> {
                val sid = subjectId(intent.subjectCode) ?: subjectList.firstOrNull()?.id ?: return null
                EditRequest.Exam(app.lifedashboard.data.db.ExamEntity(subjectId = sid, dateEpochDay = intent.date.toEpochDay(), title = intent.title, kind = app.lifedashboard.data.db.ExamKind.KLAUSUR, note = null))
            }
            is app.lifedashboard.planner.QuickIntent.Shift -> EditRequest.Shift(
                JobShiftEntity(
                    dateEpochDay = intent.date.toEpochDay(), startMinute = intent.start.hour * 60 + intent.start.minute,
                    endMinute = intent.end.hour * 60 + intent.end.minute, note = intent.note, hourlyWageCents = null,
                ),
                intent.date,
            )
            is app.lifedashboard.planner.QuickIntent.Todo -> EditRequest.Todo(
                TaskEntity(
                    title = intent.title,
                    area = if (intent.title.contains("etsy", true)) app.lifedashboard.data.db.LifeArea.ETSY else app.lifedashboard.data.db.LifeArea.PRIVATE,
                    kind = app.lifedashboard.data.db.TaskKind.TODO, subjectId = null, dueEpochDay = intent.due?.toEpochDay(),
                    estimatedMinutes = 30, done = false, doneAtMillis = null, note = null, createdAtMillis = now,
                ),
                intent.due,
            )
            is app.lifedashboard.planner.QuickIntent.Expense -> {
                val account = finance.accounts().let { list -> list.firstOrNull { it.kind == app.lifedashboard.data.db.AccountKind.CASH } ?: list.firstOrNull() } ?: return null
                EditRequest.Expense(
                    app.lifedashboard.data.db.TransactionEntity(
                        accountId = account.id, dateEpochDay = intent.date.toEpochDay(), amountCents = -intent.amountCents,
                        counterparty = intent.what, description = "", category = finance.suggestCategory(intent.what, ""),
                        importHash = null, recurringId = null,
                    ),
                )
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
