package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class WeekType {
    A, B;

    fun other(): WeekType = if (this == A) B else A
}

/** Ein Datumsbereich inkl. Start- und Endtag, z. B. Ferien. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)
}

fun LocalDate.mondayOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Bestimmt, ob ein Datum in einer A- oder B-Woche liegt.
 *
 * Grundlage ist eine Referenzwoche (z. B. KW 46/2026 = A). Danach wechseln die Wochen ab.
 * Optional zählen reine Ferienwochen nicht mit (manche Schulen machen nach den Ferien
 * mit der Woche weiter, die vor den Ferien „dran“ gewesen wäre).
 */
class SchoolWeekCalculator(
    referenceDate: LocalDate,
    private val referenceType: WeekType,
    private val holidays: List<DateRange> = emptyList(),
    private val skipHolidayWeeks: Boolean = false,
) {
    private val referenceMonday = referenceDate.mondayOfWeek()

    fun weekTypeFor(date: LocalDate): WeekType {
        val monday = date.mondayOfWeek()
        var weeks = ChronoUnit.WEEKS.between(referenceMonday, monday)
        if (skipHolidayWeeks && weeks != 0L) {
            weeks -= countHolidayWeeksBetween(referenceMonday, monday)
        }
        return if (Math.floorMod(weeks, 2L) == 0L) referenceType else referenceType.other()
    }

    /** Für die Planung am Wochenende ist meistens schon die kommende Woche interessant. */
    fun weekTypeForPlanning(date: LocalDate): WeekType {
        val target = if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            date.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        } else {
            date
        }
        return weekTypeFor(target)
    }

    /** Eine Woche gilt als Ferienwoche, wenn Montag bis Freitag komplett in Ferien liegen. */
    fun isHolidayWeek(monday: LocalDate): Boolean =
        (0L..4L).all { offset -> holidays.any { monday.plusDays(offset) in it } }

    /**
     * Zählt Ferienwochen zwischen Referenz- und Zielwoche (mit Vorzeichen):
     * vorwärts die Montage in (from, to], rückwärts die Montage in [to, from).
     */
    private fun countHolidayWeeksBetween(from: LocalDate, to: LocalDate): Long {
        val forward = to.isAfter(from)
        var monday = if (forward) from.plusWeeks(1) else to
        val lastInclusive = if (forward) to else from.minusWeeks(1)
        var count = 0L
        while (!monday.isAfter(lastInclusive)) {
            if (isHolidayWeek(monday)) count++
            monday = monday.plusWeeks(1)
        }
        return if (forward) count else -count
    }
}
