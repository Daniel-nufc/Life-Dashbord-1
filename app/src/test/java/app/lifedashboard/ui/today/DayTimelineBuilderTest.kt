package app.lifedashboard.ui.today

import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SchoolPeriodEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TimetableLessonEntity
import app.lifedashboard.data.db.WeekPattern
import app.lifedashboard.data.repository.LessonItem
import app.lifedashboard.planner.TimeRange
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DayTimelineBuilderTest {

    private val mathe = SubjectEntity(id = 1, code = "MA-B", name = "Mathematik", colorArgb = 0xFF2E8BF0)

    private fun lesson(period: Int, start: Int, end: Int) = LessonItem(
        lesson = TimetableLessonEntity(id = period.toLong(), subjectId = 1, dayOfWeek = 1, period = period, weekPattern = WeekPattern.EVERY, room = "F309"),
        subject = mathe,
        period = SchoolPeriodEntity(period, start, end),
    )

    private val floorball = RoutineBlockEntity(
        id = 1, title = "Floorball", area = LifeArea.SPORT, daysOfWeek = "1,3",
        startMinute = 18 * 60, endMinute = 20 * 60 + 15,
    )

    @Test
    fun montagMitSchuleUndFloorball() {
        val entries = DayTimelineBuilder.build(
            lessons = listOf(lesson(1, 480, 570), lesson(4, 810, 900)),
            routines = listOf(floorball),
            dayStart = LocalTime.of(6, 0),
            dayEnd = LocalTime.of(22, 0),
            commuteMinutes = 45,
        )
        val free = entries.filter { it.kind == EntryKind.FREE }.map { it.range }
        assertEquals(
            listOf(
                TimeRange.of(6, 0, 7, 15),     // vor dem Schulweg
                TimeRange.of(9, 30, 13, 30),   // Lücke zwischen Block 1 und 4
                TimeRange.of(15, 45, 18, 0),   // nach dem Heimweg bis Floorball
                TimeRange.of(20, 15, 22, 0),
            ),
            free,
        )
        assertEquals(EntryKind.COMMUTE, entries[1].kind)
        assertEquals("Schulweg", entries[1].title)
    }

    @Test
    fun hofpausenSindNichtFrei() {
        // 8:00–9:30, 9:50–11:20, 11:40–13:10 – zwei 20-Minuten-Pausen
        val entries = DayTimelineBuilder.build(
            lessons = listOf(lesson(1, 480, 570), lesson(2, 590, 680), lesson(3, 700, 790)),
            routines = emptyList(),
            dayStart = LocalTime.of(6, 0),
            dayEnd = LocalTime.of(22, 0),
            commuteMinutes = 30,
            minFreeMinutes = 15,
        )
        val free = entries.filter { it.kind == EntryKind.FREE }.map { it.range }
        assertEquals(listOf(TimeRange.of(6, 0, 7, 30), TimeRange.of(13, 40, 22, 0)), free)
    }

    @Test
    fun termineUndJobBlockierenZeitGanztaegigeNicht() {
        val entries = DayTimelineBuilder.build(
            lessons = emptyList(),
            routines = emptyList(),
            dayStart = LocalTime.of(9, 30),
            dayEnd = LocalTime.of(22, 0),
            commuteMinutes = 25,
            events = listOf(
                EventEntity(id = 1, title = "Zahnarzt", dateEpochDay = 0, startMinute = 17 * 60, endMinute = 18 * 60, area = LifeArea.PRIVATE, location = null, note = null),
                EventEntity(id = 2, title = "Geburtstag Oma", dateEpochDay = 0, startMinute = null, endMinute = null, area = LifeArea.PRIVATE, location = null, note = null),
            ),
            shifts = listOf(JobShiftEntity(id = 1, dateEpochDay = 0, startMinute = 10 * 60, endMinute = 14 * 60, note = "Baustelle", hourlyWageCents = null)),
        )
        val free = entries.filter { it.kind == EntryKind.FREE }.map { it.range }
        assertEquals(
            listOf(TimeRange.of(9, 30, 10, 0), TimeRange.of(14, 0, 17, 0), TimeRange.of(18, 0, 22, 0)),
            free,
        )
        assertEquals(1, entries.count { it.kind == EntryKind.EVENT })
        assertEquals(1, entries.count { it.kind == EntryKind.JOB })
    }

    @Test
    fun wochenendeOhneTermineIstKomplettFrei() {
        val entries = DayTimelineBuilder.build(emptyList(), emptyList(), LocalTime.of(6, 0), LocalTime.of(22, 0), 45)
        assertEquals(1, entries.size)
        assertEquals(EntryKind.FREE, entries.single().kind)
    }
}
