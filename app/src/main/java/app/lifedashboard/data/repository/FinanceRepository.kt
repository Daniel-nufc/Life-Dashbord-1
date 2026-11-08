package app.lifedashboard.data.repository

import app.lifedashboard.data.db.AccountEntity
import app.lifedashboard.data.db.BudgetEntity
import app.lifedashboard.data.db.CategoryRuleEntity
import app.lifedashboard.data.db.FinanceDao
import app.lifedashboard.data.db.RecurringEntity
import app.lifedashboard.data.db.SavingsGoalEntity
import app.lifedashboard.data.db.TransactionEntity
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.Categorizer
import app.lifedashboard.planner.DkbCsvParser
import app.lifedashboard.planner.ImportedTransaction
import app.lifedashboard.planner.MonthForecast
import app.lifedashboard.planner.SubscriptionDetector
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

val DEFAULT_CATEGORIES = listOf(
    "Lebensmittel", "Essen gehen", "Mobilität", "Abos", "Kleidung", "Shopping", "Drogerie & Gesundheit",
    "Freizeit", "Sport", "Schule", "Etsy", "Führerschein", "Feriencamp", "Geschenke", "Sonstiges",
    "Taschengeld", "Job", "Etsy-Einnahmen", "Sparen",
)

data class BudgetStatus(val category: String, val limitCents: Long, val spentCents: Long) {
    val ratio: Float get() = if (limitCents <= 0) 0f else spentCents.toFloat() / limitCents
}

data class FinanceOverview(
    val accounts: List<Pair<AccountEntity, Long>>,
    val totalCents: Long,
    val bufferCents: Long,
    val availableCents: Long,
    val forecastCents: Long,
    val monthIncomeCents: Long,
    val monthSpentCents: Long,
    val byCategory: List<Pair<String, Long>>,
    val budgets: List<BudgetStatus>,
    val transactions: List<TransactionEntity>,
    val recurring: List<RecurringEntity>,
    val subscriptions: List<SubscriptionDetector.Subscription>,
    val savings: List<SavingsGoalEntity>,
    val categories: List<String>,
    /** Vorschläge: Kategorie → Monatsbudget (aus den letzten 3 Monaten oder Startwerte) */
    val budgetSuggestions: List<Pair<String, Long>>,
    /** Sparziel → nötige Rate pro Monat */
    val savingsRates: List<Pair<SavingsGoalEntity, Long>>,
    /** Monatliche feste Einnahmen (Taschengeld …) */
    val monthlyIncomeCents: Long,
)

data class ImportResult(val added: Int, val duplicates: Int)

@Singleton
class FinanceRepository @Inject constructor(
    private val dao: FinanceDao,
    private val settings: SettingsRepository,
) {
    private data class Base(val accounts: List<AccountEntity>, val tx: List<TransactionEntity>, val budgets: List<BudgetEntity>)
    private data class Extra(val recurring: List<RecurringEntity>, val savings: List<SavingsGoalEntity>, val buffer: Long)

    fun overview(today: LocalDate = LocalDate.now()): Flow<FinanceOverview> {
        val base = combine(dao.observeAccounts(), dao.observeTransactions(), dao.observeBudgets()) { a, t, b -> Base(a, t, b) }
        val extra = combine(dao.observeRecurring(), dao.observeSavings(), settings.settings) { r, s, st -> Extra(r, s, st.bufferCents) }
        return combine(base, extra) { b, e ->
            val month = YearMonth.from(today)
            val monthStart = month.atDay(1).toEpochDay()
            val monthEnd = month.atEndOfMonth().toEpochDay()
            val monthTx = b.tx.filter { it.dateEpochDay in monthStart..monthEnd }
            val balances = b.accounts.map { acc -> acc to acc.initialBalanceCents + b.tx.filter { it.accountId == acc.id }.sumOf { it.amountCents } }
            val total = balances.sumOf { it.second }
            val spent = monthTx.filter { it.amountCents < 0 }.sumOf { -it.amountCents }
            val income = monthTx.filter { it.amountCents > 0 }.sumOf { it.amountCents }
            val variableSpent = monthTx.filter { it.amountCents < 0 && it.recurringId == null }.sumOf { -it.amountCents }
            val upcomingFixed = e.recurring.filter { it.active && it.dayOfMonth > today.dayOfMonth }.sumOf { it.amountCents }
            val byCategory = monthTx.filter { it.amountCents < 0 }.groupBy { it.category ?: "Ohne Kategorie" }
                .mapValues { (_, l) -> l.sumOf { -it.amountCents } }.toList().sortedByDescending { it.second }
            val spentBy = byCategory.toMap()
            val imported = b.tx.filter { it.dateEpochDay >= today.minusDays(150).toEpochDay() }
                .map { ImportedTransaction(LocalDate.ofEpochDay(it.dateEpochDay), it.amountCents, it.counterparty, it.description) }
            FinanceOverview(
                accounts = balances,
                totalCents = total,
                bufferCents = e.buffer,
                availableCents = total - e.buffer,
                forecastCents = MonthForecast.project(today, total, variableSpent, upcomingFixed),
                monthIncomeCents = income,
                monthSpentCents = spent,
                byCategory = byCategory,
                budgets = b.budgets.map { BudgetStatus(it.category, it.monthlyLimitCents, spentBy[it.category] ?: 0) },
                transactions = b.tx,
                recurring = e.recurring,
                subscriptions = SubscriptionDetector.detect(imported),
                savings = e.savings,
                categories = (DEFAULT_CATEGORIES + b.tx.mapNotNull { it.category }).distinct(),
                budgetSuggestions = suggestBudgets(b.tx, b.budgets, today),
                savingsRates = e.savings.mapNotNull { g ->
                    val deadline = g.deadlineEpochDay?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
                    val months = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(deadline)).coerceAtLeast(1)
                    val missing = (g.targetCents - g.savedCents).coerceAtLeast(0)
                    if (missing == 0L) null else g to (((missing / months) + 99) / 100 * 100)
                },
                monthlyIncomeCents = e.recurring.filter { it.active && it.amountCents > 0 }.sumOf { it.amountCents },
            )
        }
    }

    /**
     * Budget-Vorschläge: Durchschnitt der letzten 3 vollen Monate je Kategorie, aufgerundet auf 5 €.
     * Ohne Daten: Startwerte (Essen gehen 15 €, Shopping 10 €, Freizeit 10 €).
     */
    private fun suggestBudgets(tx: List<TransactionEntity>, existing: List<BudgetEntity>, today: LocalDate): List<Pair<String, Long>> {
        val have = existing.map { it.category }.toSet()
        val months = (1..3).map { YearMonth.from(today).minusMonths(it.toLong()) }
        val firstDay = months.last().atDay(1).toEpochDay()
        val lastDay = months.first().atEndOfMonth().toEpochDay()
        val spent = tx.filter { it.amountCents < 0 && it.dateEpochDay in firstDay..lastDay && it.category != null && it.recurringId == null }
        val suggestions = if (spent.isEmpty()) {
            listOf("Essen gehen" to 1500L, "Shopping" to 1000L, "Freizeit" to 1000L)
        } else {
            spent.groupBy { it.category!! }.mapValues { (_, l) -> -l.sumOf { it.amountCents } / 3 }
                .filter { it.value >= 300 && it.key !in setOf("Sparen", "Taschengeld", "Job") }
                .map { (c, avg) -> c to ((avg + 499) / 500 * 500) }
                .sortedByDescending { it.second }
        }
        return suggestions.filter { it.first !in have }
    }

    /** Sparrate als feste Buchung (am 22., nach dem Taschengeld) – zählt automatisch aufs Sparziel. */
    suspend fun adoptSavingsRate(goal: SavingsGoalEntity, monthlyCents: Long) {
        val account = dao.accounts().firstOrNull { it.kind == app.lifedashboard.data.db.AccountKind.BANK } ?: dao.accounts().firstOrNull() ?: return
        dao.upsertRecurring(
            RecurringEntity(
                accountId = account.id, title = "Sparen: ${goal.title}", amountCents = -monthlyCents, dayOfMonth = 22,
                category = "Sparen", active = true, lastGeneratedEpochDay = LocalDate.now().toEpochDay(),
            ),
        )
    }

    private suspend fun learnedRules(): List<Categorizer.Rule> = dao.rules().map { Categorizer.Rule(it.keyword, it.category, it.hits) }

    suspend fun suggestCategory(counterparty: String, description: String): String? =
        Categorizer.categorize(counterparty, description, learnedRules())

    /** Speichert einen Umsatz. Wurde die Kategorie geändert, lernt die App daraus. */
    suspend fun saveTransaction(tx: TransactionEntity, learn: Boolean) {
        dao.upsertTransaction(tx)
        if (learn && tx.category != null) learn(tx.counterparty, tx.description, tx.category)
    }

    private suspend fun learn(counterparty: String, description: String, category: String) {
        val keyword = Categorizer.keywordFor(counterparty, description)
        if (keyword.isBlank()) return
        val existing = dao.rules().firstOrNull { it.keyword == keyword }
        dao.putRule(CategoryRuleEntity(id = existing?.id ?: 0, keyword = keyword, category = category, hits = (existing?.hits ?: 0) + 1))
    }

    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)

    /** DKB-CSV importieren: doppelte Umsätze werden erkannt, Kategorien automatisch gesetzt. */
    suspend fun importDkbCsv(text: String, accountId: Long): ImportResult {
        val parsed = DkbCsvParser.parse(text)
        val known = dao.importHashes().toMutableSet()
        val rules = learnedRules()
        val fresh = parsed.filter { known.add(it.hash) }
        dao.insertTransactions(
            fresh.map {
                TransactionEntity(
                    accountId = accountId,
                    dateEpochDay = it.date.toEpochDay(),
                    amountCents = it.amountCents,
                    counterparty = it.counterparty,
                    description = it.description,
                    category = Categorizer.categorize(it.counterparty, it.description, rules),
                    importHash = it.hash,
                    recurringId = null,
                )
            },
        )
        return ImportResult(fresh.size, parsed.size - fresh.size)
    }

    /** Trägt fällige feste Buchungen (z. B. Taschengeld) automatisch ein. */
    suspend fun generateRecurring(today: LocalDate = LocalDate.now()) {
        dao.activeRecurring().forEach { r ->
            val last = r.lastGeneratedEpochDay?.let(LocalDate::ofEpochDay) ?: today.minusDays(1)
            var month = YearMonth.from(last)
            var lastGenerated = r.lastGeneratedEpochDay
            repeat(24) {
                val due = month.atDay(minOf(r.dayOfMonth, month.lengthOfMonth()))
                if (due.isAfter(last) && !due.isAfter(today)) {
                    if (dao.countRecurringOn(r.id, due.toEpochDay()) == 0) {
                        if (r.category == "Sparen" && r.title.startsWith("Sparen: ")) {
                            val goalTitle = r.title.removePrefix("Sparen: ")
                            dao.observeSavings().first().firstOrNull { it.title == goalTitle }?.let { g ->
                                dao.upsertSavings(g.copy(savedCents = g.savedCents - r.amountCents))
                            }
                        }
                        dao.upsertTransaction(
                            TransactionEntity(
                                accountId = r.accountId,
                                dateEpochDay = due.toEpochDay(),
                                amountCents = r.amountCents,
                                counterparty = r.title,
                                description = "Automatisch (fest)",
                                category = r.category,
                                importHash = null,
                                recurringId = r.id,
                            ),
                        )
                    }
                    lastGenerated = due.toEpochDay()
                }
                month = month.plusMonths(1)
                if (month.atDay(1).isAfter(today)) return@repeat
            }
            if (lastGenerated != r.lastGeneratedEpochDay) dao.upsertRecurring(r.copy(lastGeneratedEpochDay = lastGenerated))
        }
    }

    suspend fun saveAccount(a: AccountEntity) = dao.upsertAccount(a)
    suspend fun saveBudget(category: String, limitCents: Long) =
        if (limitCents <= 0) dao.deleteBudget(category) else dao.upsertBudget(BudgetEntity(category, limitCents))
    suspend fun saveRecurring(r: RecurringEntity) = dao.upsertRecurring(r)
    suspend fun deleteRecurring(id: Long) = dao.deleteRecurring(id)
    suspend fun saveSavings(g: SavingsGoalEntity) = dao.upsertSavings(g)
    suspend fun deleteSavings(id: Long) = dao.deleteSavings(id)
    suspend fun setBuffer(cents: Long) = settings.setBufferCents(cents)
    suspend fun accounts() = dao.accounts()
    suspend fun firstAccountId(): Long? = dao.accounts().firstOrNull()?.id
    suspend fun budgetsNow(): List<BudgetStatus> = overview().first().budgets
}

/** 1234 → „12,34 €“ */
fun Long.euro(): String {
    val sign = if (this < 0) "−" else ""
    val abs = kotlin.math.abs(this)
    return "$sign%d,%02d €".format(abs / 100, abs % 100).replace(Regex("(\\d)(?=(\\d{3})+,)"), "$1.")
}

/** „12,50“ / „12.5“ / „12“ → 1250 */
fun parseEuro(text: String): Long? {
    val t = text.trim().replace("€", "").replace(" ", "")
    val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return normalized.toDoubleOrNull()?.let { kotlin.math.round(it * 100).toLong() }
}
