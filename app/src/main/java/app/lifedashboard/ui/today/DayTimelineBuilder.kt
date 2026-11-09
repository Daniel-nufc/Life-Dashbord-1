package app.lifedashboard.ui.today

import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.planner.DemandType
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.repository.LessonItem
import app.lifedashboard.data.sync.ExternalEvent
import app.lifedashboard.planner.TimeRange
import app.lifedashboard.planner.freeSlots
import java.time.Duration
import java.time.LocalTime

enum class EntryKind { LESSON, ROUTINE, COMMUTE, EVENT, JOB, PLAN, FREE }

data class TimelineEntry(
    val range: TimeRange,
    val title: String,
    val subtitle: String?,
    val kind: EntryKind,
    val colorArgb: Long? = null,
    val area: LifeArea? = null,
    /** ID des Termins/der Schicht, damit man ihn antippen und bearbeiten kann. */
    val refId: Long? = null,
    /** Gesetzt bei Vorschlägen aus dem Tagesplan. */
    val plan: PlanBlockEntity? = null,
)

/**
 * Baut den Tagesablauf aus festen Blöcken (Unterricht, Wege, Training, Termine, Job) und
 * berechnet daraus die freien Zeitfenster. Der PlanningEngine füllt diese Fenster mit Vorschlägen.
 */
object DayTimelineBuilder {

    fun build(
        lessons: List<LessonItem>,
        routines: List<RoutineBlockEntity>,
        dayStart: LocalTime,
        dayEnd: LocalTime,
        commuteMinutes: Int,
        events: List<EventEntity> = emptyList(),
        shifts: List<JobShiftEntity> = emptyList(),
        externalEvents: List<ExternalEvent> = emptyList(),
        planBlocks: List<PlanBlockEntity> = emptyList(),
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
                area = LifeArea.SCHOOL,
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
                area = block.area,
                refId = block.id,
            )
        }

        events.forEach { event ->
            val start = event.startMinute ?: return@forEach // ganztägige Termine blockieren keine Zeit
            val end = (event.endMinute ?: (start + 60)).coerceAtLeast(start)
            fixed += TimelineEntry(
                range = TimeRange.ofMinutes(start, end),
                title = event.title,
                subtitle = listOfNotNull(event.location, event.note).joinToString(" · ").ifBlank { null },
                kind = EntryKind.EVENT,
                area = event.area,
                refId = event.id,
            )
        }

        externalEvents.forEach { event ->
            val start = event.startMinute ?: return@forEach
            fixed += TimelineEntry(
                range = TimeRange.ofMinutes(start, (event.endMinute ?: (start + 60)).coerceAtLeast(start)),
                title = event.title,
                subtitle = event.calendarName.ifBlank { null },
                kind = EntryKind.EVENT,
                colorArgb = event.color.toLong() and 0xFFFFFFFFL,
                area = LifeArea.PRIVATE,
            )
        }

        shifts.forEach { shift ->
            fixed += TimelineEntry(
                range = TimeRange.ofMinutes(shift.startMinute, shift.endMinute.coerceAtLeast(shift.startMinute)),
                title = "Job",
                subtitle = shift.note,
                kind = EntryKind.JOB,
                area = LifeArea.JOB,
                refId = shift.id,
            )
        }

        planBlocks.filter { it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED || it.status == PlanStatus.DONE }
            .forEach { block ->
                fixed += TimelineEntry(
                    range = TimeRange.ofMinutes(block.startMinute, block.endMinute),
                    title = block.title,
                    subtitle = when (block.status) {
                        PlanStatus.PROPOSED -> "Vorschlag"
                        PlanStatus.ACCEPTED -> "Eingeplant"
                        else -> "Erledigt ✓"
                    },
                    kind = EntryKind.PLAN,
                    area = block.type.area(),
                    refId = block.id,
                    plan = block,
                )
            }

        if (!dayEnd.isAfter(dayStart)) return fixed.sortedBy { it.range.start }
        val window = TimeRange(dayStart, dayEnd)
        val free = freeSlots(window, fixed.map { it.range }, Duration.ofMinutes(minFreeMinutes)).map {
            TimelineEntry(it, "Freie Zeit", null, EntryKind.FREE)
        }

        return (fixed + free).sortedWith(compareBy({ it.range.start }, { it.range.end }))
    }
}

fun DemandType.area(): LifeArea = when (this) {
    DemandType.HOMEWORK, DemandType.STUDY, DemandType.REVIEW -> LifeArea.SCHOOL
    DemandType.ETSY -> LifeArea.ETSY
    DemandType.SPORT -> LifeArea.SPORT
    DemandType.TASK -> LifeArea.PRIVATE
}
