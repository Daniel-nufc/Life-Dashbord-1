package app.lifedashboard.data.repository

import app.lifedashboard.data.db.EventDao
import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftDao
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.TaskDao
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.WeekPattern
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.WeekType
import app.lifedashboard.planner.WeeklySlot
import app.lifedashboard.planner.nextOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class EventRepository @Inject constructor(private val dao: EventDao) {
    fun between(from: LocalDate, to: LocalDate): Flow<List<EventEntity>> =
        dao.observeBetween(from.toEpochDay(), to.toEpochDay())

    suspend fun save(event: EventEntity) = dao.upsert(event)
    suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class JobRepository @Inject constructor(private val dao: JobShiftDao) {
    fun between(from: LocalDate, to: LocalDate): Flow<List<JobShiftEntity>> =
        dao.observeBetween(from.toEpochDay(), to.toEpochDay())

    suspend fun save(shift: JobShiftEntity) = dao.upsert(shift)
    suspend fun delete(id: Long) = dao.delete(id)
}

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val timetable: TimetableRepository,
    private val settings: SettingsRepository,
    private val schoolCalendar: SchoolCalendarRepository,
) {
    val tasks: Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun save(task: TaskEntity) = dao.upsert(task)

    suspend fun setDone(task: TaskEntity, done: Boolean) =
        dao.setDone(task.id, done, if (done) System.currentTimeMillis() else null)

    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Wann hat das Fach das nächste Mal Unterricht? Berücksichtigt A/B-Wochen.
     * @param skip 0 = nächste Stunde, 1 = übernächste
     */
    suspend fun nextLessonDate(subjectId: Long, after: LocalDate = LocalDate.now(), skip: Int = 0): LocalDate? {
        val lessons = timetable.lessons.first().filter { it.subject.id == subjectId }
        val slots = lessons.map {
            WeeklySlot(
                day = DayOfWeek.of(it.lesson.dayOfWeek),
                weekType = when (it.lesson.weekPattern) {
                    WeekPattern.EVERY -> null
                    WeekPattern.A -> WeekType.A
                    WeekPattern.B -> WeekType.B
                },
            )
        }.distinct()
        val free = schoolCalendar.currentRanges()
        val calculator = settings.settings.first().weekCalculator(free)
        return nextOccurrence(after, slots, calculator::weekTypeFor, skip, isSchoolDay = { !app.lifedashboard.planner.isSchoolFree(it, free) })
    }
}
