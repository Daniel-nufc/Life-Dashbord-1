package app.lifedashboard.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationHelpersTest {
    @Test
    fun themenVorschlaegeProFach() {
        assertTrue(TopicSuggestions.forSubject("MA-B", "Mathematik").any { it.contains("Ableitung") })
        assertTrue(TopicSuggestions.forSubject("BIO-Pro", "Biologie (Pro)").any { it.contains("Zell") })
        assertTrue(TopicSuggestions.forSubject("XYZ", "Unbekannt").isNotEmpty())
    }

    @Test
    fun euroFormatUndEingabe() {
        assertEquals("12,50 €", 1250L.euro())
        assertEquals("1.234,00 €", 123400L.euro())
        assertEquals("−5,00 €", (-500L).euro())
        assertEquals(1250L, parseEuro("12,50"))
        assertEquals(1250L, parseEuro("12.5"))
        assertEquals(123456L, parseEuro("1.234,56"))
    }
}
