package app.lifedashboard.planner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavingsPlanTest {
    @Test
    fun `gap is translated into job hours and etsy sales`() {
        // 107 € nötig, 40 € Taschengeld, 10 € Etsy-Gewinn → 57 € fehlen
        val plan = SavingsPlan(neededCents = 10_700, fixedIncomeCents = 4_000, jobCents = 0, etsyNetCents = 1_000, jobWageCents = 1_390, etsyNetPerSaleCents = 250)
        assertEquals(5_700, plan.gapCents)
        assertEquals(5, plan.jobHoursForGap) // 57 / 13,90 → 4,1 → 5 Stunden
        assertEquals(23, plan.etsySalesForGap)
        assertFalse(plan.covered)
    }

    @Test
    fun `covered plan has no gap and no hints`() {
        val plan = SavingsPlan(10_000, 4_000, 8_000, 0, 1_390, 0)
        assertTrue(plan.covered)
        assertEquals(0, plan.gapCents)
        assertNull(plan.jobHoursForGap)
        assertNull(plan.etsySalesForGap)
        assertEquals(1f, plan.fraction)
    }
}
