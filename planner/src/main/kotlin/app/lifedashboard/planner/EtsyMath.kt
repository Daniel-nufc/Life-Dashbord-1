package app.lifedashboard.planner

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * Etsy-Gebühren als einstellbare Werte. Standardwerte = übliche Sätze für Verkäufer in Deutschland
 * (Stand 2025/26) – bitte mit deiner Etsy-Abrechnung abgleichen und in der App anpassen.
 */
data class EtsyFees(
    /** Einstellgebühr pro verkauftem Stück (Listing wird nach Verkauf automatisch verlängert), ca. 0,20 $ */
    val listingFeeCents: Long = 18,
    /** Transaktionsgebühr in % vom Verkaufspreis */
    val transactionPercent: Double = 6.5,
    /** Zahlungsabwicklung in % */
    val processingPercent: Double = 4.0,
    /** Zahlungsabwicklung fix pro Bestellung */
    val processingFixedCents: Long = 30,
)

data class EtsySaleData(
    val date: LocalDate,
    val orderRef: String,
    val title: String,
    val priceCents: Long,
    val quantity: Int,
)

data class EtsyMonth(
    val month: YearMonth,
    val orders: Int,
    val items: Int,
    val revenueCents: Long,
    val feesCents: Long,
) {
    val netCents: Long get() = revenueCents - feesCents
}

object EtsyMath {

    fun fees(sale: EtsySaleData, fees: EtsyFees): Long {
        val gross = sale.priceCents * sale.quantity
        val percent = gross * (fees.transactionPercent + fees.processingPercent) / 100.0
        return (percent + fees.processingFixedCents + fees.listingFeeCents * sale.quantity).roundToLong()
    }

    fun month(sales: List<EtsySaleData>, month: YearMonth, fees: EtsyFees): EtsyMonth {
        val inMonth = sales.filter { YearMonth.from(it.date) == month }
        // Fixe Zahlungsgebühr fällt pro Bestellung an, nicht pro Position
        val byOrder = inMonth.groupBy { it.orderRef }
        val revenue = inMonth.sumOf { it.priceCents * it.quantity }
        val feeTotal = inMonth.sumOf { fees(it, fees.copy(processingFixedCents = 0)) } + byOrder.size * fees.processingFixedCents
        return EtsyMonth(month, byOrder.size, inMonth.sumOf { it.quantity }, revenue, feeTotal)
    }

    /** Veränderung in % zum Vormonat, null wenn der Vormonat 0 war. */
    fun change(current: Long, previous: Long): Int? =
        if (previous == 0L) null else (((current - previous) * 100.0) / previous).roundToLong().toInt()

    fun topProducts(sales: List<EtsySaleData>, limit: Int = 5): List<Pair<String, Long>> =
        sales.groupBy { it.title }.mapValues { (_, l) -> l.sumOf { it.priceCents * it.quantity } }
            .toList().sortedByDescending { it.second }.take(limit)

    /** Saison-Tipp für Tassen-/Sublimationsvorlagen. */
    fun seasonalTip(today: LocalDate): String = when (today.month) {
        Month.SEPTEMBER, Month.OCTOBER -> "Weihnachten kommt: Winter- und Weihnachts-Motive jetzt online stellen – viele kaufen Vorlagen früh, um selbst noch zu produzieren."
        Month.NOVEMBER -> "Hauptsaison! Weihnachts-Motive bis Mitte November online, danach Last-Minute-Sets und Neujahr nachlegen."
        Month.DECEMBER -> "Jetzt schon an Valentinstag und Winter-Sets für Januar denken."
        Month.JANUARY -> "Valentinstag (14.2.) vorbereiten: Herzen, Sprüche, Paar-Motive."
        Month.FEBRUARY, Month.MARCH -> "Ostern und Muttertag (Mai) vorbereiten – Frühlings-Motive."
        Month.APRIL -> "Muttertag und Vatertag (Mai/Juni) – Sprüche-Tassen laufen gut."
        Month.MAY, Month.JUNE -> "Sommer, Einschulung (August/September) und Lehrer-Geschenke vorbereiten."
        Month.JULY, Month.AUGUST -> "Einschulung, Halloween-Motive starten – und Weihnachts-Sets planen."
    }
}
