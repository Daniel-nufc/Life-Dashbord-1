package app.lifedashboard.planner

import java.time.LocalDate

/** Ein Tag im Planungszeitraum: wie viel Zeit realistisch frei ist und was schon darauf liegt. */
data class BalanceDay(val date: LocalDate, val capacity: Int, val load: Int)

/** Etwas, das bis zu einem Termin erledigt sein soll (z. B. eine Hausaufgabe). */
data class BalanceItem(
    val key: String,
    val minutes: Int,
    /** Letzter Tag, an dem es noch sinnvoll ist (null = egal) */
    val deadline: LocalDate?,
    /** Größter Block am Stück */
    val chunk: Int = 50,
)

/**
 * Verteilt Aufgaben gleichmäßig auf die Tage bis zu ihrem Termin.
 *
 * Statt alles so früh wie möglich zu stapeln, landet jeder Block auf dem Tag, der danach
 * (im Verhältnis zu seiner freien Zeit) am wenigsten ausgelastet ist. Dringendes zuerst,
 * bei Gleichstand der frühere Tag.
 */
object LoadBalancer {

    fun assign(days: List<BalanceDay>, items: List<BalanceItem>): Map<String, Map<LocalDate, Int>> {
        if (days.isEmpty()) return emptyMap()
        val sorted = days.sortedBy { it.date }
        val load = sorted.associate { it.date to it.load }.toMutableMap()
        val result = linkedMapOf<String, MutableMap<LocalDate, Int>>()

        for (item in items.filter { it.minutes > 0 }.sortedWith(compareBy({ it.deadline ?: LocalDate.MAX }, { it.key }))) {
            val candidates = sorted.filter { item.deadline == null || !it.date.isAfter(item.deadline) }.ifEmpty { listOf(sorted.first()) }
            var remaining = item.minutes
            while (remaining > 0) {
                // kein Mini-Rest: 55 Min werden nicht 50 + 5, sondern ein Block
                val unit = if (remaining <= item.chunk + 10) remaining else item.chunk
                val withRoom = candidates.filter { it.capacity - (load[it.date] ?: 0) >= unit }
                val day = if (withRoom.isNotEmpty()) {
                    withRoom.withIndex().minByOrNull { (index, d) ->
                        ((load[d.date] ?: 0) + unit).toDouble() / d.capacity.coerceAtLeast(1) + index * 0.02
                    }!!.value
                } else {
                    // nirgends genug Platz → dorthin, wo am meisten Luft ist
                    candidates.maxByOrNull { it.capacity - (load[it.date] ?: 0) }!!
                }
                result.getOrPut(item.key) { mutableMapOf() }.merge(day.date, unit, Int::plus)
                load[day.date] = (load[day.date] ?: 0) + unit
                remaining -= unit
            }
        }
        return result
    }
}
