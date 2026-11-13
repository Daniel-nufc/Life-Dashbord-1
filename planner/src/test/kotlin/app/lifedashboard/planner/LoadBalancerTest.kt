package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoadBalancerTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private fun week(vararg capacity: Int, load: Int = 0) = capacity.mapIndexed { i, c -> BalanceDay(monday.plusDays(i.toLong()), c, load) }

    @Test
    fun `tasks are spread instead of stacked on the first day`() {
        val days = week(120, 120, 120, 120, 120)
        val items = (1..5).map { BalanceItem("task:$it", 30, monday.plusDays(4)) }
        val perDay = LoadBalancer.assign(days, items).values.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        assertEquals(5, perDay.size, "jeder Tag bekommt etwas: $perDay")
        assertTrue(perDay.values.all { it == 30 })
    }

    @Test
    fun `nothing is planned after its deadline`() {
        val days = week(120, 120, 120, 120, 120)
        val result = LoadBalancer.assign(days, listOf(BalanceItem("task:1", 100, monday.plusDays(1))))
        assertTrue(result.getValue("task:1").keys.all { !it.isAfter(monday.plusDays(1)) })
        assertEquals(100, result.getValue("task:1").values.sum())
    }

    @Test
    fun `busy days get less, days with little free time are respected`() {
        // Montag ist schon voll mit Lernen, Mittwoch hat kaum freie Zeit (Training)
        val days = listOf(
            BalanceDay(monday, 150, 120),
            BalanceDay(monday.plusDays(1), 150, 0),
            BalanceDay(monday.plusDays(2), 30, 0),
            BalanceDay(monday.plusDays(3), 150, 0),
        )
        val result = LoadBalancer.assign(days, (1..4).map { BalanceItem("task:$it", 30, monday.plusDays(3)) })
        val perDay = result.values.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        assertEquals(null, perDay[monday], "voller Tag bleibt verschont: $perDay")
        assertTrue((perDay[monday.plusDays(2)] ?: 0) <= 30)
        assertEquals(120, perDay.values.sum())
    }

    @Test
    fun `overdue work goes to the first day and long tasks are split`() {
        val days = week(180, 180, 180)
        val result = LoadBalancer.assign(days, listOf(BalanceItem("task:1", 30, monday.minusDays(2)), BalanceItem("task:2", 120, monday.plusDays(2))))
        assertEquals(setOf(monday), result.getValue("task:1").keys)
        assertTrue(result.getValue("task:2").values.all { it <= 60 })
        assertEquals(120, result.getValue("task:2").values.sum())
    }
}
