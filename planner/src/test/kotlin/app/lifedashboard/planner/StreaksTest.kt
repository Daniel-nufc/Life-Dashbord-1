package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 12) // Donnerstag

    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun `durchgehende Serie`() {
        assertEquals(4, Streaks.compute(days(0, 1, 2, 3), today).current)
    }

    @Test
    fun `heute noch offen bricht die Serie nicht`() {
        assertEquals(3, Streaks.compute(days(1, 2, 3), today).current)
    }

    @Test
    fun `ein verpasster Tag pro Woche wird vom Joker gerettet`() {
        val result = Streaks.compute(days(0, 1, 3, 4), today) // Di (2) fehlt
        assertEquals(5, result.current)
        assertTrue(today.minusDays(2) in result.jokerDays)
    }

    @Test
    fun `zwei verpasste Tage in derselben Woche brechen die Serie`() {
        // Do 12., Mi 11., (Di 10. fehlt), Mo 9., (So 8. – andere Woche), ...
        val result = Streaks.compute(days(0, 1, 3, 5, 6), today) // fehlt: 10. (Di) und 8. (So)
        // 10. = Joker KW46, 8. = Joker KW45 → Serie hält bis zum 6.
        assertEquals(7, result.current)
        val broken = Streaks.compute(days(0, 2, 4), today) // fehlt 11. und 9. (beide KW46)
        assertEquals(3, broken.current) // 12., Joker 11., 10. – am 9. ist der Joker der Woche schon weg
    }

    @Test
    fun `beste Serie in der Vergangenheit`() {
        val history = (10L..20L).map { today.minusDays(it) }.toSet() + today
        val result = Streaks.compute(history, today)
        assertEquals(1, result.current)
        assertEquals(11, result.best)
    }

    @Test
    fun `leere Historie`() {
        assertEquals(0, Streaks.compute(emptySet(), today).current)
    }
}
