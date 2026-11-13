package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private typealias Props = Map<String, List<Pair<Map<String, String>, String>>>

/** Ein Termin aus einer iCal-Datei (Lernraum, FamilyWall, Google …). Zeiten in lokaler Zeitzone. */
data class IcsEvent(
    val uid: String,
    val summary: String,
    val description: String?,
    val startDate: LocalDate,
    /** null = ganztägig */
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val location: String? = null,
    /** Teilnehmer (Name aus CN oder Teil der E-Mail vor dem @), z. B. die in FamilyWall ausgewählten Personen. */
    val attendees: List<String> = emptyList(),
)

/** Ergebnis von [IcsPersonFilter.apply]: was übernommen wird und was im Kalender gefunden wurde. */
data class PersonFilterResult(
    val kept: List<IcsEvent>,
    val total: Int,
    /** Alle Personen, die im Kalender als Teilnehmer vorkommen. */
    val people: Set<String>,
)

/**
 * „Nur meine Termine“: ein Termin gehört zu [name], wenn die Person als Teilnehmer ausgewählt ist
 * oder der Name im Titel/in der Beschreibung steht (z. B. „Daniel Zahnarzt“).
 * Es reicht der Vorname: „Daniel“ passt zu „Daniel Müller“ und „daniel.mueller@…“.
 */
object IcsPersonFilter {
    fun apply(events: List<IcsEvent>, name: String): PersonFilterResult {
        val people = events.flatMap { it.attendees }.toSortedSet(String.CASE_INSENSITIVE_ORDER)
        val token = name.trim().split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
        if (token.isBlank()) return PersonFilterResult(events, events.size, people)
        val word = Regex("(^|[^\\p{L}])" + Regex.escape(token) + "($|[^\\p{L}])", RegexOption.IGNORE_CASE)
        val kept = events.filter { e ->
            e.attendees.any { word.containsMatchIn(it) } ||
                word.containsMatchIn(e.summary) || (e.description?.let(word::containsMatchIn) ?: false)
        }
        return PersonFilterResult(kept, events.size, people)
    }
}

/**
 * Robuster iCalendar-Leser (RFC 5545) für Abgabe-Termine (Moodle/IServ/WebUntis) und
 * Familienkalender (FamilyWall, Google, Outlook).
 *
 * - Serientermine (RRULE: täglich, wöchentlich, monatlich, jährlich mit INTERVAL, COUNT, UNTIL,
 *   BYDAY, BYMONTHDAY, BYMONTH), ausgelassene Tage (EXDATE) und einzeln geänderte Termine (RECURRENCE-ID)
 * - abgesagte Termine (STATUS:CANCELLED) fallen weg
 * - Termine über mehrere Tage erscheinen an jedem Tag
 *
 * Serien werden nur im Zeitraum [from, to] aufgefaltet. Ohne Zeitraum liefert eine Serie nur ihren ersten Termin.
 */
object IcsParser {
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Höchstens so viele Tage wird ein mehrtägiger Termin aufgeteilt (z. B. Urlaub). */
    private const val MAX_SPAN_DAYS = 60L

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault(), from: LocalDate? = null, to: LocalDate? = null): List<IcsEvent> {
        val raw = readEvents(text)
        // Einzeln geänderte Termine einer Serie (gleiche UID + RECURRENCE-ID)
        val overrides = raw.filter { it.containsKey("RECURRENCE-ID") }
            .groupBy { it.uid() }
        val result = mutableListOf<IcsEvent>()
        raw.filter { !it.containsKey("RECURRENCE-ID") }.forEach { props ->
            val event = toEvent(props, zone, overrides[props.uid()].orEmpty(), from, to)
            result += event
        }
        // Überschreibungen ohne passende Serie trotzdem zeigen
        val masters = raw.filter { !it.containsKey("RECURRENCE-ID") }.map { it.uid() }.toSet()
        overrides.filterKeys { it !in masters }.values.flatten().forEach { props ->
            result += single(props, zone, props.uid() + "#" + (props.first("RECURRENCE-ID")?.second?.take(8) ?: ""))
        }
        return result.filter { inRange(it.startDate, from, to) }
    }

    private fun inRange(day: LocalDate, from: LocalDate?, to: LocalDate?) =
        (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to))

    private fun Props.first(name: String) = this[name]?.firstOrNull()
    private fun Props.uid(): String = first("UID")?.second?.trim().orEmpty()
        .ifBlank { (first("SUMMARY")?.second.orEmpty()) + "@" + (first("DTSTART")?.second.orEmpty()) }
    private fun Props.cancelled() = first("STATUS")?.second?.trim().equals("CANCELLED", ignoreCase = true)

    private fun readEvents(text: String): List<Props> {
        val events = mutableListOf<Props>()
        var current: MutableMap<String, MutableList<Pair<Map<String, String>, String>>>? = null
        var nested = 0 // z. B. VALARM innerhalb eines VEVENT überspringen
        for (line in unfold(text)) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> { current = mutableMapOf(); nested = 0 }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let(events::add)
                    current = null
                }
                current != null && line.startsWith("BEGIN:", ignoreCase = true) -> nested++
                current != null && line.startsWith("END:", ignoreCase = true) -> nested = (nested - 1).coerceAtLeast(0)
                current != null && nested == 0 -> {
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
                    current.getOrPut(name) { mutableListOf() } += params to value
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

    /** Beginn, Dauer und Texte eines VEVENT. */
    private data class Base(
        val start: Parsed,
        val length: Duration,
        val allDayDays: Long,
        val summary: String,
        val description: String?,
        val location: String?,
        val attendees: List<String>,
    )

    private fun base(props: Props, zone: ZoneId): Base? {
        val (startParams, startValue) = props.first("DTSTART") ?: return null
        val start = parseTime(startParams, startValue, zone) ?: return null
        val end = props.first("DTEND")?.let { (p, v) -> parseTime(p, v, zone) }
        val duration = props.first("DURATION")?.second?.let { runCatching { Duration.parse(it.trim()) }.getOrNull() }
        val length = when {
            end != null && !end.time.isBefore(start.time) -> Duration.between(start.time, end.time)
            duration != null -> duration
            start.allDay -> Duration.ofDays(1)
            else -> Duration.ZERO
        }
        val allDayDays = if (start.allDay) length.toDays().coerceIn(1, MAX_SPAN_DAYS) else 0
        return Base(
            start = start,
            length = length,
            allDayDays = allDayDays,
            summary = props.first("SUMMARY")?.second?.let(::unescape).orEmpty().ifBlank { "(ohne Titel)" },
            description = props.first("DESCRIPTION")?.second?.let(::unescape)?.ifBlank { null },
            location = props.first("LOCATION")?.second?.let(::unescape)?.ifBlank { null },
            attendees = props["ATTENDEE"].orEmpty().mapNotNull { (params, value) ->
                params["CN"]?.let(::unescape)?.ifBlank { null }
                    ?: value.trim().removePrefix("mailto:").removePrefix("MAILTO:").substringBefore('@').ifBlank { null }
            }.distinct(),
        )
    }

    private fun single(props: Props, zone: ZoneId, uid: String): List<IcsEvent> {
        if (props.cancelled()) return emptyList()
        val b = base(props, zone) ?: return emptyList()
        return occurrence(b, b.start.time, uid)
    }

    private fun toEvent(props: Props, zone: ZoneId, overrides: List<Props>, from: LocalDate?, to: LocalDate?): List<IcsEvent> {
        val uid = props.uid()
        val rule = props.first("RRULE")?.second?.let(::parseRule)
        if (rule == null) return single(props, zone, uid)
        if (props.cancelled()) return emptyList()
        val b = base(props, zone) ?: return emptyList()

        val excluded = props["EXDATE"].orEmpty().flatMap { (p, v) ->
            v.split(',').mapNotNull { parseTime(p, it, zone)?.time?.toLocalDate() }
        }.toSet()
        val changed = overrides.mapNotNull { o ->
            val (p, v) = o.first("RECURRENCE-ID") ?: return@mapNotNull null
            parseTime(p, v, zone)?.time?.toLocalDate()?.let { it to o }
        }.toMap()

        // Ohne Zeitraum nur der erste Termin (wie früher)
        val windowFrom = from ?: b.start.time.toLocalDate()
        val windowTo = to ?: b.start.time.toLocalDate()
        val spanDays = if (b.start.allDay) b.allDayDays - 1 else ChronoUnit.DAYS.between(b.start.time.toLocalDate(), b.start.time.plus(b.length).toLocalDate())
        val days = expand(rule, b.start.time.toLocalDate(), windowFrom.minusDays(spanDays.coerceAtMost(MAX_SPAN_DAYS)), windowTo, zone)
        val result = mutableListOf<IcsEvent>()
        for (day in days) {
            if (day in excluded) continue
            val key = "$uid#${day.format(date)}"
            val override = changed[day]
            if (override != null) {
                result += single(override, zone, key)
            } else {
                result += occurrence(b, day.atTime(b.start.time.toLocalTime()), key)
            }
            if (from == null && to == null) break
        }
        // Verschobene Einzeltermine, deren ursprünglicher Tag außerhalb des Zeitraums lag
        changed.filterKeys { it !in days }.forEach { (day, o) ->
            result += single(o, zone, "$uid#${day.format(date)}")
        }
        return result
    }

    /** Ein Termin, bei mehreren Tagen ein Eintrag pro Tag. */
    private fun occurrence(b: Base, start: LocalDateTime, uid: String): List<IcsEvent> {
        if (b.start.allDay) {
            return (0 until b.allDayDays).map { i ->
                IcsEvent(if (i == 0L) uid else "$uid/$i", b.summary, b.description, start.toLocalDate().plusDays(i), null, null, b.location, b.attendees)
            }
        }
        val end = start.plus(b.length)
        val firstDay = start.toLocalDate()
        val lastDay = if (end.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalDate().isAfter(firstDay)) end.toLocalDate().minusDays(1) else end.toLocalDate()
        if (!lastDay.isAfter(firstDay)) {
            val endTime = if (b.length.isZero) null else end.toLocalTime().takeIf { it != LocalTime.MIDNIGHT } ?: LocalTime.of(23, 59)
            return listOf(IcsEvent(uid, b.summary, b.description, firstDay, start.toLocalTime(), endTime, b.location, b.attendees))
        }
        val count = ChronoUnit.DAYS.between(firstDay, lastDay).coerceAtMost(MAX_SPAN_DAYS)
        return (0..count).map { i ->
            val day = firstDay.plusDays(i)
            val (s, e) = when {
                i == 0L -> start.toLocalTime() to LocalTime.of(23, 59)
                day == lastDay -> LocalTime.MIDNIGHT to (end.toLocalTime().takeIf { it != LocalTime.MIDNIGHT } ?: LocalTime.of(23, 59))
                else -> null to null // Tage dazwischen: ganztägig
            }
            IcsEvent(if (i == 0L) uid else "$uid/$i", b.summary, b.description, day, s, e, b.location, b.attendees)
        }
    }

    // ---------- Serien (RRULE) ----------

    internal data class Rule(
        val freq: String,
        val interval: Int,
        val count: Int?,
        val until: String?,
        val untilParams: Map<String, String>,
        val byDay: List<Pair<Int?, DayOfWeek>>,
        val byMonthDay: List<Int>,
        val byMonth: List<Int>,
    )

    private val weekdays = mapOf(
        "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
    )

    internal fun parseRule(value: String): Rule? {
        val parts = value.trim().split(';').mapNotNull {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1) else null
        }.toMap()
        val freq = parts["FREQ"]?.uppercase() ?: return null
        if (freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) return null
        val byDay = parts["BYDAY"]?.split(',')?.mapNotNull { token ->
            val t = token.trim().uppercase()
            val day = weekdays[t.takeLast(2)] ?: return@mapNotNull null
            val n = t.dropLast(2).ifBlank { null }?.toIntOrNull()
            n to day
        }.orEmpty()
        return Rule(
            freq = freq,
            interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            count = parts["COUNT"]?.toIntOrNull(),
            until = parts["UNTIL"],
            untilParams = if (parts["UNTIL"]?.length == 8) mapOf("VALUE" to "DATE") else emptyMap(),
            byDay = byDay,
            byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty(),
            byMonth = parts["BYMONTH"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty(),
        )
    }

    /** Alle Tage der Serie im Zeitraum [from, to]. COUNT zählt ab Serienbeginn. */
    internal fun expand(rule: Rule, start: LocalDate, from: LocalDate, to: LocalDate, zone: ZoneId): List<LocalDate> {
        val until = rule.until?.let { parseTime(rule.untilParams, it, zone)?.time?.toLocalDate() }
        val last = listOfNotNull(to, until).minOrNull()!!
        val result = mutableListOf<LocalDate>()
        var found = 0
        // Nur COUNT muss ab Serienbeginn zählen, sonst reicht der Zeitraum (z. B. Geburtstag seit 1960)
        var day = if (rule.count == null && from.isAfter(start)) from else start
        while (!day.isAfter(last)) {
            if (matches(rule, start, day)) {
                found++
                if (!day.isBefore(from)) result += day
                if (rule.count != null && found >= rule.count) break
            }
            day = day.plusDays(1)
        }
        return result
    }

    private fun matchesWeekday(rule: Rule, day: LocalDate, withinMonth: Boolean): Boolean = rule.byDay.any { (n, dow) ->
        if (dow != day.dayOfWeek) return@any false
        if (n == null || !withinMonth) return@any true
        day.with(TemporalAdjusters.dayOfWeekInMonth(n, dow)) == day
    }

    private fun matchesMonthDay(rule: Rule, day: LocalDate): Boolean = rule.byMonthDay.any { md ->
        if (md > 0) day.dayOfMonth == md else day.dayOfMonth == day.lengthOfMonth() + md + 1
    }

    private fun matches(rule: Rule, start: LocalDate, day: LocalDate): Boolean {
        if (day.isBefore(start)) return false
        if (rule.byMonth.isNotEmpty() && day.monthValue !in rule.byMonth) return false
        return when (rule.freq) {
            "DAILY" -> ChronoUnit.DAYS.between(start, day) % rule.interval == 0L &&
                (rule.byDay.isEmpty() || matchesWeekday(rule, day, withinMonth = false)) &&
                (rule.byMonthDay.isEmpty() || matchesMonthDay(rule, day))
            "WEEKLY" -> {
                val weeks = ChronoUnit.WEEKS.between(start.with(DayOfWeek.MONDAY), day.with(DayOfWeek.MONDAY))
                weeks % rule.interval == 0L &&
                    if (rule.byDay.isEmpty()) day.dayOfWeek == start.dayOfWeek else matchesWeekday(rule, day, withinMonth = false)
            }
            "MONTHLY" -> {
                val months = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), day.withDayOfMonth(1))
                months % rule.interval == 0L && when {
                    rule.byMonthDay.isNotEmpty() -> matchesMonthDay(rule, day) && (rule.byDay.isEmpty() || matchesWeekday(rule, day, true))
                    rule.byDay.isNotEmpty() -> matchesWeekday(rule, day, withinMonth = true)
                    else -> day.dayOfMonth == start.dayOfMonth
                }
            }
            "YEARLY" -> {
                val years = (day.year - start.year).toLong()
                val months = rule.byMonth.ifEmpty { listOf(start.monthValue) }
                years % rule.interval == 0L && day.monthValue in months && when {
                    rule.byMonthDay.isNotEmpty() -> matchesMonthDay(rule, day)
                    rule.byDay.isNotEmpty() -> matchesWeekday(rule, day, withinMonth = true)
                    else -> day.dayOfMonth == start.dayOfMonth
                }
            }
            else -> false
        }
    }

    // ---------- Zeiten ----------

    private data class Parsed(val time: LocalDateTime, val allDay: Boolean)

    private fun parseTime(params: Map<String, String>, raw: String, zone: ZoneId): Parsed? {
        val value = raw.trim()
        return try {
            when {
                params["VALUE"]?.uppercase() == "DATE" || value.length == 8 ->
                    Parsed(LocalDate.parse(value.take(8), date).atStartOfDay(), allDay = true)
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
