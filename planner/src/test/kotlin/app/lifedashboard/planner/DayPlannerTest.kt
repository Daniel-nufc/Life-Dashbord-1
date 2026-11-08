package app.lifedashboard.planner

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayPlannerTest {
    private val monday = LocalDate.of(2026, 11, 9)
    // Montag: frei 15:25–18:00 und 20:15–22:00 (nach Schule/Heimweg, Floorball 18–20:15)
    private val slots = listOf(TimeRange.of(15, 25, 18, 0), TimeRange.of(20, 15, 22, 0))

    private fun assertNoOverlap(blocks: List<PlannedBlock>) {
        blocks.zipWithNext().forEach { (a, b) -> assertTrue(!a.range.overlaps(b.range), "Überlappung: $a / $b") }
    }

    private fun assertInsideSlots(blocks: List<PlannedBlock>) {
        blocks.forEach { block ->
            assertTrue(slots.any { !block.range.start.isBefore(it.start) && !block.range.end.isAfter(it.end) }, "außerhalb: $block")
        }
    }

    @Test
    fun `Hausaufgabe fuer morgen kommt vor Etsy und nachmittags`() {
        val plan = DayPlanner.plan(
            monday,
            slots,
            listOf(
                Demand("etsy", DemandType.ETSY, "Etsy", 45, 0.6),
                Demand("hw", DemandType.HOMEWORK, "Mathe S. 45", 30, 1.0, deadline = monday.plusDays(1)),
            ),
        )
        val hw = plan.first { it.demandKey == "hw" }
        assertEquals(LocalTime.of(15, 25), hw.range.start)
        val etsy = plan.first { it.demandKey == "etsy" }
        assertTrue(etsy.range.start.hour >= 15)
        assertNoOverlap(plan)
        assertInsideSlots(plan)
    }

    @Test
    fun `lange Lerneinheiten werden in Bloecke mit Pausen geteilt`() {
        val plan = DayPlanner.plan(monday, slots, listOf(Demand("study", DemandType.STUDY, "Bio", 100, 1.0, maxChunk = 50)))
        assertEquals(100, plan.sumOf { it.range.duration.toMinutes().toInt() })
        assertTrue(plan.all { it.range.duration.toMinutes() <= 50 })
        assertNoOverlap(plan)
        // Pause von 5 Min zwischen zwei Blöcken im selben Fenster
        val sameSlot = plan.filter { it.range.start.hour in 15..17 }
        if (sameSlot.size >= 2) assertTrue(java.time.Duration.between(sameSlot[0].range.end, sameSlot[1].range.start).toMinutes() >= 5)
    }

    @Test
    fun `was nicht passt bleibt uebrig statt zu ueberlappen`() {
        val plan = DayPlanner.plan(monday, listOf(TimeRange.of(16, 0, 17, 0)), listOf(Demand("big", DemandType.STUDY, "Viel", 300, 1.0)))
        assertTrue(plan.sumOf { it.range.duration.toMinutes() } <= 60)
        assertNoOverlap(plan)
    }

    @Test
    fun `gelernte Ablehnungen verschieben auf eine andere Tageszeit`() {
        val prefs = PreferenceModel(
            mapOf(
                PreferenceModel.keyOf(DemandType.ETSY, monday, LocalTime.of(20, 15)) to PreferenceModel.Counts(0, 6),
            ),
        )
        val plan = DayPlanner.plan(monday, slots, listOf(Demand("etsy", DemandType.ETSY, "Etsy", 30, 0.6)), PlannerConfig(preferences = prefs))
        assertTrue(plan.single().range.start.hour < 18, "Etsy sollte nicht mehr abends landen: ${plan.single().range}")
    }

    @Test
    fun `ab jetzt planen schneidet vergangene Zeit ab`() {
        val now = DayPlanner.from(slots, LocalTime.of(16, 30))
        assertEquals(listOf(TimeRange.of(16, 30, 18, 0), TimeRange.of(20, 15, 22, 0)), now)
    }

    @Test
    fun `Tageslimit wird eingehalten`() {
        val plan = DayPlanner.plan(
            monday, slots,
            listOf(Demand("a", DemandType.STUDY, "A", 120, 1.0), Demand("b", DemandType.ETSY, "B", 120, 0.6)),
            maxTotalMinutes = 90,
        )
        assertTrue(plan.sumOf { it.range.duration.toMinutes() } <= 90)
        assertNoOverlap(plan)
    }
}
