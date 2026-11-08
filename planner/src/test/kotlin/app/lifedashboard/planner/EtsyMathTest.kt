package app.lifedashboard.planner

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtsyMathTest {
    private val fees = EtsyFees()

    @Test
    fun `Gebuehren fuer einen Verkauf`() {
        // 4,99 €: 10,5 % = 0,52 + 0,30 fix + 0,18 Einstellgebühr = 1,00 €
        assertEquals(100, EtsyMath.fees(EtsySaleData(LocalDate.of(2026, 11, 20), "A", "Winter-Set", 499, 1), fees))
    }

    @Test
    fun `Monat mit mehreren Positionen pro Bestellung`() {
        val sales = listOf(
            EtsySaleData(LocalDate.of(2026, 11, 20), "A", "Winter-Set", 499, 1),
            EtsySaleData(LocalDate.of(2026, 11, 20), "A", "Fuchs", 299, 1),
            EtsySaleData(LocalDate.of(2026, 11, 22), "B", "Winter-Set", 499, 2),
            EtsySaleData(LocalDate.of(2026, 10, 2), "C", "Fuchs", 299, 1),
        )
        val nov = EtsyMath.month(sales, YearMonth.of(2026, 11), fees)
        assertEquals(2, nov.orders)
        assertEquals(4, nov.items)
        assertEquals(1796, nov.revenueCents)
        // ≈ 10,5 % von 17,96 (1,89) + 2 × 0,30 + 4 × 0,18 – pro Position gerundet = 3,20
        assertEquals(320, nov.feesCents)
        assertEquals(1476, nov.netCents)
    }

    @Test
    fun `Vergleich zum Vormonat und Top-Produkte`() {
        assertEquals(50, EtsyMath.change(150, 100))
        assertEquals(-25, EtsyMath.change(75, 100))
        assertNull(EtsyMath.change(100, 0))
        val top = EtsyMath.topProducts(
            listOf(
                EtsySaleData(LocalDate.now(), "A", "Fuchs", 299, 1),
                EtsySaleData(LocalDate.now(), "B", "Winter-Set", 499, 2),
            ),
        )
        assertEquals("Winter-Set", top.first().first)
    }

    @Test
    fun `Saison-Tipp im November`() {
        assertTrue(EtsyMath.seasonalTip(LocalDate.of(2026, 11, 8)).contains("Weihnacht"))
    }

    @Test
    fun `naechste Saison wird rueckwaerts geplant`() {
        assertEquals("Valentinstag" to LocalDate.of(2027, 1, 15), EtsyMath.nextSeason(LocalDate.of(2026, 11, 9)))
        assertEquals("Ostern", EtsyMath.nextSeason(LocalDate.of(2027, 1, 10)).first)
    }
}
