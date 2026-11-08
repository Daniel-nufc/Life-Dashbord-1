package app.lifedashboard.planner

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class FreeSlotsTest {

    private val day = TimeRange.of(6, 0, 22, 0)

    @Test
    fun `ohne Termine ist der ganze Tag frei`() {
        assertEquals(listOf(day), freeSlots(day, emptyList()))
    }

    @Test
    fun `Schultag mit Floorball ergibt drei freie Fenster`() {
        val busy = listOf(
            TimeRange.of(8, 0, 9, 30),
            TimeRange.of(9, 50, 11, 20),
            TimeRange.of(11, 40, 13, 10),
            TimeRange.of(13, 30, 15, 0),
            TimeRange.of(18, 0, 20, 15),
        )
        val free = freeSlots(day, busy, minDuration = Duration.ofMinutes(30))
        assertEquals(
            listOf(
                TimeRange.of(6, 0, 8, 0),
                TimeRange.of(15, 0, 18, 0),
                TimeRange.of(20, 15, 22, 0),
            ),
            free,
        )
    }

    @Test
    fun `kurze Pausen werden ignoriert, aber ab Mindestdauer gezaehlt`() {
        val busy = listOf(TimeRange.of(8, 0, 9, 30), TimeRange.of(9, 50, 11, 20))
        val free = freeSlots(TimeRange.of(8, 0, 12, 0), busy, minDuration = Duration.ofMinutes(20))
        assertEquals(listOf(TimeRange.of(9, 30, 9, 50), TimeRange.of(11, 20, 12, 0)), free)
    }

    @Test
    fun `ueberlappende Termine und Termine ausserhalb des Fensters`() {
        val busy = listOf(
            TimeRange.of(5, 0, 7, 0),     // ragt vorne ins Fenster
            TimeRange.of(10, 0, 12, 0),
            TimeRange.of(11, 0, 13, 0),   // überlappt
            TimeRange.of(21, 30, 23, 0),  // ragt hinten raus
        )
        assertEquals(
            listOf(TimeRange.of(7, 0, 10, 0), TimeRange.of(13, 0, 21, 30)),
            freeSlots(day, busy),
        )
    }

    @Test
    fun `mergeRanges fuegt angrenzende Bloecke zusammen`() {
        val merged = mergeRanges(listOf(TimeRange.of(9, 30, 10, 0), TimeRange.of(8, 0, 9, 30)))
        assertEquals(listOf(TimeRange.of(8, 0, 10, 0)), merged)
    }
}
