package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate

/** Ein Wochentermin eines Fachs. [weekType] = null bedeutet „jede Woche“. */
data class WeeklySlot(val day: DayOfWeek, val weekType: WeekType? = null)

/**
 * Findet das nächste Datum NACH [after], an dem eines der [slots] stattfindet.
 * Typischer Fall: Hausaufgabe heute bekommen → fällig zur nächsten Stunde des Fachs.
 *
 * @param skip 0 = nächste Stunde, 1 = übernächste Stunde, …
 * @return null, wenn innerhalb von [horizonDays] keine Stunde gefunden wurde
 */
fun nextOccurrence(
    after: LocalDate,
    slots: List<WeeklySlot>,
    weekTypeOf: (LocalDate) -> WeekType,
    skip: Int = 0,
    horizonDays: Int = 60,
    /** false für Ferien und Feiertage – dort findet kein Unterricht statt */
    isSchoolDay: (LocalDate) -> Boolean = { true },
): LocalDate? {
    if (slots.isEmpty()) return null
    var remaining = skip
    for (offset in 1..horizonDays) {
        val date = after.plusDays(offset.toLong())
        if (!isSchoolDay(date)) continue
        val matches = slots.any { slot ->
            slot.day == date.dayOfWeek && (slot.weekType == null || slot.weekType == weekTypeOf(date))
        }
        if (matches) {
            if (remaining == 0) return date
            remaining--
        }
    }
    return null
}
