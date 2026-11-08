package app.lifedashboard.planner

import java.time.LocalDate

/**
 * Berliner Schulferien und Feiertage (Schuljahr 2026/27, dazu Herbst/Weihnachten 2027 vorläufig).
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
    )
}

/** Schulfrei? (Ferien, Feiertag, eigener freier Tag – oder Wochenende) */
fun isSchoolFree(date: LocalDate, freeRanges: List<DateRange>): Boolean =
    date.dayOfWeek.value >= 6 || freeRanges.any { date in it }
