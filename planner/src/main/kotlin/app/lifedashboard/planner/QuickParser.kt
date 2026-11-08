package app.lifedashboard.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/** Ergebnis der Schnelleingabe. */
sealed interface QuickIntent {
    data class Event(val title: String, val date: LocalDate, val start: LocalTime?, val end: LocalTime?) : QuickIntent
    data class Homework(val subjectCode: String?, val title: String, val due: LocalDate?) : QuickIntent
    data class Exam(val subjectCode: String?, val title: String, val date: LocalDate) : QuickIntent
    data class Shift(val date: LocalDate, val start: LocalTime, val end: LocalTime, val note: String?) : QuickIntent
    data class Todo(val title: String, val due: LocalDate?) : QuickIntent
    data class Expense(val amountCents: Long, val what: String, val date: LocalDate) : QuickIntent
}

/** Fach, das erkannt werden kann (Kürzel wie im Stundenplan + Name). */
data class QuickSubject(val code: String, val name: String)

/**
 * Versteht kurze deutsche Sätze – offline, ohne KI:
 * „Mi 17 Uhr Zahnarzt“, „morgen 15:30-17 Training“, „HA Mathe S. 45 Nr. 3“,
 * „Mathe Klausur am 2.12.“, „Schicht Sa 8-14“, „12,50 Döner“, „Etsy Mockups machen bis Freitag“.
 */
object QuickParser {
    private val weekdays = mapOf(
        "mo" to DayOfWeek.MONDAY, "montag" to DayOfWeek.MONDAY,
        "di" to DayOfWeek.TUESDAY, "dienstag" to DayOfWeek.TUESDAY,
        "mi" to DayOfWeek.WEDNESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
        "do" to DayOfWeek.THURSDAY, "donnerstag" to DayOfWeek.THURSDAY,
        "fr" to DayOfWeek.FRIDAY, "freitag" to DayOfWeek.FRIDAY,
        "sa" to DayOfWeek.SATURDAY, "samstag" to DayOfWeek.SATURDAY,
        "so" to DayOfWeek.SUNDAY, "sonntag" to DayOfWeek.SUNDAY,
    )
    private val homeworkWords = setOf("ha", "hausaufgabe", "hausaufgaben", "hausi", "hü")
    private val examWords = setOf("klausur", "test", "arbeit", "klassenarbeit", "lk", "kontrolle", "referat", "präsentation", "vortrag")
    private val shiftWords = setOf("schicht", "job", "arbeiten", "baustelle", "minijob")
    private val fillers = setOf("am", "um", "bis", "zum", "zur", "den", "der", "die", "das", "von", "ab", "in", "für", "uhr", "nächsten", "nächste", "kommenden", "kommende", "auf")

    private val subjectAliases = mapOf(
        "mathe" to "MA", "mathematik" to "MA", "ma" to "MA",
        "deutsch" to "DE", "de" to "DE",
        "englisch" to "E", "english" to "E", "e" to "E",
        "bio" to "BIO", "biologie" to "BIO",
        "chemie" to "CH", "ch" to "CH",
        "sport" to "SP",
        "gewi" to "GeWi", "geschichte" to "GeWi", "politik" to "GeWi",
        "ds" to "DS", "theater" to "DS",
        "pm" to "PM", "projektmanagement" to "PM",
        "tumm" to "TuMM", "management" to "TuMM",
        "tumt" to "TuMT", "technik" to "TuMT",
    )

    fun parse(input: String, today: LocalDate, subjects: List<QuickSubject> = emptyList()): QuickIntent? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val consumed = mutableSetOf<Int>() // Indizes verbrauchter Wörter
        val words = text.split(Regex("\\s+"))
        val lower = words.map { it.lowercase().trimEnd(',', '.', ':', '!') }

        // ---------- Betrag am Anfang → Ausgabe („12,50 Döner“, „5€ Kino“)
        Regex("^(\\d+(?:[.,]\\d{1,2})?)\\s*(€|euro|eur)?\\s+(.+)$", RegexOption.IGNORE_CASE).find(text)?.let { m ->
            val hasCurrency = m.groupValues[2].isNotEmpty() || m.groupValues[1].contains(',') || m.groupValues[1].contains('.')
            val rest = m.groupValues[3]
            if (hasCurrency && !rest.lowercase().startsWith("uhr")) {
                val cents = (m.groupValues[1].replace(',', '.').toDouble() * 100).roundToLong()
                return QuickIntent.Expense(cents, rest.trim().replaceFirstChar { it.uppercase() }, today)
            }
        }

        // ---------- Datum
        var date: LocalDate? = null
        lower.forEachIndexed { i, w ->
            if (date != null) return@forEachIndexed
            val d = when (w) {
                "heute" -> today
                "morgen" -> today.plusDays(1)
                "übermorgen", "uebermorgen" -> today.plusDays(2)
                else -> weekdays[w]?.let { today.with(TemporalAdjusters.nextOrSame(it)) }
            }
            if (d != null) {
                date = d
                consumed += i
            }
        }
        if (date == null) {
            lower.forEachIndexed { i, w ->
                if (date != null) return@forEachIndexed
                Regex("^(\\d{1,2})\\.(\\d{1,2})\\.?(\\d{2,4})?$").find(w)?.let { m ->
                    val day = m.groupValues[1].toInt()
                    val month = m.groupValues[2].toInt()
                    val yearRaw = m.groupValues[3]
                    if (day in 1..31 && month in 1..12) {
                        var year = if (yearRaw.isEmpty()) today.year else yearRaw.toInt().let { if (it < 100) 2000 + it else it }
                        var candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                        if (candidate != null && yearRaw.isEmpty() && candidate.isBefore(today)) {
                            year += 1
                            candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                        }
                        if (candidate != null) {
                            date = candidate
                            consumed += i
                        }
                    }
                }
            }
        }
        // „in 3 Tagen“
        if (date == null) {
            for (i in 0 until lower.size - 2) {
                if (lower[i] == "in" && lower[i + 1].toIntOrNull() != null && lower[i + 2].startsWith("tag")) {
                    date = today.plusDays(lower[i + 1].toLong())
                    consumed += setOf(i, i + 1, i + 2)
                    break
                }
            }
        }

        // ---------- Uhrzeit(en): „17 Uhr“, „17:30“, „8-14“, „15:30-17“, „von 15 bis 18“
        var start: LocalTime? = null
        var end: LocalTime? = null
        fun time(token: String): LocalTime? {
            val m = Regex("^(\\d{1,2})(?::|\\.)?(\\d{2})?(?:h|uhr)?$").find(token) ?: return null
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].ifEmpty { "0" }.toInt()
            return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
        }
        lower.forEachIndexed { i, w ->
            if (start != null || i in consumed) return@forEachIndexed
            val range = Regex("^(\\d{1,2}(?::\\d{2})?)-(\\d{1,2}(?::\\d{2})?)(?:uhr)?$").find(w)
            if (range != null) {
                val s = time(range.groupValues[1])
                val e = time(range.groupValues[2])
                if (s != null && e != null) {
                    start = s; end = e; consumed += i
                    if (lower.getOrNull(i + 1) == "uhr") consumed += i + 1
                }
                return@forEachIndexed
            }
            val isTimeContext = w.contains(':') || lower.getOrNull(i + 1) == "uhr" || w.endsWith("uhr") || lower.getOrNull(i - 1) == "um" || lower.getOrNull(i - 1) == "von"
            if (isTimeContext) {
                time(w)?.let {
                    start = it
                    consumed += i
                    if (lower.getOrNull(i + 1) == "uhr") consumed += i + 1
                    // „bis 18“
                    if (lower.getOrNull(i + 1) == "bis" || lower.getOrNull(i + 2) == "bis") {
                        val j = if (lower.getOrNull(i + 1) == "bis") i + 2 else i + 3
                        time(lower.getOrNull(j) ?: "")?.let { e -> end = e; consumed += j }
                    }
                }
            }
        }

        // ---------- Fach
        var subjectCode: String? = null
        lower.forEachIndexed { i, w ->
            if (subjectCode != null || i in consumed) return@forEachIndexed
            val direct = subjects.firstOrNull { s ->
                val base = s.code.lowercase().substringBefore('-')
                w == s.code.lowercase() || w == base || w == s.name.lowercase()
            }?.code
            val alias = subjectAliases[w]?.let { a -> subjects.firstOrNull { it.code.substringBefore('-').equals(a, true) }?.code ?: a }
            val code = direct ?: alias
            // Einzelbuchstaben-Kürzel („e“) nur zusammen mit HA/Klausur
            if (code != null && (w.length > 1 || lower.any { it in homeworkWords || it in examWords })) {
                subjectCode = code
                consumed += i
            }
        }

        val isHomework = lower.any { it in homeworkWords }
        val isExam = lower.any { it in examWords }
        val isShift = lower.any { it in shiftWords }
        lower.forEachIndexed { i, w -> if (w in homeworkWords || w in examWords || w in shiftWords) consumed += i }

        val title = words.filterIndexed { i, _ -> i !in consumed && lower[i] !in fillers }
            .joinToString(" ").trim().trim(',', '-', ':').replaceFirstChar { it.uppercase() }

        return when {
            isShift && start != null -> QuickIntent.Shift(date ?: today, start!!, end ?: start!!.plusHours(4), title.ifBlank { null })
            isExam && date != null -> QuickIntent.Exam(subjectCode, title.ifBlank { examTitle(lower) }, date!!)
            isHomework -> QuickIntent.Homework(subjectCode, title.ifBlank { "Hausaufgabe" }, date)
            start != null -> QuickIntent.Event(title.ifBlank { "Termin" }, date ?: today, start, end ?: start!!.plusHours(1))
            title.isNotBlank() -> QuickIntent.Todo(title, date)
            else -> null
        }
    }

    private fun examTitle(lower: List<String>) = when {
        "test" in lower -> "Test"
        "referat" in lower || "vortrag" in lower -> "Referat"
        "präsentation" in lower -> "Präsentation"
        else -> "Klausur"
    }
}
