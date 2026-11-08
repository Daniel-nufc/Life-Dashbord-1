package app.lifedashboard.data.sync

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

data class SystemCalendar(val id: Long, val name: String, val account: String, val color: Int)

/** Termin aus dem Samsung-/Google-Kalender (nur gelesen). */
data class ExternalEvent(
    val id: Long,
    val title: String,
    val date: LocalDate,
    /** null = ganztägig */
    val startMinute: Int?,
    val endMinute: Int?,
    val color: Int,
    val calendarName: String,
)

/** Liest den Handy-Kalender (Calendar Provider). Schreibt nichts. */
@Singleton
class SystemCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(): List<SystemCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val result = mutableListOf<SystemCalendar>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                result += SystemCalendar(c.getLong(0), c.getString(1) ?: "Kalender", c.getString(2) ?: "", c.getInt(3))
            }
        }
        result.sortedBy { it.name.lowercase() }
    }

    /** Alle Termine (inkl. Serien) zwischen [from] und [to] aus den gewählten Kalendern. */
    suspend fun events(from: LocalDate, to: LocalDate, calendarIds: Set<Long>): List<ExternalEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission() || calendarIds.isEmpty()) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val result = mutableListOf<ExternalEvent>()
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(7) !in calendarIds) continue
                val allDay = c.getInt(4) == 1
                if (allDay) {
                    // Ganztägige Termine sind in UTC gespeichert
                    val day = Instant.ofEpochMilli(c.getLong(2)).atZone(ZoneOffset.UTC).toLocalDate()
                    result += ExternalEvent(c.getLong(0), c.getString(1) ?: "(ohne Titel)", day, null, null, c.getInt(5), c.getString(6) ?: "")
                } else {
                    val s = Instant.ofEpochMilli(c.getLong(2)).atZone(zone)
                    val e = Instant.ofEpochMilli(c.getLong(3)).atZone(zone)
                    val endMinute = if (e.toLocalDate() != s.toLocalDate()) 24 * 60 - 1 else e.hour * 60 + e.minute
                    result += ExternalEvent(
                        c.getLong(0), c.getString(1) ?: "(ohne Titel)", s.toLocalDate(),
                        s.hour * 60 + s.minute, endMinute.coerceAtLeast(s.hour * 60 + s.minute), c.getInt(5), c.getString(6) ?: "",
                    )
                }
            }
        }
        result
    }

    /** Meldet sich, wenn sich im Handy-Kalender etwas ändert. */
    fun changes(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        if (!hasPermission()) {
            awaitClose { }
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
