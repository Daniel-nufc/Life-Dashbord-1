package app.lifedashboard.planner

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Ein Termin aus einer iCal-Datei (z. B. Lernraum-/Moodle-Export). Zeiten in lokaler Zeitzone. */
data class IcsEvent(
    val uid: String,
    val summary: String,
    val description: String?,
    val startDate: LocalDate,
    /** null = ganztägig */
    val startTime: java.time.LocalTime?,
    val endTime: java.time.LocalTime?,
)

/**
 * Minimaler, robuster iCalendar-Leser (RFC 5545) – liest nur VEVENTs mit
 * UID, SUMMARY, DESCRIPTION, DTSTART, DTEND. Reicht für Abgabe-Termine aus Moodle/IServ/WebUntis.
 */
object IcsParser {
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<IcsEvent> {
        val lines = unfold(text)
        val events = mutableListOf<IcsEvent>()
        var current: MutableMap<String, Pair<Map<String, String>, String>>? = null
        for (line in lines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableMapOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let { toEvent(it, zone) }?.let(events::add)
                    current = null
                }
                current != null -> {
                    val colon = indexOfValueColon(line)
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val value = line.substring(colon + 1)
                    val parts = head.split(';')
                    val name = parts.first().uppercase()
                    val params = parts.drop(1).mapNotNull {
                        val eq = it.indexOf('=')
                        if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1).trim('"') else null
                    }.toMap()
                    if (name !in current) current[name] = params to value
                }
            }
        }
        return events
    }

    /** Zeilen, die mit Leerzeichen/Tab beginnen, gehören zur vorherigen Zeile. */
    private fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + raw.substring(1)
            } else if (raw.isNotEmpty()) {
                result += raw
            }
        }
        return result
    }

    /** Doppelpunkt, der Name/Parameter vom Wert trennt (Doppelpunkte in Anführungszeichen ignorieren). */
    private fun indexOfValueColon(line: String): Int {
        var quoted = false
        line.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) return i
        }
        return -1
    }

    private fun unescape(value: String): String = value
        .replace("\\n", "\n").replace("\\N", "\n")
        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
        .trim()

    private fun toEvent(props: Map<String, Pair<Map<String, String>, String>>, zone: ZoneId): IcsEvent? {
        val (startParams, startValue) = props["DTSTART"] ?: return null
        val start = parseTime(startParams, startValue, zone) ?: return null
        val end = props["DTEND"]?.let { (p, v) -> parseTime(p, v, zone) }
        val summary = props["SUMMARY"]?.second?.let(::unescape).orEmpty().ifBlank { "(ohne Titel)" }
        val uid = props["UID"]?.second?.trim().orEmpty().ifBlank { "$summary@$startValue" }
        return IcsEvent(
            uid = uid,
            summary = summary,
            description = props["DESCRIPTION"]?.second?.let(::unescape)?.ifBlank { null },
            startDate = start.toLocalDate(),
            startTime = if (start.allDay) null else start.time.toLocalTime(),
            endTime = if (start.allDay || end == null || end.allDay) null else end.time.toLocalTime(),
        )
    }

    private data class Parsed(val time: LocalDateTime, val allDay: Boolean) {
        fun toLocalDate(): LocalDate = time.toLocalDate()
    }

    private fun parseTime(params: Map<String, String>, raw: String, zone: ZoneId): Parsed? {
        val value = raw.trim()
        return try {
            when {
                params["VALUE"]?.uppercase() == "DATE" || value.length == 8 ->
                    Parsed(LocalDate.parse(value, date).atStartOfDay(), allDay = true)
                value.endsWith("Z") ->
                    Parsed(
                        LocalDateTime.parse(value.dropLast(1), dateTime).atOffset(ZoneOffset.UTC)
                            .atZoneSameInstant(zone).toLocalDateTime(),
                        allDay = false,
                    )
                params["TZID"] != null -> {
                    val source = runCatching { ZoneId.of(params.getValue("TZID")) }.getOrDefault(zone)
                    Parsed(LocalDateTime.parse(value, dateTime).atZone(source).withZoneSameInstant(zone).toLocalDateTime(), allDay = false)
                }
                else -> Parsed(LocalDateTime.parse(value, dateTime), allDay = false)
            }
        } catch (e: Exception) {
            null
        }
    }
}
