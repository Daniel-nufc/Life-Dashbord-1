package app.lifedashboard.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Nachgebauter Android-Kalender für Tests – so wie die FamilyWall-App ihn befüllt:
 * keine Teilnehmer, keine eigenen Farben, nur eine interne Nummer in SYNC_DATA1.
 */
class FakeCalendarProvider : ContentProvider() {

    data class Cal(val id: Long, val name: String, val account: String, val color: Int = 0xFF67740F.toInt())

    data class Ev(
        val id: Long,
        val calendarId: Long,
        val title: String,
        val date: LocalDate,
        /** null = ganztägig */
        val start: LocalTime? = null,
        val end: LocalTime? = null,
        val days: Int = 1,
    ) {
        fun begin(zone: ZoneId): Long =
            if (start == null) date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else date.atTime(start).atZone(zone).toInstant().toEpochMilli()

        fun end(zone: ZoneId): Long =
            if (start == null) date.plusDays(days.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            else date.atTime(end ?: start.plusHours(1)).atZone(zone).toInstant().toEpochMilli()
    }

    companion object {
        val calendars = mutableListOf<Cal>()
        val events = mutableListOf<Ev>()

        fun reset() {
            calendars.clear()
            events.clear()
        }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cols = projection ?: arrayOf()
        val cursor = MatrixCursor(cols)
        val zone = ZoneId.systemDefault()
        val path = uri.pathSegments
        when (path.firstOrNull()) {
            "calendars" -> calendars.forEach { c ->
                cursor.addRow(cols.map { col ->
                    when (col) {
                        CalendarContract.Calendars._ID -> c.id
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME -> c.name
                        CalendarContract.Calendars.ACCOUNT_NAME -> c.account
                        CalendarContract.Calendars.CALENDAR_COLOR -> c.color
                        else -> null
                    }
                }.toTypedArray())
            }
            "instances" -> {
                val begin = path[2].toLong()
                val end = path[3].toLong()
                events.filter { it.end(zone) > begin && it.begin(zone) < end }.sortedBy { it.begin(zone) }.forEach { e ->
                    val cal = calendars.first { it.id == e.calendarId }
                    cursor.addRow(cols.map { col ->
                        when (col) {
                            CalendarContract.Instances.EVENT_ID -> e.id
                            CalendarContract.Instances.TITLE -> e.title
                            CalendarContract.Instances.BEGIN -> e.begin(zone)
                            CalendarContract.Instances.END -> e.end(zone)
                            CalendarContract.Instances.ALL_DAY -> if (e.start == null) 1 else 0
                            CalendarContract.Instances.DISPLAY_COLOR -> cal.color
                            CalendarContract.Instances.CALENDAR_DISPLAY_NAME -> cal.name
                            CalendarContract.Instances.CALENDAR_ID -> e.calendarId
                            CalendarContract.Instances.DESCRIPTION -> ""
                            else -> null
                        }
                    }.toTypedArray())
                }
            }
            "events" -> events.forEach { e ->
                cursor.addRow(cols.map { col ->
                    when (col) {
                        CalendarContract.Events._ID -> e.id
                        CalendarContract.Events.SYNC_DATA1 -> (1_790_000_000_000L + e.id).toString()
                        else -> null
                    }
                }.toTypedArray())
            }
            else -> Unit // Teilnehmer, Zusatz-Eigenschaften: FamilyWall schreibt keine
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
