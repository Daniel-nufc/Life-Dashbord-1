package app.lifedashboard.planner

import java.time.Duration
import java.time.LocalTime

/** Ein Zeitraum innerhalb eines Tages, z. B. 08:00–09:30. Ende ist exklusiv. */
data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init {
        require(!end.isBefore(start)) { "Ende ($end) liegt vor Start ($start)" }
    }

    val duration: Duration get() = Duration.between(start, end)

    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end

    operator fun contains(time: LocalTime): Boolean = !time.isBefore(start) && time.isBefore(end)

    override fun toString(): String = "$start–$end"

    companion object {
        fun of(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
            TimeRange(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute))

        fun ofMinutes(startMinuteOfDay: Int, endMinuteOfDay: Int) =
            TimeRange(minuteToTime(startMinuteOfDay), minuteToTime(endMinuteOfDay))
    }
}

/** Minuten seit Mitternacht → LocalTime (24:00 wird als 23:59 dargestellt). */
fun minuteToTime(minuteOfDay: Int): LocalTime {
    val clamped = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(clamped / 60, clamped % 60)
}

fun LocalTime.minuteOfDay(): Int = hour * 60 + minute
