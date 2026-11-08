package app.lifedashboard.data.repository

import app.lifedashboard.data.db.HolidayKind
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SchoolHolidayEntity
import app.lifedashboard.data.db.SchoolPeriodEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TimetableLessonEntity
import app.lifedashboard.data.db.WeekPattern
import app.lifedashboard.data.settings.ALL_TODAY_CARDS
import app.lifedashboard.data.settings.AppSettings
import app.lifedashboard.planner.BerlinSchoolCalendar
import app.lifedashboard.planner.EtsyFees
import app.lifedashboard.planner.WeekType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kalender über ein ganzes Schuljahr: jede Woche korrekt gefüllt, Ferien leer, A/B richtig. */
class AgendaBuilderTest {
    private val settings = AppSettings(
        appLockEnabled = false, lockTimeoutMinutes = 1, abReferenceMonday = LocalDate.of(2026, 11, 9), abReferenceType = WeekType.A,
        dayStart = LocalTime.of(6, 0), dayEnd = LocalTime.of(22, 0), schoolCommuteMinutes = 25, userName = "Daniel",
        weekendDayStart = LocalTime.of(9, 30), seedVersion = 99, systemCalendarIds = emptySet(), priorityOrder = listOf("SCHOOL", "SPORT", "ETSY"),
        bufferCents = 0, jobWageCents = 0, etsyFees = EtsyFees(), todayCards = ALL_TODAY_CARDS, hiddenTodayCards = emptySet(),
    )
    private val mathe = SubjectEntity(id = 1, code = "MA-B", name = "Mathematik", colorArgb = 0)
    private val deutsch = SubjectEntity(id = 2, code = "DE-B", name = "Deutsch", colorArgb = 0)
    private val p1 = SchoolPeriodEntity(1, 480, 570)
    private fun lesson(id: Long, s: SubjectEntity, day: Int, pattern: WeekPattern) =
        LessonItem(TimetableLessonEntity(id, s.id, day, 1, pattern, "F309"), s, p1)
    // Montag Block 1: Woche A Deutsch, Woche B Mathe; Freitag immer Mathe
    private val lessons = listOf(
        lesson(1, deutsch, 1, WeekPattern.A), lesson(2, mathe, 1, WeekPattern.B), lesson(3, mathe, 5, WeekPattern.EVERY),
    )
    private val floorball = RoutineBlockEntity(1, "Floorball", LifeArea.SPORT, "1,3", 1080, 1215)
    private val holidays = BerlinSchoolCalendar.entries.mapIndexed { i, e ->
        SchoolHolidayEntity(i.toLong(), e.name, e.range.start.toEpochDay(), e.range.endInclusive.toEpochDay(), if (e.publicHoliday) HolidayKind.PUBLIC_HOLIDAY else HolidayKind.SCHOOL_HOLIDAY)
    }

    @Test
    fun ganzesSchuljahrIstGefuellt() {
        val from = LocalDate.of(2026, 11, 9)
        val days = AgendaBuilder.buildDays(from, 52 * 7, lessons, listOf(floorball), settings, holidays)
        assertEquals(364, days.size)
        days.forEach { d ->
            val free = d.isSchoolFree
            if (!free && d.date.dayOfWeek == DayOfWeek.MONDAY) {
                val expected = if (d.weekType == WeekType.A) "DE-B" else "MA-B"
                assertEquals("Montag ${d.date}", expected, d.lessons.single().subject.code)
            }
            if (!free && d.date.dayOfWeek == DayOfWeek.FRIDAY) assertEquals("Freitag ${d.date}", 1, d.lessons.size)
            if (free) assertTrue("kein Unterricht am ${d.date} (${d.holiday})", d.lessons.isEmpty())
            // Training ist eine Regel und gilt immer, auch nach Monaten
            if (d.date.dayOfWeek == DayOfWeek.MONDAY || d.date.dayOfWeek == DayOfWeek.WEDNESDAY) assertEquals(1, d.routines.size)
        }
        // Weit in der Zukunft (Juni 2027) gibt es weiterhin Unterricht
        assertTrue(days.first { it.date == LocalDate.of(2027, 6, 11) }.lessons.isNotEmpty())
    }

    @Test
    fun jahreswechselUndFerienwochen() {
        val days = AgendaBuilder.buildDays(LocalDate.of(2026, 12, 21), 21, lessons, emptyList(), settings, holidays)
        val byDate = days.associateBy { it.date }
        assertTrue(byDate.getValue(LocalDate.of(2026, 12, 21)).lessons.isNotEmpty()) // Mo vor den Ferien
        assertEquals("Weihnachtsferien 2026/27", byDate.getValue(LocalDate.of(2026, 12, 28)).holiday)
        assertTrue(byDate.getValue(LocalDate.of(2027, 1, 1)).lessons.isEmpty())
        // Nach den Ferien: 21.12. war A, Ferienwoche übersprungen → 4.1. ist B → Montag Mathe
        assertEquals(WeekType.B, byDate.getValue(LocalDate.of(2027, 1, 4)).weekType)
        assertEquals("MA-B", byDate.getValue(LocalDate.of(2027, 1, 4)).lessons.single().subject.code)
    }

    @Test
    fun feiertagUnterDerWocheUndZeitumstellung() {
        // Mo 8.3.2027 Frauentag: kein Unterricht, Tag startet wie am Wochenende
        val frauentag = AgendaBuilder.buildDays(LocalDate.of(2027, 3, 8), 1, lessons, emptyList(), settings, holidays).single()
        assertTrue(frauentag.lessons.isEmpty())
        assertEquals("Internationaler Frauentag", frauentag.holiday)
        assertEquals(LocalTime.of(9, 30), settings.dayStartFor(frauentag.date, true))
        // Woche der Sommerzeit-Umstellung (28.3.2027) und Winterzeit (25.10.2026): 7 Tage, nichts verschoben
        listOf(LocalDate.of(2027, 3, 22), LocalDate.of(2026, 10, 19), LocalDate.of(2027, 4, 5)).forEach { monday ->
            val week = AgendaBuilder.buildDays(monday, 7, lessons, listOf(floorball), settings, holidays)
            assertEquals(7, week.size)
            assertEquals((0L..6L).map { monday.plusDays(it) }, week.map { it.date })
        }
    }
}
