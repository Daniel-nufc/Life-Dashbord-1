package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunningProgressTest {
    private val today = LocalDate.of(2026, 11, 14)

    @Test
    fun `Weg zu 10 km - laengster Lauf, Wochen-km, Prognose`() {
        val acts = listOf(
            Activity(today.minusDays(2), "RUN", 34, 5200),
            Activity(today.minusDays(9), "RUN", 30, 4500),
            Activity(today.minusDays(40), "RUN", 80, 12000), // Winterberg – zu lange her für „aktuell“
            Activity(today.minusDays(3), "FLOORBALL", 90, null),
        )
        val p = RunningProgress.tenK(acts, today)
        assertEquals(5.2, p.longestRecentKm, 0.001)
        assertEquals(listOf(0.0, 0.0, 4.5, 5.2), p.weeklyKm)
        assertEquals(7, p.weeksTo10k) // 5,2 × 1,1^7 ≈ 10,1
        assertEquals(5.7, p.nextLongRunKm!!, 0.001)
    }

    @Test
    fun `ohne Lauf keine Prognose, ab 10 km geschafft`() {
        assertNull(RunningProgress.tenK(emptyList(), today).weeksTo10k)
        assertEquals(0, RunningProgress.tenK(listOf(Activity(today, "RUN", 60, 10200)), today).weeksTo10k)
    }

    @Test
    fun `Bestleistungen`() {
        val b = RunningProgress.bests(
            listOf(
                Activity(today.minusDays(40), "RUN", 80, 12000),
                Activity(today.minusDays(2), "RUN", 25, 5000), // 5:00 /km
                Activity(today.minusDays(5), "RUN", 6, 2000), // zu kurz für die Pace-Wertung
                Activity(today.minusDays(7), "BIKE", 60, 21000),
                Activity(today.minusDays(3), "FLOORBALL", 90, null),
            ),
        )
        assertEquals(12.0, b.longestRunKm!!, 0.001)
        assertEquals(300, b.fastestPaceSecPerKm)
        assertEquals("5:00 /km", RunningProgress.pace(300))
        assertEquals(21.0, b.longestRideKm!!, 0.001)
        assertEquals(90, b.longestSessionMinutes)
    }
}
