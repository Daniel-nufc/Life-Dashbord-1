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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import app.lifedashboard.data.sync.ExternalEvent
import app.lifedashboard.data.sync.SystemCalendarRepository

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
    val externalEvents: List<ExternalEvent> = emptyList(),
) {
    val isWeekend: Boolean get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun timeline(planBlocks: List<app.lifedashboard.data.db.PlanBlockEntity> = emptyList()): List<TimelineEntry> = DayTimelineBuilder.build(
        lessons = lessons,
        routines = routines,
        events = events,
        shifts = shifts,
        externalEvents = externalEvents,
        planBlocks = planBlocks,
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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class AgendaRepository @Inject constructor(
    private val timetable: TimetableRepository,
    private val routines: RoutineRepository,
    private val events: EventRepository,
    private val jobs: JobRepository,
    private val tasks: TaskRepository,
    private val settingsRepository: app.lifedashboard.data.settings.SettingsRepository,
    private val systemCalendar: SystemCalendarRepository,
) {
    private fun externalEvents(from: LocalDate, to: LocalDate): Flow<List<ExternalEvent>> =
        combine(settingsRepository.settings.map { it.systemCalendarIds }.distinctUntilChanged(), systemCalendar.changes()) { ids, _ -> ids }
            .mapLatest { ids -> runCatching { systemCalendar.events(from, to, ids) }.getOrDefault(emptyList()) }

    fun observe(from: LocalDate, days: Int): Flow<AgendaRange> {
        val to = from.plusDays((days - 1).toLong())
        val base = combine(timetable.lessons, timetable.subjects, routines.routines, settingsRepository.settings) { l, s, r, st ->
            BaseData(l, s, r, st)
        }
        val dynamic = combine(events.between(from, to), jobs.between(from, to), tasks.tasks, externalEvents(from, to)) { e, j, t, x ->
            DynamicData(e, j, t, x)
        }
        return combine(base, dynamic) { b, d ->
            val eventList = d.events
            val shiftList = d.shifts
            val taskList = d.tasks
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
                    externalEvents = d.external.filter { it.date == date },
                )
            }
            AgendaRange(dayList, taskList, b.subjects.associateBy { it.id }, b.settings)
        }
    }

    private data class DynamicData(
        val events: List<EventEntity>,
        val shifts: List<JobShiftEntity>,
        val tasks: List<TaskEntity>,
        val external: List<ExternalEvent>,
    )

    private data class BaseData(
        val lessons: List<LessonItem>,
        val subjects: List<SubjectEntity>,
        val routines: List<RoutineBlockEntity>,
        val settings: AppSettings,
    )
}
