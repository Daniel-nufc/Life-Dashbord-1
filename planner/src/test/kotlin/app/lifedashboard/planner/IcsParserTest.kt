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

    // ---------- Familienkalender (FamilyWall, Google …) ----------

    private fun cal(vararg events: String) = "BEGIN:VCALENDAR\nVERSION:2.0\n" + events.joinToString("\n") + "\nEND:VCALENDAR"

    private val from = LocalDate.of(2026, 11, 1)
    private val to = LocalDate.of(2027, 1, 31)

    @Test
    fun `woechentlicher Serientermin mit ausgelassenem Tag`() {
        val text = cal(
            """
            BEGIN:VEVENT
            UID:gitarre@familywall
            SUMMARY:Gitarre
            LOCATION:Musikschule
            DTSTART;TZID=Europe/Berlin:20261005T160000
            DTEND;TZID=Europe/Berlin:20261005T170000
            RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261207T235959Z
            EXDATE;TZID=Europe/Berlin:20261116T160000
            BEGIN:VALARM
            TRIGGER:-PT15M
            DESCRIPTION:Erinnerung
            END:VALARM
            END:VEVENT
            """.trimIndent(),
        )
        val events = IcsParser.parse(text, berlin, from, to)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 7)),
            events.map { it.startDate },
        )
        assertEquals(LocalTime.of(16, 0), events[0].startTime)
        assertEquals(LocalTime.of(17, 0), events[0].endTime)
        assertEquals("Musikschule", events[0].location)
        assertEquals("Gitarre", events[0].summary) // VALARM-Text überschreibt nichts
        assertEquals(events.size, events.map { it.uid }.toSet().size) // jede Wiederholung eigene Kennung
    }

    @Test
    fun `jaehrlicher Geburtstag erscheint im Zeitraum`() {
        val text = cal(
            """
            BEGIN:VEVENT
            UID:bday-oma
            SUMMARY:Geburtstag Oma
            DTSTART;VALUE=DATE:19601212
            DTEND;VALUE=DATE:19601213
            RRULE:FREQ=YEARLY
            END:VEVENT
            """.trimIndent(),
        )
        val events = IcsParser.parse(text, berlin, from, to)
        assertEquals(listOf(LocalDate.of(2026, 12, 12)), events.map { it.startDate })
        assertNull(events[0].startTime)
    }

    @Test
    fun `monatlich am zweiten Dienstag mit Anzahl`() {
        val text = cal(
            """
            BEGIN:VEVENT
            UID:eltern
            SUMMARY:Elternrat
            DTSTART:20261013T170000Z
            DTEND:20261013T180000Z
            RRULE:FREQ=MONTHLY;BYDAY=2TU;COUNT=3
            END:VEVENT
            """.trimIndent(),
        )
        val days = IcsParser.parse(text, berlin, from, to).map { it.startDate }
        // 13.10. (vor dem Zeitraum) zählt mit → im Zeitraum nur noch 10.11. und 8.12.
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 8)), days)
    }

    @Test
    fun `einzeln verschobener und abgesagter Termin`() {
        val text = cal(
            """
            BEGIN:VEVENT
            UID:essen
            SUMMARY:Familienessen
            DTSTART;TZID=Europe/Berlin:20261101T130000
            DTEND;TZID=Europe/Berlin:20261101T140000
            RRULE:FREQ=WEEKLY
            END:VEVENT
            BEGIN:VEVENT
            UID:essen
            RECURRENCE-ID;TZID=Europe/Berlin:20261108T130000
            SUMMARY:Familienessen bei Tante
            DTSTART;TZID=Europe/Berlin:20261108T150000
            DTEND;TZID=Europe/Berlin:20261108T170000
            END:VEVENT
            BEGIN:VEVENT
            UID:abgesagt
            SUMMARY:Zahnarzt
            STATUS:CANCELLED
            DTSTART;TZID=Europe/Berlin:20261110T090000
            END:VEVENT
            """.trimIndent(),
        )
        val events = IcsParser.parse(text, berlin, from, LocalDate.of(2026, 11, 15))
        assertEquals(listOf(1, 8, 15), events.map { it.startDate.dayOfMonth }.sorted()) // Zahnarzt abgesagt
        val moved = events.first { it.startDate == LocalDate.of(2026, 11, 8) }
        assertEquals("Familienessen bei Tante", moved.summary)
        assertEquals(LocalTime.of(15, 0), moved.startTime)
    }

    @Test
    fun `mehrtaegige Termine erscheinen an jedem Tag`() {
        val text = cal(
            """
            BEGIN:VEVENT
            UID:urlaub
            SUMMARY:Ostsee
            DTSTART;VALUE=DATE:20261228
            DTEND;VALUE=DATE:20261231
            END:VEVENT
            BEGIN:VEVENT
            UID:party
            SUMMARY:Silvester
            DTSTART;TZID=Europe/Berlin:20261231T200000
            DTEND;TZID=Europe/Berlin:20270101T020000
            END:VEVENT
            """.trimIndent(),
        )
        val events = IcsParser.parse(text, berlin, from, to)
        val urlaub = events.filter { it.summary == "Ostsee" }
        assertEquals(listOf(28, 29, 30), urlaub.map { it.startDate.dayOfMonth })
        val party = events.filter { it.summary == "Silvester" }
        assertEquals(2, party.size)
        assertEquals(LocalTime.of(20, 0), party[0].startTime)
        assertEquals(LocalTime.of(23, 59), party[0].endTime)
        assertEquals(LocalDate.of(2027, 1, 1), party[1].startDate)
        assertEquals(LocalTime.of(2, 0), party[1].endTime)
    }

    @Test
    fun `ohne Zeitraum liefert eine Serie nur den ersten Termin`() {
        val text = cal("BEGIN:VEVENT\nUID:x\nSUMMARY:Serie\nDTSTART;VALUE=DATE:20261102\nRRULE:FREQ=DAILY\nEND:VEVENT")
        assertEquals(1, IcsParser.parse(text, berlin).size)
    }

    // ---------- Nur meine Termine ----------

    private val family = """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        UID:1
        SUMMARY:Zahnarzt
        DTSTART;TZID=Europe/Berlin:20261118T090000
        ATTENDEE;CN=Daniel Müller;PARTSTAT=ACCEPTED:mailto:daniel@example.org
        ATTENDEE;CN=Mama:mailto:mama@example.org
        END:VEVENT
        BEGIN:VEVENT
        UID:2
        SUMMARY:Elternabend Lisa
        DTSTART;TZID=Europe/Berlin:20261119T190000
        ATTENDEE;CN=Mama:mailto:mama@example.org
        END:VEVENT
        BEGIN:VEVENT
        UID:3
        SUMMARY:Daniel Friseur
        DTSTART;TZID=Europe/Berlin:20261120T150000
        END:VEVENT
        BEGIN:VEVENT
        UID:4
        SUMMARY:Danielas Geburtstag
        DTSTART;VALUE=DATE:20261121
        END:VEVENT
        BEGIN:VEVENT
        UID:5
        SUMMARY:Training
        DTSTART;TZID=Europe/Berlin:20261122T100000
        ATTENDEE:mailto:daniel.m@example.org
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test
    fun `Teilnehmer werden gelesen`() {
        val events = IcsParser.parse(family, berlin)
        assertEquals(listOf("Daniel Müller", "Mama"), events[0].attendees)
        assertEquals(listOf("daniel.m"), events[4].attendees)
    }

    @Test
    fun `nur Termine, bei denen ich ausgewaehlt bin oder im Titel stehe`() {
        val result = IcsPersonFilter.apply(IcsParser.parse(family, berlin), "Daniel")
        assertEquals(listOf("1", "3", "5"), result.kept.map { it.uid }) // nicht „Danielas Geburtstag“, nicht Mamas Termin
        assertEquals(5, result.total)
        assertEquals(setOf("Daniel Müller", "Mama", "daniel.m"), result.people.toSet())
    }

    @Test
    fun `ohne Namen bleibt alles`() {
        assertEquals(5, IcsPersonFilter.apply(IcsParser.parse(family, berlin), " ").kept.size)
    }

    @Test
    fun `Handy-Kalender - FamilyWall-Konto Daniel_Sohn und Beschreibung zaehlen`() {
        assertEquals(true, IcsPersonFilter.matches("Daniel", "Arzt", null, listOf("Daniel_Sohn")))
        assertEquals(true, IcsPersonFilter.matches("Daniel", "Arzt", "Teilnehmer: Andrea, Daniel", emptyList()))
        assertEquals(false, IcsPersonFilter.matches("Daniel", "Arzt", null, listOf("Andrea")))
    }
}
