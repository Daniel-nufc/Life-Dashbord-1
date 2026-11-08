package app.lifedashboard.data.repository

import app.lifedashboard.data.db.SchoolPeriodEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TimetableDao
import app.lifedashboard.data.db.TimetableLessonEntity
import app.lifedashboard.data.db.WeekPattern
import app.lifedashboard.planner.TimeRange
import app.lifedashboard.planner.WeekType
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Eine Stunde inklusive Fach und Uhrzeit – fertig für die Anzeige. */
data class LessonItem(
    val lesson: TimetableLessonEntity,
    val subject: SubjectEntity,
    val period: SchoolPeriodEntity?,
) {
    val timeRange: TimeRange?
        get() = period?.let { TimeRange.ofMinutes(it.startMinute, it.endMinute) }
}

fun WeekPattern.appliesTo(week: WeekType): Boolean = when (this) {
    WeekPattern.EVERY -> true
    WeekPattern.A -> week == WeekType.A
    WeekPattern.B -> week == WeekType.B
}

fun List<LessonItem>.forDay(day: DayOfWeek, week: WeekType): List<LessonItem> =
    filter { it.lesson.dayOfWeek == day.value && it.lesson.weekPattern.appliesTo(week) }
        .sortedBy { it.lesson.period }

/** Daten für einen geänderten Stundenplan-Eintrag aus dem Bearbeiten-Dialog. */
data class LessonEdit(
    val dayOfWeek: Int,
    val period: Int,
    val original: TimetableLessonEntity?,
    val subjectId: Long,
    val room: String,
    val pattern: WeekPattern,
)

@Singleton
class TimetableRepository @Inject constructor(
    private val dao: TimetableDao,
) {
    val subjects: Flow<List<SubjectEntity>> = dao.observeSubjects()
    val periods: Flow<List<SchoolPeriodEntity>> = dao.observePeriods()

    val lessons: Flow<List<LessonItem>> =
        combine(dao.observeLessons(), subjects, periods) { lessons, subjects, periods ->
            val subjectById = subjects.associateBy { it.id }
            val periodByNumber = periods.associateBy { it.number }
            lessons.mapNotNull { lesson ->
                subjectById[lesson.subjectId]?.let { LessonItem(lesson, it, periodByNumber[lesson.period]) }
            }
        }

    /**
     * Speichert eine Stunde. Sonderfälle:
     * - War die Stunde „jede Woche“ und gilt jetzt nur für A, bleibt sie für B unverändert erhalten.
     * - Gilt sie jetzt „jede Woche“, werden andere Stunden im selben Block entfernt (keine Doppelungen).
     */
    suspend fun saveLesson(edit: LessonEdit) {
        val original = edit.original
        val updated = (original ?: TimetableLessonEntity(
            subjectId = edit.subjectId,
            dayOfWeek = edit.dayOfWeek,
            period = edit.period,
            weekPattern = edit.pattern,
            room = edit.room,
        )).copy(subjectId = edit.subjectId, room = edit.room.trim(), weekPattern = edit.pattern)

        if (edit.pattern == WeekPattern.EVERY) {
            dao.lessonsAt(edit.dayOfWeek, edit.period)
                .filter { it.id != original?.id }
                .forEach { dao.deleteLesson(it.id) }
        } else if (original != null && original.weekPattern == WeekPattern.EVERY) {
            val otherWeek = if (edit.pattern == WeekPattern.A) WeekPattern.B else WeekPattern.A
            dao.insertLessons(listOf(original.copy(id = 0, weekPattern = otherWeek)))
        }
        dao.upsertLesson(updated)
    }

    suspend fun saveSubject(subject: SubjectEntity) = dao.upsertSubject(subject)

    /** Löscht eine Stunde nur für die gewählte Woche – die andere Woche bleibt erhalten. */
    suspend fun deleteLessonForWeek(lesson: TimetableLessonEntity, week: WeekType) {
        if (lesson.weekPattern == WeekPattern.EVERY) {
            val remaining = if (week == WeekType.A) WeekPattern.B else WeekPattern.A
            dao.upsertLesson(lesson.copy(weekPattern = remaining))
        } else {
            dao.deleteLesson(lesson.id)
        }
    }
}
