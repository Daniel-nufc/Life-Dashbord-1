package app.lifedashboard.planner

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** Ein Umsatz aus einem Import (Beträge in Cent, negativ = Ausgabe). */
data class ImportedTransaction(
    val date: LocalDate,
    val amountCents: Long,
    val counterparty: String,
    val description: String,
) {
    /** Kennung zum Erkennen von Doppel-Importen. */
    val hash: String get() = "${date.toEpochDay()}|$amountCents|${counterparty.lowercase().trim()}|${description.lowercase().trim().take(60)}"
}

/**
 * Liest den CSV-Export der DKB (neues Format seit 2023 und altes Format).
 * Die Datei bleibt auf dem Handy – es wird nichts hochgeladen.
 */
object DkbCsvParser {
    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.yy")
    private val longDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(text: String): List<ImportedTransaction> {
        val rows = text.removePrefix("﻿").lines().map(::splitRow)
        val headerIndex = rows.indexOfFirst { row ->
            row.any { it.startsWith("Buchungsdatum") || it.startsWith("Buchungstag") } && row.any { it.startsWith("Betrag") }
        }
        if (headerIndex < 0) error("Keine DKB-Umsatzliste erkannt (Kopfzeile mit „Buchungsdatum“ und „Betrag“ fehlt).")
        val header = rows[headerIndex]
        fun col(vararg names: String) = header.indexOfFirst { h -> names.any { h.startsWith(it) } }
        val iDate = col("Buchungsdatum", "Buchungstag")
        val iAmount = col("Betrag")
        val iPayer = col("Zahlungspflichtige")
        val iPayee = col("Zahlungsempfänger")
        val iOld = col("Auftraggeber")
        val iPurpose = col("Verwendungszweck")
        val iStatus = col("Status")

        return rows.drop(headerIndex + 1).mapNotNull { row ->
            if (row.size <= maxOf(iDate, iAmount)) return@mapNotNull null
            if (iStatus >= 0 && row.getOrNull(iStatus)?.contains("vorgemerkt", ignoreCase = true) == true) return@mapNotNull null
            val date = parseDate(row[iDate]) ?: return@mapNotNull null
            val amount = parseAmount(row[iAmount]) ?: return@mapNotNull null
            val counterparty = when {
                iOld >= 0 -> row.getOrNull(iOld)
                amount < 0 -> row.getOrNull(iPayee)
                else -> row.getOrNull(iPayer)
            }.orEmpty().trim()
            ImportedTransaction(date, amount, counterparty, row.getOrNull(iPurpose).orEmpty().trim())
        }
    }

    /** Teilt eine CSV-Zeile mit ; und Anführungszeichen. */
    private fun splitRow(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ';' && !quoted -> { cells += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells.map { it.trim() }
    }

    fun parseDate(value: String): LocalDate? = value.trim().let {
        runCatching { if (it.length <= 8) LocalDate.parse(it, shortDate) else LocalDate.parse(it, longDate) }.getOrNull()
    }

    /** „-1.234,56 €“ → -123456 */
    fun parseAmount(value: String): Long? {
        val cleaned = value.replace("€", "").replace("EUR", "").replace(" ", "").replace(" ", "").replace(".", "").replace(',', '.')
        return cleaned.toDoubleOrNull()?.let { (it * 100).roundToLong() }
    }
}

/**
 * Ordnet Umsätze Kategorien zu – mit eingebauten Regeln (REWE → Lebensmittel …) und Regeln,
 * die aus deinen Korrekturen gelernt werden. Gelernte Regeln gewinnen immer.
 */
object Categorizer {
    data class Rule(val keyword: String, val category: String, val hits: Int = 1)

    val defaults: List<Rule> = listOf(
        "rewe", "edeka", "lidl", "aldi", "netto", "penny", "kaufland", "denns", "bio company",
    ).map { Rule(it, "Lebensmittel") } + listOf(
        "bvg", "deutsche bahn", "db vertrieb", "s-bahn", "tier", "lime", "shell", "aral",
    ).map { Rule(it, "Mobilität") } + listOf(
        "spotify", "netflix", "disney", "apple.com", "google", "youtube", "playstation", "xbox", "canva", "amazon prime",
    ).map { Rule(it, "Abos") } + listOf(
        "mcdonald", "burger king", "lieferando", "wolt", "uber eats", "doener", "döner", "subway", "kfc", "starbucks",
    ).map { Rule(it, "Essen gehen") } + listOf(
        "zalando", "h&m", "nike", "adidas", "about you", "primark", "snipes",
    ).map { Rule(it, "Kleidung") } + listOf(
        "dm-drogerie", "dm drogerie", "rossmann", "apotheke",
    ).map { Rule(it, "Drogerie & Gesundheit") } + listOf(
        Rule("amazon", "Shopping"),
        Rule("ebay", "Shopping"),
        Rule("etsy", "Etsy"),
        Rule("taschengeld", "Taschengeld"),
        Rule("lohn", "Job"),
        Rule("gehalt", "Job"),
        Rule("fahrschule", "Führerschein"),
        Rule("berliner tsc", "Sport"),
        Rule("decathlon", "Sport"),
    )

    fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[0-9]+"), " ")
        .replace(Regex("[^a-zäöüß&.\\- ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun categorize(counterparty: String, description: String, learned: List<Rule>): String? {
        val text = normalize("$counterparty $description")
        val match: (List<Rule>) -> Rule? = { rules ->
            rules.filter { it.keyword.isNotBlank() && text.contains(it.keyword) }
                .maxWithOrNull(compareBy<Rule>({ it.keyword.length }, { it.hits }))
        }
        return (match(learned) ?: match(defaults))?.category
    }

    /** Leitet aus einer Korrektur ein Stichwort ab (meist der Name des Empfängers). */
    fun keywordFor(counterparty: String, description: String): String {
        val fromCounterparty = normalize(counterparty)
            .split(' ')
            .filter { it.length >= 3 && it !in stopWords }
            .take(2)
            .joinToString(" ")
        if (fromCounterparty.isNotBlank()) return fromCounterparty
        return normalize(description).split(' ').firstOrNull { it.length >= 4 && it !in stopWords }.orEmpty()
    }

    private val stopWords = setOf("gmbh", "co.", "kg", "ag", "und", "der", "die", "das", "markt", "sagt", "danke", "berlin", "kartenzahlung", "lastschrift")
}

/** Erkennt wiederkehrende Zahlungen (Abos/Fixkosten): mind. 2× im Abstand von ~1 Monat mit ähnlichem Betrag. */
object SubscriptionDetector {
    data class Subscription(val name: String, val averageCents: Long, val lastDate: LocalDate, val nextExpected: LocalDate)

    fun detect(transactions: List<ImportedTransaction>): List<Subscription> =
        transactions.filter { it.amountCents < 0 }
            .groupBy { Categorizer.keywordFor(it.counterparty, it.description) }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (name, list) ->
                val sorted = list.sortedBy { it.date }
                if (sorted.size < 2) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
                val monthly = gaps.all { it in 26..35 }
                val avg = sorted.map { abs(it.amountCents) }.average()
                val similar = sorted.all { abs(abs(it.amountCents) - avg) <= avg * 0.15 }
                if (!monthly || !similar) return@mapNotNull null
                Subscription(name, avg.roundToLong(), sorted.last().date, sorted.last().date.plusMonths(1))
            }
            .sortedByDescending { it.averageCents }
}

/** Prognose für den Kontostand am Monatsende. */
object MonthForecast {
    /**
     * @param balanceCents aktueller Stand
     * @param variableSpentCents bisher variable Ausgaben in diesem Monat (positiv)
     * @param upcomingFixedCents noch erwartete feste Buchungen bis Monatsende (Einnahmen +, Ausgaben −)
     */
    fun project(today: LocalDate, balanceCents: Long, variableSpentCents: Long, upcomingFixedCents: Long): Long {
        val elapsed = today.dayOfMonth
        val remaining = today.lengthOfMonth() - elapsed
        val perDay = if (elapsed > 0) variableSpentCents.toDouble() / elapsed else 0.0
        return balanceCents + upcomingFixedCents - (perDay * remaining).roundToLong()
    }
}
