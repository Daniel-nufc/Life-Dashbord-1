package app.lifedashboard.ui.components

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val GERMAN: Locale = Locale.GERMANY

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", GERMAN)

fun LocalTime.hhmm(): String = format(timeFormatter)

fun DayOfWeek.longName(): String = getDisplayName(TextStyle.FULL, GERMAN)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, GERMAN).removeSuffix(".")

/** 95 Minuten → „1 Std 35 Min“. */
fun Duration.pretty(): String {
    val hours = toHours()
    val minutes = toMinutesPart()
    return when {
        hours == 0L -> "$minutes Min"
        minutes == 0 -> "$hours Std"
        else -> "$hours Std $minutes Min"
    }
}

fun minutesToTimeText(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
