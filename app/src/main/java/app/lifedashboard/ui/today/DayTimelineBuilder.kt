package app.lifedashboard.ui.today

import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.repository.LessonItem
import app.lifedashboard.planner.TimeRange
import app.lifedashboard.planner.freeSlots
import java.time.Duration
import java.time.LocalTime

enum class EntryKind { LESSON, ROUTINE, COMMUTE, FREE }

data class TimelineEntry(
    val range: TimeRange,
    val title: String,
    val subtitle: String?,
    val kind: EntryKind,
    val colorArgb: Long? = null,
)

/**
 * Baut den Tagesablauf aus festen Blöcken (Unterricht, Schulweg, Training) und berechnet
 * daraus die freien Zeitfenster. In Phase 4 füllt der Planer diese Fenster mit Vorschlägen.
 */
object DayTimelineBuilder {

    fun build(
        lessons: List<LessonItem>,
        routines: List<RoutineBlockEntity>,
        dayStart: LocalTime,
        dayEnd: LocalTime,
        commuteMinutes: Int,
        minFreeMinutes: Long = 30,
    ): List<TimelineEntry> {
        val fixed = mutableListOf<TimelineEntry>()

        lessons.forEach { item ->
            val range = item.timeRange ?: return@forEach
            fixed += TimelineEntry(
                range = range,
                title = item.subject.name,
                subtitle = listOf(item.subject.code, item.lesson.room).filter { it.isNotBlank() }.joinToString(" · "),
                kind = EntryKind.LESSON,
                colorArgb = item.subject.colorArgb,
            )
        }

        val schoolRanges = lessons.mapNotNull { it.timeRange }
        if (schoolRanges.isNotEmpty() && commuteMinutes > 0) {
            val first = schoolRanges.minOf { it.start }
            val last = schoolRanges.maxOf { it.end }
            val commute = commuteMinutes.toLong()
            if (first.minusMinutes(commute).isBefore(first)) {
                fixed += TimelineEntry(TimeRange(first.minusMinutes(commute), first), "Schulweg", null, EntryKind.COMMUTE)
            }
            if (last.plusMinutes(commute).isAfter(last)) {
                fixed += TimelineEntry(TimeRange(last, last.plusMinutes(commute)), "Heimweg", null, EntryKind.COMMUTE)
            }
        }

        routines.forEach { block ->
            fixed += TimelineEntry(
                range = TimeRange.ofMinutes(block.startMinute, block.endMinute),
                title = block.title,
                subtitle = listOfNotNull(block.location, block.note).joinToString(" · ").ifBlank { null },
                kind = EntryKind.ROUTINE,
            )
        }

        val window = TimeRange(dayStart, dayEnd)
        val free = freeSlots(window, fixed.map { it.range }, Duration.ofMinutes(minFreeMinutes)).map {
            TimelineEntry(it, "Freie Zeit", null, EntryKind.FREE)
        }

        return (fixed + free).sortedWith(compareBy({ it.range.start }, { it.range.end }))
    }
}
