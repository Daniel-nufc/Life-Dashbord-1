package app.lifedashboard.data.repository

import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SchoolHolidayEntity
import app.lifedashboard.data.db.StudyDao
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.settings.AppSettings
import app.lifedashboard.data.sync.ExternalEvent
import app.lifedashboard.data.sync.SystemCalendarRepository
import app.lifedashboard.planner.DateRange
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
    /** Name der Ferien / des Feiertags, falls schulfrei */
    val holiday: String? = null,
    val exams: List<ExamEntity> = emptyList(),
) {
    val isWeekend: Boolean get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val isSchoolFree: Boolean get() = isWeekend || holiday != null

    fun timeline(planBlocks: List<PlanBlockEntity> = emptyList()): List<TimelineEntry> = DayTimelineBuilder.build(
        lessons = lessons,
        routines = routines,
        events = events,
        shifts = shifts,
        externalEvents = externalEvents,
        planBlocks = planBlocks,
        dayStart = settings.dayStartFor(date, holiday != null),
        dayEnd = settings.dayEnd,
        commuteMinutes = settings.schoolCommuteMinutes,
        breakAfterSchoolMinutes = AFTER_SCHOOL_BREAK_MINUTES,
    )

    /**
     * Alles, was an diesem Tag keine freie Zeit ist: feste Einträge plus Hofpausen
     * (Lücken unter 1 Std zwischen zwei Stunden werden nie verplant, Freistunden schon).
     */
    fun busyRanges(planBlocks: List<PlanBlockEntity> = emptyList()): List<app.lifedashboard.planner.TimeRange> =
        timeline(planBlocks).filter { it.kind != app.lifedashboard.ui.today.EntryKind.FREE }.map { it.range } +
            app.lifedashboard.planner.schoolBreaks(lessons.mapNotNull { it.timeRange })

    /**
     * Bis wann eine heute fällige Hausaufgabe spätestens fertig sein muss: Beginn der Stunde des Fachs –
     * ist es die erste Stunde des Tages, schon vor dem Schulweg. Freistunden davor zählen mit.
     */
    fun homeworkCutoff(subjectId: Long?): java.time.LocalTime? {
        val lesson = firstLessonStart(subjectId) ?: return null
        val schoolStart = lessons.mapNotNull { it.timeRange?.start }.minOrNull()
        return if (lesson == schoolStart) lesson.minusMinutes(settings.schoolCommuteMinutes.toLong()) else lesson
    }

    /** Beginn der ersten Stunde eines Fachs an diesem Tag (für Hausaufgaben, die heute fällig sind) */
    fun firstLessonStart(subjectId: Long?): java.time.LocalTime? =
        lessons.filter { subjectId == null || it.subject.id == subjectId }.mapNotNull { it.timeRange?.start }.minOrNull()
            ?: lessons.mapNotNull { it.timeRange?.start }.minOrNull()

    /** Schul-Pensum (Lernen + Hausaufgaben) für diesen Tag: werktags 2 Std, mit Training/Job 1 Std, Samstag 2 Std, Sonntag frei. */
    fun schoolCapMinutes(examSoon: Boolean = false): Int = when {
        date.dayOfWeek == DayOfWeek.SUNDAY -> if (examSoon) 60 else 0
        date.dayOfWeek == DayOfWeek.SATURDAY -> 120
        holiday != null -> 150
        routines.any { it.area == app.lifedashboard.data.db.LifeArea.SPORT } || shifts.isNotEmpty() -> 60
        else -> 120
    }

    companion object {
        const val AFTER_SCHOOL_BREAK_MINUTES = 60
    }
}

data class AgendaRange(
    val days: List<DayAgenda>,
    val allTasks: List<TaskEntity>,
    val subjects: Map<Long, SubjectEntity>,
    val settings: AppSettings,
)

/**
 * Baut beliebige Zeiträume aus Regeln: Stundenplan (A/B), feste Trainings (Wochentage),
 * Ferien und Feiertage – nichts wird für einen festen Zeitraum vorab erzeugt.
 * Reine Funktion → mit Tests über ein ganzes Schuljahr geprüft.
 */
object AgendaBuilder {
    fun buildDays(
        from: LocalDate,
        days: Int,
        lessons: List<LessonItem>,
        routines: List<RoutineBlockEntity>,
        settings: AppSettings,
        holidays: List<SchoolHolidayEntity>,
        events: List<EventEntity> = emptyList(),
        shifts: List<JobShiftEntity> = emptyList(),
        tasks: List<TaskEntity> = emptyList(),
        external: List<ExternalEvent> = emptyList(),
        exams: List<ExamEntity> = emptyList(),
    ): List<DayAgenda> {
        val ranges: List<Pair<SchoolHolidayEntity, DateRange>> = holidays.map { it to it.range() }
        val calculator = settings.weekCalculator(ranges.map { it.second })
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            val epoch = date.toEpochDay()
            val weekType = calculator.weekTypeFor(date)
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val holiday = ranges.firstOrNull { date in it.second }?.first?.name
            DayAgenda(
                date = date,
                weekType = weekType,
                lessons = if (weekend || holiday != null) emptyList() else lessons.forDay(date.dayOfWeek, weekType),
                routines = routines.forDay(date.dayOfWeek),
                events = events.filter { it.dateEpochDay == epoch },
                shifts = shifts.filter { it.dateEpochDay == epoch },
                tasksDue = tasks.filter { !it.done && it.dueEpochDay == epoch },
                settings = settings,
                externalEvents = external.filter { it.date == date },
                holiday = holiday,
                exams = exams.filter { it.dateEpochDay == epoch },
            )
        }
    }
}

/**
 * Führt Stundenplan, Training, Termine, Schichten, Klausuren und Aufgaben zusammen.
 * Heute, Kalender, Planung und Widgets lesen alle hier – eine Änderung erscheint überall.
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
    private val schoolCalendar: SchoolCalendarRepository,
    private val studyDao: StudyDao,
) {
    private fun externalEvents(from: LocalDate, to: LocalDate): Flow<List<ExternalEvent>> =
        combine(
            settingsRepository.settings.map { listOf(it.systemCalendarIds, it.onlyMineCalendarIds, it.userName, it.mineTitles, it.notMineTitles) }
                .distinctUntilChanged(),
            systemCalendar.changes(),
        ) { config, _ -> config }
            .mapLatest { c ->
                @Suppress("UNCHECKED_CAST")
                runCatching {
                    systemCalendar.events(from, to, c[0] as Set<Long>, c[1] as Set<Long>, c[2] as String, c[3] as Set<String>, c[4] as Set<String>)
                }.getOrDefault(emptyList())
            }

    fun observe(from: LocalDate, days: Int): Flow<AgendaRange> {
        val to = from.plusDays((days - 1).toLong())
        val base = combine(timetable.lessons, timetable.subjects, routines.routines, settingsRepository.settings, schoolCalendar.holidays) { l, s, r, st, h ->
            BaseData(l, s, r, st, h)
        }
        val dynamic = combine(
            events.between(from, to),
            jobs.between(from, to),
            tasks.tasks,
            externalEvents(from, to),
            studyDao.observeExamsBetween(from.toEpochDay(), to.toEpochDay()),
        ) { e, j, t, x, ex -> DynamicData(e, j, t, x, ex) }
        return combine(base, dynamic) { b, d ->
            val dayList = AgendaBuilder.buildDays(
                from = from, days = days, lessons = b.lessons, routines = b.routines, settings = b.settings,
                holidays = b.holidays, events = d.events, shifts = d.shifts, tasks = d.tasks, external = d.external, exams = d.exams,
            )
            AgendaRange(dayList, d.tasks, b.subjects.associateBy { it.id }, b.settings)
        }
    }

    private data class DynamicData(
        val events: List<EventEntity>,
        val shifts: List<JobShiftEntity>,
        val tasks: List<TaskEntity>,
        val external: List<ExternalEvent>,
        val exams: List<ExamEntity>,
    )

    private data class BaseData(
        val lessons: List<LessonItem>,
        val subjects: List<SubjectEntity>,
        val routines: List<RoutineBlockEntity>,
        val settings: AppSettings,
        val holidays: List<SchoolHolidayEntity>,
    )
}
