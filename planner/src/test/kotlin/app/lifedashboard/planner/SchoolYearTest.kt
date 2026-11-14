package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ein ganzes Schuljahr: A/B-Wochen, Jahreswechsel, Ferien, Feiertage. */
class SchoolYearTest {
    private val free = BerlinSchoolCalendar.entries.map { it.range }
    private val calc = SchoolWeekCalculator(LocalDate.of(2026, 11, 9), WeekType.A, free, skipHolidayWeeks = true)

    @Test
    fun `52 Wochen am Stueck wechseln A und B in jeder Schulwoche`() {
        var monday = LocalDate.of(2026, 11, 9)
        var expected = WeekType.A
        repeat(52) {
            if (!calc.isHolidayWeek(monday)) {
                assertEquals(expected, calc.weekTypeFor(monday), "Woche ab $monday")
                // Jeder Tag der Woche hat denselben Typ
                (0L..6L).forEach { assertEquals(expected, calc.weekTypeFor(monday.plusDays(it)), "Tag ${monday.plusDays(it)}") }
                expected = expected.other()
            }
            monday = monday.plusWeeks(1)
        }
    }

    @Test
    fun `Jahreswechsel mit KW 53 und Weihnachtsferien`() {
        // 2026 hat 53 ISO-Wochen – über die KW-Parität wäre das falsch.
        // 9.11. A, 16. B, 23. A, 30. B, 7.12. A, 14.12. B, 21.12. A (Mo/Di Schule),
        // 28.12. = reine Ferienwoche (übersprungen), 4.1.2027 = B
        assertEquals(WeekType.A, calc.weekTypeFor(LocalDate.of(2026, 12, 21)))
        assertTrue(calc.isHolidayWeek(LocalDate.of(2026, 12, 28)))
        assertEquals(WeekType.B, calc.weekTypeFor(LocalDate.of(2027, 1, 4)))
        assertEquals(WeekType.A, calc.weekTypeFor(LocalDate.of(2027, 1, 11)))
    }

    @Test
    fun `Winter- und Osterferien werden uebersprungen`() {
        val beforeWinter = calc.weekTypeFor(LocalDate.of(2027, 1, 25))
        assertTrue(calc.isHolidayWeek(LocalDate.of(2027, 2, 1)))
        assertEquals(beforeWinter.other(), calc.weekTypeFor(LocalDate.of(2027, 2, 8)))
        val beforeEaster = calc.weekTypeFor(LocalDate.of(2027, 3, 15))
        assertTrue(calc.isHolidayWeek(LocalDate.of(2027, 3, 22)))
        assertTrue(calc.isHolidayWeek(LocalDate.of(2027, 3, 29)))
        assertEquals(beforeEaster.other(), calc.weekTypeFor(LocalDate.of(2027, 4, 5)))
    }

    @Test
    fun `Zeitumstellung aendert nichts an Wochen und Tagen`() {
        // Winterzeit 25.10.2026 (Ferien), Sommerzeit 28.3.2027 (Ferien) – Datum-Logik ist zeitzonenfrei
        assertEquals(DayOfWeek.MONDAY, LocalDate.of(2027, 3, 29).dayOfWeek)
        assertEquals(calc.weekTypeFor(LocalDate.of(2027, 4, 5)), calc.weekTypeFor(LocalDate.of(2027, 4, 9)))
    }

    @Test
    fun `Feiertage und Ferien sind schulfrei, normale Tage nicht`() {
        assertTrue(isSchoolFree(LocalDate.of(2027, 3, 8), free)) // Frauentag
        assertTrue(isSchoolFree(LocalDate.of(2027, 5, 6), free)) // Himmelfahrt
        assertTrue(isSchoolFree(LocalDate.of(2026, 12, 23), free))
        assertTrue(isSchoolFree(LocalDate.of(2026, 11, 14), free)) // Samstag
        assertFalse(isSchoolFree(LocalDate.of(2026, 11, 10), free))
    }

    @Test
    fun `naechste Stunde ueberspringt Ferien`() {
        // Mathe freitags: Hausaufgabe am Di 22.12. → nächste Mathestunde erst Fr 8.1.2027
        val slots = listOf(WeeklySlot(DayOfWeek.FRIDAY))
        val due = nextOccurrence(LocalDate.of(2026, 12, 22), slots, calc::weekTypeFor, isSchoolDay = { !isSchoolFree(it, free) })
        assertEquals(LocalDate.of(2027, 1, 8), due)
    }

    @Test
    fun `Schuljahr 2027-28 ist hinterlegt - Ferien ueberlappen nicht und liegen in der richtigen Reihenfolge`() {
        val ranges = BerlinSchoolCalendar.entries.filter { !it.publicHoliday }.map { it.range }.sortedBy { it.start }
        ranges.zipWithNext().forEach { (a, b) -> kotlin.test.assertTrue(a.endInclusive.isBefore(b.start), "$a / $b") }
        kotlin.test.assertTrue(BerlinSchoolCalendar.coveredUntil(ranges)!!.isAfter(java.time.LocalDate.of(2028, 8, 1)))
        val free = BerlinSchoolCalendar.entries.map { it.range }
        kotlin.test.assertTrue(isSchoolFree(java.time.LocalDate.of(2028, 4, 12), free)) // Osterferien 2028
        kotlin.test.assertTrue(!isSchoolFree(java.time.LocalDate.of(2028, 3, 15), free)) // normaler Mittwoch
    }

    @Test
    fun `Countdown bis zu den Weihnachtsferien zaehlt nur Schultage`() {
        val all = BerlinSchoolCalendar.entries
        val breaks = all.filter { !it.publicHoliday }.map { it.name to it.range }
        val (name, days) = schoolDaysUntilBreak(java.time.LocalDate.of(2026, 12, 14), breaks, all.map { it.range })!!
        kotlin.test.assertEquals("Weihnachtsferien 2026/27", name)
        kotlin.test.assertEquals(7, days) // Mo 14. – Di 22.12.: 7 Schultage
    }
}
