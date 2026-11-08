package app.lifedashboard.data.repository

import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.settings.AppSettings
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.today.DayTimelineBuilder
import app.lifedashboard.ui.today.TimelineEntry
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Alles, was an einem Tag passiert – aus allen Bereichen zusammengeführt. */
data class DayAgenda(
    val date: LocalDate,
    val weekType: WeekType,
    val lessons: List<LessonItem>,
    val routines: List<RoutineBlockEntity>,
    val events: List<EventEntity>,
    val shifts: List<JobShiftEntity>,
    val tasksDue: List<TaskEntity>,
    val settings: AppSettings,
) {
    val isWeekend: Boolean get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun timeline(): List<TimelineEntry> = DayTimelineBuilder.build(
        lessons = lessons,
        routines = routines,
        events = events,
        shifts = shifts,
        dayStart = settings.dayStartFor(date),
        dayEnd = settings.dayEnd,
        commuteMinutes = settings.schoolCommuteMinutes,
    )
}

data class AgendaRange(
    val days: List<DayAgenda>,
    val allTasks: List<TaskEntity>,
    val subjects: Map<Long, SubjectEntity>,
    val settings: AppSettings,
)

/**
 * Führt Stundenplan, Training, Termine, Schichten und Aufgaben zusammen.
 * Heute und Kalender lesen beide hier – eine Änderung erscheint daher überall gleichzeitig.
 */
@Singleton
class AgendaRepository @Inject constructor(
    private val timetable: TimetableRepository,
    private val routines: RoutineRepository,
    private val events: EventRepository,
    private val jobs: JobRepository,
    private val tasks: TaskRepository,
    private val settingsRepository: app.lifedashboard.data.settings.SettingsRepository,
) {
    fun observe(from: LocalDate, days: Int): Flow<AgendaRange> {
        val to = from.plusDays((days - 1).toLong())
        val base = combine(timetable.lessons, timetable.subjects, routines.routines, settingsRepository.settings) { l, s, r, st ->
            BaseData(l, s, r, st)
        }
        val dynamic = combine(events.between(from, to), jobs.between(from, to), tasks.tasks) { e, j, t ->
            Triple(e, j, t)
        }
        return combine(base, dynamic) { b, (eventList, shiftList, taskList) ->
            val calculator = b.settings.weekCalculator()
            val dayList = (0 until days).map { offset ->
                val date = from.plusDays(offset.toLong())
                val weekType = calculator.weekTypeFor(date)
                val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
                val epoch = date.toEpochDay()
                DayAgenda(
                    date = date,
                    weekType = weekType,
                    lessons = if (weekend) emptyList() else b.lessons.forDay(date.dayOfWeek, weekType),
                    routines = b.routines.forDay(date.dayOfWeek),
                    events = eventList.filter { it.dateEpochDay == epoch },
                    shifts = shiftList.filter { it.dateEpochDay == epoch },
                    tasksDue = taskList.filter { !it.done && it.dueEpochDay == epoch },
                    settings = b.settings,
                )
            }
            AgendaRange(dayList, taskList, b.subjects.associateBy { it.id }, b.settings)
        }
    }

    private data class BaseData(
        val lessons: List<LessonItem>,
        val subjects: List<SubjectEntity>,
        val routines: List<RoutineBlockEntity>,
        val settings: AppSettings,
    )
}
