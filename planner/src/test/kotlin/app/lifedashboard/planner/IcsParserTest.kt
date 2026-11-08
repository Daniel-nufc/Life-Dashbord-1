package app.lifedashboard.planner

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IcsParserTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private val moodle = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Moodle Pty Ltd//NONSGML Moodle Version 2024//EN
        BEGIN:VEVENT
        UID:123@lernraum-berlin.de
        SUMMARY:Abgabe Erörterung ist fällig
        DESCRIPTION:Bitte als PDF hochladen\, max. 3 Seiten\nViel Erfolg
        CLASS:PUBLIC
        DTSTAMP:20261108T120000Z
        DTSTART:20261120T225900Z
        DTEND:20261120T225900Z
        CATEGORIES:DE-B 3G62
        END:VEVENT
        BEGIN:VEVENT
        UID:holiday-1
        SUMMARY:Studientag
        DTSTART;VALUE=DATE:20261125
        DTEND;VALUE=DATE:20261126
        END:VEVENT
        BEGIN:VEVENT
        UID:tz-1
        SUMMARY:Elternabend mit einem sehr langen Titel, der umg
         ebrochen wurde
        DTSTART;TZID=Europe/Berlin:20261126T180000
        DTEND;TZID=Europe/Berlin:20261126T193000
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `liest Moodle-Abgabe mit UTC-Zeit`() {
        val events = IcsParser.parse(moodle, berlin)
        assertEquals(3, events.size)
        val abgabe = events[0]
        assertEquals("123@lernraum-berlin.de", abgabe.uid)
        assertEquals("Abgabe Erörterung ist fällig", abgabe.summary)
        assertEquals("Bitte als PDF hochladen, max. 3 Seiten\nViel Erfolg", abgabe.description)
        assertEquals(LocalDate.of(2026, 11, 20), abgabe.startDate)
        assertEquals(LocalTime.of(23, 59), abgabe.startTime) // 22:59 UTC = 23:59 in Berlin (Winterzeit)
    }

    @Test
    fun `ganztaegige Termine haben keine Uhrzeit`() {
        val tag = IcsParser.parse(moodle, berlin)[1]
        assertEquals(LocalDate.of(2026, 11, 25), tag.startDate)
        assertNull(tag.startTime)
    }

    @Test
    fun `umgebrochene Zeilen und Zeitzonen`() {
        val abend = IcsParser.parse(moodle, berlin)[2]
        assertEquals("Elternabend mit einem sehr langen Titel, der umgebrochen wurde", abend.summary)
        assertEquals(LocalTime.of(18, 0), abend.startTime)
        assertEquals(LocalTime.of(19, 30), abend.endTime)
    }

    @Test
    fun `kaputte Eintraege werden uebersprungen`() {
        val text = "BEGIN:VEVENT\nSUMMARY:ohne Datum\nEND:VEVENT\nBEGIN:VEVENT\nDTSTART:kaputt\nEND:VEVENT"
        assertEquals(0, IcsParser.parse(text, berlin).size)
    }
}
