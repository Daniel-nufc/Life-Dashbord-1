package app.lifedashboard.planner

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceTest {
    private val newFormat = """
        "Girokonto";"DE00 1203 0000 0000 0000 00"
        ""
        "Kontostand vom 08.11.2026:";"123,45 €"
        ""
        "Buchungsdatum";"Wertstellung";"Status";"Zahlungspflichtige*r";"Zahlungsempfänger*in";"Verwendungszweck";"Umsatztyp";"IBAN";"Betrag (€)";"Gläubiger-ID";"Mandatsreferenz";"Kundenreferenz"
        "07.11.26";"07.11.26";"Gebucht";"Daniel";"REWE Markt GmbH";"REWE SAGT DANKE 1234";"Ausgang";"DE00";"-12,34";"";"";""
        "21.10.26";"21.10.26";"Gebucht";"Mama";"Daniel";"Taschengeld Oktober";"Eingang";"DE00";"20,00";"";"";""
        "06.11.26";"06.11.26";"Vorgemerkt";"Daniel";"Spotify";"Abo";"Ausgang";"DE00";"-1.010,99";"";"";""
    """.trimIndent()

    @Test
    fun `DKB neues Format`() {
        val tx = DkbCsvParser.parse(newFormat)
        assertEquals(2, tx.size) // vorgemerkte Buchung wird ignoriert
        assertEquals(LocalDate.of(2026, 11, 7), tx[0].date)
        assertEquals(-1234, tx[0].amountCents)
        assertEquals("REWE Markt GmbH", tx[0].counterparty)
        assertEquals(2000, tx[1].amountCents)
        assertEquals("Mama", tx[1].counterparty)
    }

    @Test
    fun `DKB altes Format`() {
        val old = """
            "Kontonummer:";"DE00";
            "Buchungstag";"Wertstellung";"Buchungstext";"Auftraggeber / Begünstigter";"Verwendungszweck";"Kontonummer";"BLZ";"Betrag (EUR)";
            "02.10.2025";"02.10.2025";"Lastschrift";"NETFLIX.COM";"Abo";"DE00";"BYLADEM";"-1.099,00";
        """.trimIndent()
        val tx = DkbCsvParser.parse(old).single()
        assertEquals(-109900, tx.amountCents)
        assertEquals("NETFLIX.COM", tx.counterparty)
    }

    @Test
    fun `Betraege und Daten`() {
        assertEquals(-123456, DkbCsvParser.parseAmount("-1.234,56 €"))
        assertEquals(500, DkbCsvParser.parseAmount("5,00"))
        assertEquals(LocalDate.of(2026, 1, 5), DkbCsvParser.parseDate("05.01.26"))
    }

    @Test
    fun `Kategorien mit Standardregeln und gelernten Regeln`() {
        assertEquals("Lebensmittel", Categorizer.categorize("REWE Markt GmbH", "REWE SAGT DANKE", emptyList()))
        assertEquals("Abos", Categorizer.categorize("Spotify AB", "", emptyList()))
        assertNull(Categorizer.categorize("Kiosk Müller", "Kartenzahlung", emptyList()))
        // Korrektur: Kiosk Müller → Essen gehen
        val learned = listOf(Categorizer.Rule(Categorizer.keywordFor("Kiosk Müller", "Kartenzahlung"), "Essen gehen"))
        assertEquals("kiosk müller", learned.single().keyword)
        assertEquals("Essen gehen", Categorizer.categorize("KIOSK MÜLLER", "Kartenzahlung 123", learned))
        // Gelernte Regel schlägt Standardregel
        val override = listOf(Categorizer.Rule("rewe", "Party"))
        assertEquals("Party", Categorizer.categorize("REWE", "", override))
    }

    @Test
    fun `Abo-Erkennung`() {
        val tx = listOf(
            ImportedTransaction(LocalDate.of(2026, 8, 3), -1099, "Spotify", ""),
            ImportedTransaction(LocalDate.of(2026, 9, 3), -1099, "Spotify", ""),
            ImportedTransaction(LocalDate.of(2026, 10, 4), -1099, "Spotify", ""),
            ImportedTransaction(LocalDate.of(2026, 9, 10), -2350, "REWE", ""),
            ImportedTransaction(LocalDate.of(2026, 9, 12), -800, "REWE", ""),
        )
        val subs = SubscriptionDetector.detect(tx)
        assertEquals(1, subs.size)
        assertEquals("spotify", subs[0].name)
        assertEquals(1099, subs[0].averageCents)
        assertEquals(LocalDate.of(2026, 11, 4), subs[0].nextExpected)
    }

    @Test
    fun `Monatsprognose`() {
        // 10. Nov, 100 € Stand, 50 € bisher ausgegeben → 5 €/Tag, 20 Tage übrig → −100 €, +20 € Taschengeld
        val projected = MonthForecast.project(LocalDate.of(2026, 11, 10), 10_000, 5_000, 2_000)
        assertEquals(2_000, projected)
        assertTrue(MonthForecast.project(LocalDate.of(2026, 11, 30), 5_000, 9_000, 0) == 5_000L)
    }
}
