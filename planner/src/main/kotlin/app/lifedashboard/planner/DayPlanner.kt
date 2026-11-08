package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Art eines Vorschlags. */
enum class DemandType { HOMEWORK, STUDY, REVIEW, TASK, ETSY, SPORT }

/** Etwas, das heute Zeit braucht. */
data class Demand(
    /** Stabiler Schlüssel, z. B. „task:12“ – damit Ablehnungen wiedererkannt werden. */
    val key: String,
    val type: DemandType,
    val title: String,
    val minutes: Int,
    /** 0,0 … 1,5 – Grundwichtigkeit */
    val priority: Double,
    val deadline: LocalDate? = null,
    val minChunk: Int = 25,
    val maxChunk: Int = 50,
    val refId: Long? = null,
)

data class PlannedBlock(
    val demandKey: String,
    val type: DemandType,
    val title: String,
    val range: TimeRange,
    val refId: Long?,
)

/**
 * Gelernte Vorlieben: Wie oft wurde ein Typ zu einer Tageszeit angenommen bzw. abgelehnt?
 * Ergebnis ist ein Faktor um 1,0 (0,4 … 1,6).
 */
class PreferenceModel(private val stats: Map<Key, Counts> = emptyMap()) {
    data class Key(val type: DemandType, val weekend: Boolean, val bucket: Int)
    data class Counts(val accepted: Int, val declined: Int)

    fun weight(type: DemandType, date: LocalDate, time: LocalTime): Double {
        val counts = stats[keyOf(type, date, time)] ?: return 1.0
        val ratio = (counts.accepted + 1.0) / (counts.accepted + counts.declined + 2.0) // 0..1, neutral 0,5
        return 0.4 + ratio * 1.2
    }

    companion object {
        /** Tageszeit in 3-Stunden-Blöcken (6–9, 9–12, 12–15, 15–18, 18–21, 21–24). */
        fun bucketOf(time: LocalTime): Int = (time.hour / 3).coerceIn(0, 7)

        fun keyOf(type: DemandType, date: LocalDate, time: LocalTime) =
            Key(type, date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY, bucketOf(time))
    }
}

data class PlannerConfig(
    /** Bereichs-Gewichte (z. B. Klausurphase > Sport > Etsy). */
    val typeWeight: Map<DemandType, Double> = mapOf(
        DemandType.HOMEWORK to 1.2,
        DemandType.STUDY to 1.1,
        DemandType.REVIEW to 1.0,
        DemandType.SPORT to 0.8,
        DemandType.TASK to 0.7,
        DemandType.ETSY to 0.6,
    ),
    val breakMinutes: Int = 5,
    val preferences: PreferenceModel = PreferenceModel(),
)

/**
 * Plant einen Tag: Verteilt Bedarfe auf freie Zeitfenster.
 *
 * - Wichtigstes zuerst (Priorität × Bereichs-Gewicht × Dringlichkeit)
 * - Lange Aufgaben werden in Blöcke (min/max) geteilt, dazwischen Pausen
 * - Jede Art hat Lieblings-Tageszeiten (Lernen nachmittags, Etsy abends) – gelernte Vorlieben verschieben das
 * - Was nicht passt, bleibt übrig (wird später neu versucht, nicht „bestraft“)
 */
object DayPlanner {

    fun plan(
        date: LocalDate,
        freeSlots: List<TimeRange>,
        demands: List<Demand>,
        config: PlannerConfig = PlannerConfig(),
    ): List<PlannedBlock> {
        val segments = freeSlots.sortedBy { it.start }.toMutableList()
        val result = mutableListOf<PlannedBlock>()

        val ordered = demands
            .filter { it.minutes > 0 }
            .sortedByDescending { score(it, date, config) }

        for (demand in ordered) {
            var remaining = demand.minutes
            var guard = 0
            while (remaining > 0 && guard++ < 20) {
                val minNeeded = minOf(demand.minChunk, remaining)
                val candidates = segments.filter { it.duration.toMinutes() >= minNeeded }
                if (candidates.isEmpty()) break
                val best = candidates.maxByOrNull { seg ->
                    timeFit(demand.type, seg.start) * config.preferences.weight(demand.type, date, seg.start) -
                        seg.start.toSecondOfDay() / 86_400.0 * 0.05 // bei Gleichstand früher
                } ?: break
                val chunk = minOf(remaining, demand.maxChunk, best.duration.toMinutes().toInt())
                val range = TimeRange(best.start, best.start.plusMinutes(chunk.toLong()))
                result += PlannedBlock(demand.key, demand.type, demand.title, range, demand.refId)
                remaining -= chunk

                // Segment verkleinern (inkl. Pause danach)
                val index = segments.indexOf(best)
                val newStart = range.end.plusMinutes(config.breakMinutes.toLong())
                if (newStart.isBefore(best.end) && !newStart.isBefore(range.end)) {
                    segments[index] = TimeRange(newStart, best.end)
                } else {
                    segments.removeAt(index)
                }
            }
        }
        return result.sortedBy { it.range.start }
    }

    fun score(demand: Demand, date: LocalDate, config: PlannerConfig): Double {
        val urgency = demand.deadline?.let {
            val days = ChronoUnit.DAYS.between(date, it).coerceAtLeast(0)
            1.0 + 2.0 / (days + 1)
        } ?: 1.0
        return demand.priority * (config.typeWeight[demand.type] ?: 1.0) * urgency
    }

    /** Lieblings-Tageszeiten je Art (Daniel lernt am besten nachmittags). */
    fun timeFit(type: DemandType, time: LocalTime): Double {
        val h = time.hour
        return when (type) {
            DemandType.HOMEWORK, DemandType.STUDY -> when (h) {
                in 15..17 -> 1.4
                in 12..14 -> 1.1
                in 18..19 -> 1.0
                in 20..20 -> 0.7
                in 6..7 -> 0.6
                in 21..23 -> 0.3
                else -> 0.8
            }
            DemandType.REVIEW -> if (h in 21..23) 0.7 else 1.0
            DemandType.ETSY -> when (h) {
                in 18..20 -> 1.3
                in 15..17 -> 1.0
                in 21..21 -> 0.8
                else -> 0.6
            }
            DemandType.SPORT -> when (h) {
                in 16..19 -> 1.3
                in 6..8 -> 0.9
                in 20..23 -> 0.4
                else -> 0.8
            }
            DemandType.TASK -> 1.0
        }
    }

    /** Freie Fenster ab einer Uhrzeit (z. B. „ab jetzt“). */
    fun from(slots: List<TimeRange>, notBefore: LocalTime, minDuration: Duration = Duration.ofMinutes(15)): List<TimeRange> =
        slots.mapNotNull { slot ->
            when {
                !slot.end.isAfter(notBefore) -> null
                slot.start.isBefore(notBefore) -> TimeRange(notBefore, slot.end)
                else -> slot
            }
        }.filter { it.duration >= minDuration }
}
