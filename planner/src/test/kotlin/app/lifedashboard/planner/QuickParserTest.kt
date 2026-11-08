package app.lifedashboard.planner

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class QuickParserTest {
    private val today = LocalDate.of(2026, 11, 9) // Montag
    private val subjects = listOf(
        QuickSubject("MA-B", "Mathematik"), QuickSubject("DE-B", "Deutsch"), QuickSubject("E-B", "Englisch"),
        QuickSubject("BIO", "Biologie"), QuickSubject("CH", "Chemie"),
    )

    private fun parse(s: String) = QuickParser.parse(s, today, subjects)

    @Test
    fun `Termin mit Wochentag und Uhrzeit`() {
        val e = assertIs<QuickIntent.Event>(parse("Mi 17 Uhr Zahnarzt"))
        assertEquals("Zahnarzt", e.title)
        assertEquals(LocalDate.of(2026, 11, 11), e.date)
        assertEquals(LocalTime.of(17, 0), e.start)
        assertEquals(LocalTime.of(18, 0), e.end)
    }

    @Test
    fun `Termin morgen mit Zeitraum`() {
        val e = assertIs<QuickIntent.Event>(parse("morgen 15:30-17 Treffen mit Tom"))
        assertEquals(today.plusDays(1), e.date)
        assertEquals(LocalTime.of(15, 30), e.start)
        assertEquals(LocalTime.of(17, 0), e.end)
        assertEquals("Treffen mit Tom", e.title)
    }

    @Test
    fun `Hausaufgabe mit Fach`() {
        val h = assertIs<QuickIntent.Homework>(parse("HA Mathe S. 45 Nr. 3"))
        assertEquals("MA-B", h.subjectCode)
        assertEquals("S. 45 Nr. 3", h.title)
    }

    @Test
    fun `Klausur mit Datum`() {
        val k = assertIs<QuickIntent.Exam>(parse("Mathe Klausur am 2.12."))
        assertEquals("MA-B", k.subjectCode)
        assertEquals(LocalDate.of(2026, 12, 2), k.date)
    }

    @Test
    fun `Schicht am Samstag`() {
        val s = assertIs<QuickIntent.Shift>(parse("Schicht Sa 8-14"))
        assertEquals(LocalDate.of(2026, 11, 14), s.date)
        assertEquals(LocalTime.of(8, 0), s.start)
        assertEquals(LocalTime.of(14, 0), s.end)
    }

    @Test
    fun `Ausgabe mit Betrag`() {
        val x = assertIs<QuickIntent.Expense>(parse("12,50 Döner"))
        assertEquals(1250, x.amountCents)
        assertEquals("Döner", x.what)
    }

    @Test
    fun `Aufgabe ohne Zeit`() {
        val t = assertIs<QuickIntent.Todo>(parse("Etsy Mockups machen bis Freitag"))
        assertEquals("Etsy Mockups machen", t.title)
        assertEquals(LocalDate.of(2026, 11, 13), t.due)
    }

    @Test
    fun `Datum in der Vergangenheit springt ins naechste Jahr`() {
        val k = assertIs<QuickIntent.Exam>(parse("Bio Test 5.1."))
        assertEquals(LocalDate.of(2027, 1, 5), k.date)
        assertEquals("BIO", k.subjectCode)
    }
}
