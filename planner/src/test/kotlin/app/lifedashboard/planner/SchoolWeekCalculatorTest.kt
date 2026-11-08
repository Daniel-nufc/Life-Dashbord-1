package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SchoolWeekCalculatorTest {

    // KW 46/2026 (Mo 9.11.) ist laut Stundenplan Woche A.
    private val calc = SchoolWeekCalculator(LocalDate.of(2026, 11, 9), WeekType.A)

    @Test
    fun `Referenzwoche und Folgewochen wechseln ab`() {
        assertEquals(WeekType.A, calc.weekTypeFor(LocalDate.of(2026, 11, 9)))
        assertEquals(WeekType.A, calc.weekTypeFor(LocalDate.of(2026, 11, 13)))  // Freitag
        assertEquals(WeekType.B, calc.weekTypeFor(LocalDate.of(2026, 11, 16)))
        assertEquals(WeekType.A, calc.weekTypeFor(LocalDate.of(2026, 11, 23)))
    }

    @Test
    fun `Wochen vor der Referenz funktionieren auch`() {
        assertEquals(WeekType.B, calc.weekTypeFor(LocalDate.of(2026, 11, 4)))
        assertEquals(WeekType.A, calc.weekTypeFor(LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun `am Wochenende zaehlt fuer die Planung die naechste Woche`() {
        // So 8.11. gehört zur KW 45 (B), geplant wird aber schon KW 46 (A).
        assertEquals(WeekType.B, calc.weekTypeFor(LocalDate.of(2026, 11, 8)))
        assertEquals(WeekType.A, calc.weekTypeForPlanning(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `Ferienwochen koennen uebersprungen werden`() {
        // Eine Ferienwoche (21.–25.12.) – eine ungerade Anzahl, damit sich etwas ändert.
        val ferien = listOf(DateRange(LocalDate.of(2026, 12, 19), LocalDate.of(2026, 12, 27)))
        val mitFerien = SchoolWeekCalculator(
            LocalDate.of(2026, 11, 9), WeekType.A, ferien, skipHolidayWeeks = true,
        )
        // 9.11. A, 16. B, 23. A, 30. B, 7.12. A, 14.12. B
        assertEquals(WeekType.B, mitFerien.weekTypeFor(LocalDate.of(2026, 12, 14)))
        // Ferienwoche zählt nicht → 28.12. ist A (Fortsetzung nach dem 14.12.)
        assertEquals(WeekType.A, mitFerien.weekTypeFor(LocalDate.of(2026, 12, 28)))
        // Ohne Überspringen wäre der 28.12. eine B-Woche
        assertEquals(WeekType.B, calc.weekTypeFor(LocalDate.of(2026, 12, 28)))

        // Rückwärts: Referenz nach den Ferien (4.1.2027 = A)
        val rueckwaerts = SchoolWeekCalculator(
            LocalDate.of(2027, 1, 4), WeekType.A, ferien, skipHolidayWeeks = true,
        )
        assertEquals(WeekType.B, rueckwaerts.weekTypeFor(LocalDate.of(2026, 12, 28)))
        assertEquals(WeekType.A, rueckwaerts.weekTypeFor(LocalDate.of(2026, 12, 14)))
        val ohne = SchoolWeekCalculator(LocalDate.of(2027, 1, 4), WeekType.A)
        assertEquals(WeekType.B, ohne.weekTypeFor(LocalDate.of(2026, 12, 14)))
    }
}
