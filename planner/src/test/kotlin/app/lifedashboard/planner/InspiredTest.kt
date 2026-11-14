package app.lifedashboard.planner

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspiredTest {
    private val today = LocalDate.of(2026, 11, 18) // Mittwoch
    private fun r(daysAgo: Long, minutes: Int, done: Boolean = true) = PlanResult(today.minusDays(daysAgo), 960, minutes, done)

    @Test
    fun `Wald - Baeume je 25 Minuten, verpasste Bloecke verwelken`() {
        val f = Inspired.forest(listOf(r(0, 50), r(0, 20), r(1, 25, done = false), r(9, 100)), today)
        assertEquals(7, f.size)
        assertEquals(3, f[2].trees) // Mittwoch: 2 + 1
        assertEquals(1, f[1].withered) // Dienstag
        assertEquals(3, f.sumOf { it.trees }) // letzte Woche zählt nicht
    }

    @Test
    fun `Wochen-Boss - Schaden und Sieg`() {
        val b = Inspired.weekBoss(300, 120, today)
        assertEquals(180, b.left)
        assertEquals(0.4f, b.fraction)
        assertTrue(Inspired.weekBoss(300, 320, today).defeated)
        assertTrue(b.name.isNotBlank())
    }

    @Test
    fun `diese Woche gegen letzte Woche zum selben Zeitpunkt`() {
        // diese Woche Mo+Mi je 50; letzte Woche Mo 50, Fr 100
        val c = Inspired.weekCompare(listOf(r(0, 50), r(2, 50), r(9, 50), r(5, 100)), today)
        assertEquals(100, c.thisWeek)
        assertEquals(50, c.lastWeekSamePoint)
        assertEquals(150, c.lastWeekTotal)
        assertEquals(50, c.ahead)
    }

    @Test
    fun `Trainingsbelastung - viel mehr als gewohnt wird erkannt`() {
        val base = (7L..34L step 7).map { Activity(today.minusDays(it), "RUN", 60, 8000) } // 60 Min/Woche
        assertEquals(Inspired.LoadLevel.STEADY, Inspired.trainingLoad(base + Activity(today, "RUN", 60, 8000), today).level)
        assertEquals(Inspired.LoadLevel.TOO_MUCH, Inspired.trainingLoad(base + Activity(today, "RUN", 150, 20000), today).level)
        assertEquals(Inspired.LoadLevel.LOW, Inspired.trainingLoad(base, today).level)
        assertEquals(Inspired.LoadLevel.NO_DATA, Inspired.trainingLoad(emptyList(), today).level)
    }

    @Test
    fun `Sport-Monat zaehlt nur den Monat`() {
        val m = Inspired.sportMonth(listOf(Activity(today, "RUN", 30, 5000), Activity(today.minusDays(30), "RUN", 30, 5000), Activity(today.minusDays(1), "FLOORBALL", 90, null)), today)
        assertEquals(Inspired.SportMonth(2, 120, 5.0), m)
    }

    @Test
    fun `Schlaf - Schnitt, Schuld und Zubettgeh-Zeit`() {
        val a = Inspired.sleepAdvice(listOf(Duration.ofHours(7), null, Duration.ofMinutes(390), Duration.ofHours(9)), LocalTime.of(6, 0))
        assertEquals(450, a.averageMinutes) // (420 + 390 + 540) / 3
        assertEquals(150, a.debtMinutes) // 60 + 90
        assertEquals(LocalTime.of(21, 45), a.bedtime)
        assertNull(Inspired.sleepAdvice(listOf(null), LocalTime.of(6, 0)).averageMinutes)
    }

    @Test
    fun `Geld - Tage ohne Ausgabe, Arbeitszeit, pro Tag`() {
        assertEquals(2, Inspired.noSpendStreak(setOf(today.minusDays(2)), today, today.minusDays(30)))
        assertEquals(0, Inspired.noSpendStreak(setOf(today), today, today.minusDays(30)))
        assertEquals(125, Inspired.workMinutes(2500, 1200)) // 25 € bei 12 €/Std
        assertNull(Inspired.workMinutes(2500, 0))
        val next = Inspired.nextIncomeDay(listOf(21, 1), today)!!
        assertEquals(LocalDate.of(2026, 11, 21), next)
        assertEquals(400, Inspired.perDay(1200, today, next))
        assertEquals(LocalDate.of(2026, 12, 1), Inspired.nextIncomeDay(listOf(1), today))
    }

    @Test
    fun `Noten-Trend`() {
        assertEquals(Inspired.Trend.UP, Inspired.gradeTrend(listOf(8, 9, 11)))
        assertEquals(Inspired.Trend.DOWN, Inspired.gradeTrend(listOf(12, 12, 9)))
        assertEquals(Inspired.Trend.FLAT, Inspired.gradeTrend(listOf(10, 10)))
        assertNull(Inspired.gradeTrend(listOf(10)))
    }

    @Test
    fun `wiederkehrende Aufgabe - naechster Termin liegt nie in der Vergangenheit`() {
        assertEquals(today.plusDays(5), Inspired.nextRepeat(today.minusDays(2), today, 7))
        assertEquals(today.plusDays(4), Inspired.nextRepeat(today.minusDays(17), today, 7)) // lange überfällig → nächster in der Zukunft
        assertEquals(today.plusDays(1), Inspired.nextRepeat(null, today, 1))
    }

    @Test
    fun `Energie-Effekt braucht genug Daten`() {
        val energy = mapOf(today to 5, today.minusDays(1) to 1)
        val results = (1..3).map { r(0, 25) } + listOf(r(1, 25, false), r(1, 25, false), r(1, 25))
        assertEquals(100 to 33, Inspired.energyEffect(energy, results))
        assertNull(Inspired.energyEffect(energy, results.take(2)))
    }
}
