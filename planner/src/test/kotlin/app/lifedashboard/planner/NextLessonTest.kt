package app.lifedashboard.planner

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextLessonTest {

    private val calc = SchoolWeekCalculator(LocalDate.of(2026, 11, 9), WeekType.A)

    // Mathe: Montag nur in Woche B, Freitag jede Woche
    private val mathe = listOf(WeeklySlot(MONDAY, WeekType.B), WeeklySlot(FRIDAY))

    // Deutsch: Montag nur in Woche A, Mittwoch jede Woche
    private val deutsch = listOf(WeeklySlot(MONDAY, WeekType.A), WeeklySlot(WEDNESDAY))

    @Test
    fun `Mathe am Montag der A-Woche aufbekommen ist am Freitag faellig`() {
        assertEquals(LocalDate.of(2026, 11, 13), nextOccurrence(LocalDate.of(2026, 11, 9), mathe, calc::weekTypeFor))
    }

    @Test
    fun `Mathe am Freitag aufbekommen ist am Montag der B-Woche faellig`() {
        assertEquals(LocalDate.of(2026, 11, 16), nextOccurrence(LocalDate.of(2026, 11, 13), mathe, calc::weekTypeFor))
    }

    @Test
    fun `Deutsch am Mittwoch - Montag der B-Woche wird uebersprungen`() {
        assertEquals(LocalDate.of(2026, 11, 18), nextOccurrence(LocalDate.of(2026, 11, 11), deutsch, calc::weekTypeFor))
    }

    @Test
    fun `uebernaechste Stunde`() {
        assertEquals(
            LocalDate.of(2026, 11, 16),
            nextOccurrence(LocalDate.of(2026, 11, 9), mathe, calc::weekTypeFor, skip = 1),
        )
    }

    @Test
    fun `ohne Stunden gibt es kein Datum`() {
        assertNull(nextOccurrence(LocalDate.of(2026, 11, 9), emptyList(), calc::weekTypeFor))
    }
}
