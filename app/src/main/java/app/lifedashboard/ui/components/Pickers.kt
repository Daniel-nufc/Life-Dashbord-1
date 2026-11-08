package app.lifedashboard.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

/** Systemeigene Datums-/Uhrzeit-Auswahl (zuverlässig und vertraut). */
fun Context.pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        this,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).apply { datePicker.firstDayOfWeek = Calendar.MONDAY }.show()
}

fun Context.pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(this, { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) }, initial.hour, initial.minute, true).show()
}

private val dayFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", GERMAN)

/** „Fr, 13. Nov.“ – mit „Heute“/„Morgen“, wenn passend. */
fun LocalDate.friendly(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Heute"
    today.plusDays(1) -> "Morgen"
    today.minusDays(1) -> "Gestern"
    else -> format(dayFormatter)
}
