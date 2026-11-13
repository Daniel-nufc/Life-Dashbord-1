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

data class SystemCalendar(val id: Long, val name: String, val account: String, val color: Int) {
    /** Der Familienkalender, den die FamilyWall-App selbst ins Handy überträgt. */
    val isFamilyWall: Boolean get() = name.contains("familywall", ignoreCase = true) || account.contains("familywall", ignoreCase = true)
}

/** „Nur meine Termine“ für einen Kalender: wie viele Termine passen und wer im Kalender vorkommt. */
data class PersonStats(val kept: Int, val total: Int, val people: Set<String>, val openTitles: Int = 0)

/** Ein Termin-Titel aus dem Familienkalender zum Zuordnen (alle Termine mit gleichem Titel zusammen). */
data class TitleItem(val title: String, val dates: List<LocalDate>, val choice: Boolean?, val guess: Boolean?, val autoMine: Boolean) {
    /** Zählt als meins: selbst zugeordnet, sonst geraten (bekannte Wörter), sonst am Namen erkannt. */
    val mine: Boolean get() = choice ?: guess ?: autoMine
    /** Weder zugeordnet noch erkannt → die App fragt nach. */
    val open: Boolean get() = choice == null && guess == null && !autoMine
}

/** meins? – eigene Wahl, sonst geraten, sonst am Namen erkannt. */
private fun decide(title: String, autoMine: Boolean, mineTitles: Set<String>, notMineTitles: Set<String>): Boolean =
    app.lifedashboard.planner.IcsPersonFilter.choice(title, mineTitles, notMineTitles)
        ?: app.lifedashboard.planner.IcsPersonFilter.guess(title, mineTitles, notMineTitles)
        ?: autoMine

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
    /** Ganztägiger eigener Termin (z. B. Schülerreise, Spieltag): reservierter Tag, nur Dringendes wird geplant. */
    val blocksDay: Boolean = false,
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

    /**
     * Alle Termine (inkl. Serien) zwischen [from] und [to] aus den gewählten Kalendern.
     * Aus Kalendern in [onlyMineIds] kommen nur Termine, bei denen [name] Teilnehmer ist oder im Titel/in der Beschreibung steht.
     */
    suspend fun events(
        from: LocalDate,
        to: LocalDate,
        calendarIds: Set<Long>,
        onlyMineIds: Set<Long> = emptySet(),
        name: String = "",
        mineTitles: Set<String> = emptySet(),
        notMineTitles: Set<String> = emptySet(),
    ): List<ExternalEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission() || calendarIds.isEmpty()) return@withContext emptyList()
        val rows = instances(from, to, calendarIds)
        val filtered = rows.filter { it.calendarId in onlyMineIds }
        val autoMine = mineOf(filtered, name)
        rows.mapNotNull { row ->
            when {
                row.calendarId !in onlyMineIds -> row.event
                decide(row.event.title, row.event.id in autoMine, mineTitles, notMineTitles) ->
                    row.event.copy(blocksDay = row.event.startMinute == null)
                else -> null
            }
        }
    }

    /** Termin-Titel der nächsten [days] Tage zum Zuordnen („Gehören die dir?“). */
    suspend fun titles(calendarId: Long, name: String, mineTitles: Set<String>, notMineTitles: Set<String>, days: Long = 31): List<TitleItem> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val today = LocalDate.now()
            val rows = instances(today, today.plusDays(days - 1), setOf(calendarId))
            val autoMine = mineOf(rows, name)
            rows.groupBy { app.lifedashboard.planner.IcsPersonFilter.titleKey(it.event.title) }.values.map { group ->
                val first = group.first().event
                TitleItem(
                    title = first.title,
                    dates = group.map { it.event.date }.distinct().sorted(),
                    choice = app.lifedashboard.planner.IcsPersonFilter.choice(first.title, mineTitles, notMineTitles),
                    guess = app.lifedashboard.planner.IcsPersonFilter.guess(first.title, mineTitles, notMineTitles),
                    autoMine = group.any { it.event.id in autoMine },
                )
            }.sortedBy { it.dates.first() }
        }

    /** Event-IDs der Termine, die zu [name] gehören (Titel, Beschreibung, Teilnehmer, versteckte Felder der Kalender-App). */
    private fun mineOf(rows: List<Row>, name: String): Set<Long> {
        if (rows.isEmpty()) return emptySet()
        val ids = rows.map { it.event.id }.toSet()
        val fields = fieldsOf(ids)
        // je Termin (nicht je Wiederholung) einmal bewerten
        val unique = rows.distinctBy { it.event.id }
        val candidates = unique.map { app.lifedashboard.planner.IcsPersonFilter.Candidate(it.event.title, it.description, fields[it.event.id].orEmpty()) }
        return app.lifedashboard.planner.IcsPersonFilter.mine(name, candidates).map { unique[it].event.id }.toSet()
    }

    /** Teilnehmer, versteckte Sync-Felder und Zusatz-Eigenschaften je Termin. */
    private fun fieldsOf(eventIds: Set<Long>): Map<Long, Map<String, List<String>>> {
        val result = mutableMapOf<Long, MutableMap<String, MutableList<String>>>()
        fun add(id: Long, key: String, value: String?) {
            if (value.isNullOrBlank()) return
            result.getOrPut(id) { mutableMapOf() }.getOrPut(key) { mutableListOf() } += value
        }
        attendeesOf(eventIds).forEach { (id, names) -> names.forEach { add(id, "Teilnehmer", it) } }
        val sync = arrayOf(
            CalendarContract.Events.SYNC_DATA1, CalendarContract.Events.SYNC_DATA2, CalendarContract.Events.SYNC_DATA3,
            CalendarContract.Events.SYNC_DATA4, CalendarContract.Events.SYNC_DATA5, CalendarContract.Events.SYNC_DATA6,
            CalendarContract.Events.SYNC_DATA7, CalendarContract.Events.SYNC_DATA8, CalendarContract.Events.SYNC_DATA9,
            CalendarContract.Events.SYNC_DATA10, CalendarContract.Events.CUSTOM_APP_URI, CalendarContract.Events.EVENT_LOCATION,
        )
        eventIds.chunked(400).forEach { chunk ->
            val inIds = " IN (" + chunk.joinToString(",") + ")"
            runCatching {
                context.contentResolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID) + sync, CalendarContract.Events._ID + inIds, null, null)?.use { c ->
                    while (c.moveToNext()) sync.forEachIndexed { i, col -> add(c.getLong(0), col, runCatching { c.getString(i + 1) }.getOrNull()) }
                }
            }
            runCatching {
                val projection = arrayOf(CalendarContract.ExtendedProperties.EVENT_ID, CalendarContract.ExtendedProperties.NAME, CalendarContract.ExtendedProperties.VALUE)
                context.contentResolver.query(CalendarContract.ExtendedProperties.CONTENT_URI, projection, CalendarContract.ExtendedProperties.EVENT_ID + inIds, null, null)?.use { c ->
                    while (c.moveToNext()) add(c.getLong(0), "Zusatz:" + (c.getString(1) ?: "?"), c.getString(2))
                }
            }
        }
        return result
    }

    /** Auswertung „Nur meine Termine“ für einen Kalender in den nächsten [days] Tagen (Anzeige unter Kalender & Abos). */
    suspend fun personStats(
        calendarId: Long,
        name: String,
        mineTitles: Set<String> = emptySet(),
        notMineTitles: Set<String> = emptySet(),
        days: Long = 31,
    ): PersonStats = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext PersonStats(0, 0, emptySet())
        val today = LocalDate.now()
        val rows = instances(today, today.plusDays(days - 1), setOf(calendarId))
        val autoMine = mineOf(rows, name)
        val attendees = attendeesOf(rows.map { it.event.id }.toSet())
        val f = app.lifedashboard.planner.IcsPersonFilter
        PersonStats(
            kept = rows.count { decide(it.event.title, it.event.id in autoMine, mineTitles, notMineTitles) },
            total = rows.size,
            people = attendees.values.flatten().toSortedSet(String.CASE_INSENSITIVE_ORDER),
            openTitles = rows.filter {
                f.choice(it.event.title, mineTitles, notMineTitles) == null && f.guess(it.event.title, mineTitles, notMineTitles) == null &&
                    it.event.id !in autoMine
            }.map { f.titleKey(it.event.title) }.distinct().size,
        )
    }

    private data class Row(val event: ExternalEvent, val calendarId: Long, val description: String?)

    /** Teilnehmer je Termin (Name, sonst E-Mail vor dem @). */
    private fun attendeesOf(eventIds: Set<Long>): Map<Long, List<String>> {
        if (eventIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, MutableList<String>>()
        eventIds.chunked(400).forEach { chunk ->
            val projection = arrayOf(CalendarContract.Attendees.EVENT_ID, CalendarContract.Attendees.ATTENDEE_NAME, CalendarContract.Attendees.ATTENDEE_EMAIL)
            val selection = CalendarContract.Attendees.EVENT_ID + " IN (" + chunk.joinToString(",") + ")"
            runCatching {
                context.contentResolver.query(CalendarContract.Attendees.CONTENT_URI, projection, selection, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val who = c.getString(1)?.takeIf { it.isNotBlank() } ?: c.getString(2)?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: continue
                        result.getOrPut(c.getLong(0)) { mutableListOf() } += who
                    }
                }
            }
        }
        return result
    }

    private fun instances(from: LocalDate, to: LocalDate, calendarIds: Set<Long>): List<Row> {
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
            CalendarContract.Instances.DESCRIPTION,
        )
        val result = mutableListOf<Row>()
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(7) !in calendarIds) continue
                val allDay = c.getInt(4) == 1
                if (allDay) {
                    // Ganztägige Termine sind in UTC gespeichert
                    val day = Instant.ofEpochMilli(c.getLong(2)).atZone(ZoneOffset.UTC).toLocalDate()
                    result += Row(ExternalEvent(c.getLong(0), c.getString(1) ?: "(ohne Titel)", day, null, null, c.getInt(5), c.getString(6) ?: ""), c.getLong(7), c.getString(8))
                } else {
                    val s = Instant.ofEpochMilli(c.getLong(2)).atZone(zone)
                    val e = Instant.ofEpochMilli(c.getLong(3)).atZone(zone)
                    val endMinute = if (e.toLocalDate() != s.toLocalDate()) 24 * 60 - 1 else e.hour * 60 + e.minute
                    result += Row(
                        ExternalEvent(
                            c.getLong(0), c.getString(1) ?: "(ohne Titel)", s.toLocalDate(),
                            s.hour * 60 + s.minute, endMinute.coerceAtLeast(s.hour * 60 + s.minute), c.getInt(5), c.getString(6) ?: "",
                        ),
                        c.getLong(7), c.getString(8),
                    )
                }
            }
        }
        return result
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
