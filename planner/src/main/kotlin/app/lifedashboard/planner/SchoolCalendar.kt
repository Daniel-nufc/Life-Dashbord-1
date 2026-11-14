package app.lifedashboard.planner

import java.time.LocalDate

/**
 * Berliner Schulferien und Feiertage (Schuljahr 2026/27, dazu 2027/28 und Herbst/Weihnachten 2028 vorläufig –
 * Stand KMK-Übersicht, abgeglichen mit kalenderpedia.de am 14.11.2026).
 * Quelle: Ferienordnung der Senatsverwaltung, abgeglichen über Ferienkalender – ohne Gewähr,
 * in der App änderbar. Zusätzliche schulfreie Tage (Studientage) trägt man selbst ein.
 */
object BerlinSchoolCalendar {
    data class Entry(val name: String, val range: DateRange, val publicHoliday: Boolean)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun holiday(name: String, from: LocalDate, to: LocalDate) = Entry(name, DateRange(from, to), false)
    private fun feiertag(name: String, date: LocalDate) = Entry(name, DateRange(date, date), true)

    val entries: List<Entry> = listOf(
        holiday("Sommerferien 2026", d(2026, 7, 9), d(2026, 8, 22)),
        holiday("Herbstferien 2026", d(2026, 10, 19), d(2026, 10, 31)),
        holiday("Weihnachtsferien 2026/27", d(2026, 12, 23), d(2027, 1, 2)),
        holiday("Winterferien 2027", d(2027, 2, 1), d(2027, 2, 6)),
        holiday("Osterferien 2027", d(2027, 3, 22), d(2027, 4, 2)),
        holiday("Sommerferien 2027", d(2027, 7, 1), d(2027, 8, 14)),
        holiday("Herbstferien 2027 (vorläufig)", d(2027, 10, 11), d(2027, 10, 23)),
        holiday("Weihnachtsferien 2027/28 (vorläufig)", d(2027, 12, 22), d(2027, 12, 31)),
        holiday("Winterferien 2028 (vorläufig)", d(2028, 1, 31), d(2028, 2, 5)),
        holiday("Osterferien 2028 (vorläufig)", d(2028, 4, 10), d(2028, 4, 22)),
        holiday("Freitag nach Himmelfahrt 2028 (vorläufig)", d(2028, 5, 26), d(2028, 5, 26)),
        holiday("Pfingstferien 2028 (vorläufig)", d(2028, 6, 1), d(2028, 6, 2)),
        holiday("Sommerferien 2028 (vorläufig)", d(2028, 7, 1), d(2028, 8, 12)),
        holiday("Herbstferien 2028 (vorläufig)", d(2028, 10, 2), d(2028, 10, 14)),
        holiday("Weihnachtsferien 2028/29 (vorläufig)", d(2028, 12, 22), d(2029, 1, 2)),
        feiertag("Tag der Deutschen Einheit", d(2026, 10, 3)),
        feiertag("1. Weihnachtstag", d(2026, 12, 25)),
        feiertag("2. Weihnachtstag", d(2026, 12, 26)),
        feiertag("Neujahr", d(2027, 1, 1)),
        feiertag("Internationaler Frauentag", d(2027, 3, 8)),
        feiertag("Karfreitag", d(2027, 3, 26)),
        feiertag("Ostermontag", d(2027, 3, 29)),
        feiertag("Tag der Arbeit", d(2027, 5, 1)),
        feiertag("Christi Himmelfahrt", d(2027, 5, 6)),
        feiertag("Pfingstmontag", d(2027, 5, 17)),
        feiertag("Tag der Deutschen Einheit", d(2027, 10, 3)),
        feiertag("1. Weihnachtstag", d(2027, 12, 25)),
        feiertag("2. Weihnachtstag", d(2027, 12, 26)),
        feiertag("Neujahr", d(2028, 1, 1)),
        feiertag("Internationaler Frauentag", d(2028, 3, 8)),
        feiertag("Karfreitag", d(2028, 4, 14)),
        feiertag("Ostermontag", d(2028, 4, 17)),
        feiertag("Tag der Arbeit", d(2028, 5, 1)),
        feiertag("Christi Himmelfahrt", d(2028, 5, 25)),
        feiertag("Pfingstmontag", d(2028, 6, 5)),
        feiertag("Einmaliger Feiertag (17. Juni)", d(2028, 6, 17)),
        feiertag("Tag der Deutschen Einheit", d(2028, 10, 3)),
        feiertag("1. Weihnachtstag", d(2028, 12, 25)),
        feiertag("2. Weihnachtstag", d(2028, 12, 26)),
    )

    /** Letzter Tag, für den Ferien hinterlegt sind – danach muss das neue Schuljahr eingetragen werden. */
    fun coveredUntil(entries: List<DateRange>): LocalDate? = entries.maxOfOrNull { it.endInclusive }
}

/** Schulfrei? (Ferien, Feiertag, eigener freier Tag – oder Wochenende) */
fun isSchoolFree(date: LocalDate, freeRanges: List<DateRange>): Boolean =
    date.dayOfWeek.value >= 6 || freeRanges.any { date in it }
