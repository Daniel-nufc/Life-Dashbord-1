package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InsightsTest {
    private val today = LocalDate.of(2026, 11, 18) // Mittwoch

    @Test
    fun `Heatmap - Minuten je Tag, Zukunft leer`() {
        val map = Insights.heatmap(
            listOf(
                PlanResult(today, 960, 50, true),
                PlanResult(today, 1020, 25, true),
                PlanResult(today.minusDays(1), 960, 50, false),
                PlanResult(today.minusDays(8), 960, 30, true),
            ),
            today, weeks = 2,
        )
        assertEquals(listOf(0, 30, 0, 0, 0, 0, 0), map[0]) // Vorwoche: Dienstag 10.11.
        assertEquals(75, map[1][2]) // diese Woche Mittwoch
        assertEquals(0, map[1][1]) // Dienstag: verpasst zählt nicht
        assertNull(map[1][3]) // Donnerstag liegt in der Zukunft
    }

    @Test
    fun `beste Zeit ist das Fenster mit der hoechsten Quote`() {
        val outcomes = (1..6).map { PlanResult(today.minusDays(it.toLong()), 16 * 60, 50, true) } +
            (1..6).map { PlanResult(today.minusDays(it.toLong()), 20 * 60, 50, it % 3 == 0) }
        val best = Insights.bestTime(outcomes)!!
        assertEquals(1.0, best.rate, 0.001)
        assertEquals(true, best.fromHour in 15..16)
        assertNull(Insights.bestTime(outcomes.take(2)))
    }

    @Test
    fun `Serie zaehlt Tage am Stueck`() {
        val o = listOf(0L, 1L, 2L, 4L).map { PlanResult(today.minusDays(it), 900, 25, true) }
        assertEquals(3, Insights.currentRunDays(o, today))
    }
}
