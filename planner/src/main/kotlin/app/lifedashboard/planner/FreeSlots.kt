package app.lifedashboard.planner

import java.time.Duration

/**
 * Fügt überlappende oder direkt aneinander grenzende Zeiträume zusammen.
 * Beispiel: 08:00–09:30 + 09:30–10:00 → 08:00–10:00
 */
fun mergeRanges(ranges: List<TimeRange>): List<TimeRange> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.start }
    val result = mutableListOf(sorted.first())
    for (range in sorted.drop(1)) {
        val last = result.last()
        if (!range.start.isAfter(last.end)) {
            result[result.lastIndex] = TimeRange(last.start, maxOf(last.end, range.end))
        } else {
            result += range
        }
    }
    return result
}

/**
 * Berechnet die freien Zeitfenster eines Tages.
 *
 * @param window   Tagesfenster, in dem geplant werden darf (z. B. 06:00–22:00)
 * @param busy     belegte Zeiten (Unterricht, Termine, Training, Wege …)
 * @param minDuration Fenster, die kürzer sind, werden ignoriert (z. B. 20-Minuten-Pausen)
 */
fun freeSlots(
    window: TimeRange,
    busy: List<TimeRange>,
    minDuration: Duration = Duration.ofMinutes(30),
): List<TimeRange> {
    val relevant = busy
        .filter { it.overlaps(window) }
        .map { TimeRange(maxOf(it.start, window.start), minOf(it.end, window.end)) }
    val merged = mergeRanges(relevant)

    val free = mutableListOf<TimeRange>()
    var cursor = window.start
    for (block in merged) {
        if (block.start.isAfter(cursor)) free += TimeRange(cursor, block.start)
        if (block.end.isAfter(cursor)) cursor = block.end
    }
    if (window.end.isAfter(cursor)) free += TimeRange(cursor, window.end)

    return free.filter { it.duration >= minDuration }
}

/**
 * Hofpausen: Lücken zwischen zwei Unterrichtsstunden, die kürzer als [maxBreakMinutes] sind.
 * Sie gelten als belegt (nie verplant). Längere Lücken (Freistunden) bleiben frei.
 */
fun schoolBreaks(lessons: List<TimeRange>, maxBreakMinutes: Long = 60): List<TimeRange> {
    val sorted = lessons.sortedBy { it.start }
    return sorted.zipWithNext().mapNotNull { (a, b) ->
        val gap = Duration.between(a.end, b.start)
        if (!gap.isNegative && !gap.isZero && gap.toMinutes() < maxBreakMinutes) TimeRange(a.end, b.start) else null
    }
}
