package app.lifedashboard.ui.finance

import app.lifedashboard.ui.components.wideItem
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RecurringEntity
import app.lifedashboard.data.db.SavingsGoalEntity
import app.lifedashboard.data.db.TransactionEntity
import app.lifedashboard.data.repository.FinanceOverview
import app.lifedashboard.data.repository.FinanceRepository
import app.lifedashboard.data.repository.euro
import app.lifedashboard.data.repository.parseEuro
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.pickDate
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.ChoiceChips
import app.lifedashboard.ui.editors.FieldLabel
import app.lifedashboard.ui.editors.ValueButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repo: FinanceRepository,
    etsy: app.lifedashboard.data.repository.EtsyRepository,
    jobs: app.lifedashboard.data.repository.JobRepository,
    settings: app.lifedashboard.data.settings.SettingsRepository,
) : ViewModel() {
    val overview: StateFlow<FinanceOverview?> = repo.overview().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sparplan: Sparziele gegen Taschengeld, Job-Verdienst und Etsy-Gewinn dieses Monats */
    val savingsPlan: StateFlow<app.lifedashboard.planner.SavingsPlan?> = run {
        val month = java.time.YearMonth.now()
        kotlinx.coroutines.flow.combine(repo.overview(), etsy.overview(), jobs.between(month.atDay(1), month.atEndOfMonth()), settings.settings) { f, e, shifts, s ->
            val job = shifts.sumOf { (it.endMinute - it.startMinute).toLong() * (it.hourlyWageCents?.toLong() ?: s.jobWageCents) / 60 }
            val soldItems = e.sales.sumOf { it.quantity }
            val allNet = e.sales.sumOf { it.priceCents * it.quantity } - e.sales.sumOf {
                app.lifedashboard.planner.EtsyMath.fees(
                    app.lifedashboard.planner.EtsySaleData(java.time.LocalDate.ofEpochDay(it.dateEpochDay), it.orderRef, it.title, it.priceCents, it.quantity), e.fees,
                )
            }
            app.lifedashboard.planner.SavingsPlan(
                neededCents = f.savingsRates.sumOf { it.second },
                fixedIncomeCents = f.monthlyIncomeCents,
                jobCents = job,
                etsyNetCents = e.thisMonth.netCents,
                jobWageCents = s.jobWageCents,
                etsyNetPerSaleCents = if (soldItems > 0) (allNet / soldItems).coerceAtLeast(0) else 0,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { repo.generateRecurring() }
    }

    fun dismissMessage() { _message.value = null }

    suspend fun suggest(counterparty: String) = repo.suggestCategory(counterparty, "")

    fun save(tx: TransactionEntity, learn: Boolean) = launch { repo.saveTransaction(tx, learn) }
    fun delete(id: Long) = launch { repo.deleteTransaction(id) }
    fun setBudget(category: String, cents: Long) = launch { repo.saveBudget(category, cents) }
    fun setBuffer(cents: Long) = launch { repo.setBuffer(cents) }
    fun adoptBudgets(list: List<Pair<String, Long>>) = launch { list.forEach { (c, v) -> repo.saveBudget(c, v) } }
    fun adoptRate(goal: SavingsGoalEntity, cents: Long) = launch { repo.adoptSavingsRate(goal, cents) }
    fun setStartBalance(accountId: Long, cents: Long) = launch {
        repo.accounts().firstOrNull { it.id == accountId }?.let { repo.saveAccount(it.copy(initialBalanceCents = cents)) }
    }
    fun saveRecurring(r: RecurringEntity) = launch { repo.saveRecurring(r) }
    fun deleteRecurring(id: Long) = launch { repo.deleteRecurring(id) }
    fun saveSavings(g: SavingsGoalEntity) = launch { repo.saveSavings(g) }
    fun deleteSavings(id: Long) = launch { repo.deleteSavings(id) }

    fun import(text: String, accountId: Long) = launch {
        _message.value = try {
            val r = repo.importDkbCsv(text, accountId)
            "${r.added} Umsätze importiert" + if (r.duplicates > 0) ", ${r.duplicates} waren schon da." else "."
        } catch (e: Exception) {
            "Import fehlgeschlagen: ${e.message}"
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private val MoneyGradient = listOf(Color(0xFF0F9D58), Color(0xFF1DB370), Color(0xFF3D7BF7))

@Composable
fun FinanceScreen(onBack: () -> Unit, viewModel: FinanceViewModel = hiltViewModel()) {
    val data by viewModel.overview.collectAsStateWithLifecycle()
    val savingsPlan by viewModel.savingsPlan.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var txDialog by remember { mutableStateOf<TransactionEntity?>(null) }
    var newTx by remember { mutableStateOf(false) }
    var budgetFor by remember { mutableStateOf<String?>(null) }
    var bufferDialog by remember { mutableStateOf(false) }
    var balanceFor by remember { mutableStateOf<Long?>(null) }
    var recurringDialog by remember { mutableStateOf<RecurringEntity?>(null) }
    var savingsDialog by remember { mutableStateOf<SavingsGoalEntity?>(null) }
    var newSavings by remember { mutableStateOf(false) }
    var importAccount by remember { mutableStateOf<Long?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val account = importAccount
        if (uri != null && account != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                // DKB liefert UTF-8 (neu) oder ISO-8859-1 (alt)
                val utf8 = String(bytes, Charsets.UTF_8)
                val text = if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8
                viewModel.import(text, account)
            }
        }
    }

    OneUiScaffold(
        title = "Finanzen",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
        floatingActionButton = { AddFab { newTx = true } },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingState()
            return@OneUiScaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                wideItem {
                    GradientCard(MoneyGradient) {
                        Text("VERFÜGBAR (OHNE PUFFER)", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.1.sp)
                        Text(d.availableCents.euro(), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Puffer geschützt: ${d.bufferCents.euro()} · Gesamt: ${d.totalCents.euro()}",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { bufferDialog = true },
                        )
                        Text(
                            "Prognose Monatsende: ${(d.forecastCents - d.bufferCents).euro()} verfügbar",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (d.forecastCents < d.bufferCents) {
                            Text("So würdest du an deinen Puffer gehen – etwas weniger ausgeben.", color = Color(0xFFE9B35B), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            "Diesen Monat: +${d.monthIncomeCents.euro()} / −${d.monthSpentCents.euro()}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                savingsPlan?.takeIf { it.neededCents > 0 }?.let { plan ->
                    item { SavingsPlanCard(plan) }
                }
                message?.let { msg ->
                    item {
                        SectionCard(onClick = viewModel::dismissMessage) { Text(msg) }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.AccountBalance, "Konten", LifeArea.JOB.color)
                        d.accounts.forEach { (acc, balance) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f).clickable { balanceFor = acc.id }) {
                                    Text(acc.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("Startstand antippen zum Ändern", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(balance.euro(), style = MaterialTheme.typography.titleMedium)
                                if (acc.kind == app.lifedashboard.data.db.AccountKind.BANK) {
                                    IconButton(onClick = {
                                        importAccount = acc.id
                                        importLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel", "*/*"))
                                    }) { Icon(Icons.Rounded.FileUpload, "DKB-CSV importieren") }
                                }
                            }
                        }
                        Text(
                            "CSV-Import: In der DKB-App/Banking „Umsätze → Exportieren (CSV)“, dann hier ↑ tippen. Die Datei bleibt auf deinem Handy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.PieChart, "Budgets diesen Monat", LifeArea.ETSY.color)
                        if (d.budgets.isEmpty()) {
                            Text("Noch keine Budgets. Tippe unten auf eine Kategorie, um ein Monatslimit zu setzen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        d.budgets.forEach { b ->
                            val color = when {
                                b.ratio >= 1f -> MaterialTheme.colorScheme.error
                                b.ratio >= 0.8f -> Color(0xFFF2A21D)
                                else -> LifeArea.SPORT.color
                            }
                            Column(Modifier.padding(vertical = 4.dp).clickable { budgetFor = b.category }) {
                                Row {
                                    Text(b.category, modifier = Modifier.weight(1f))
                                    Text("${b.spentCents.euro()} / ${b.limitCents.euro()}", color = color)
                                }
                                app.lifedashboard.ui.components.Meter(b.ratio.coerceAtMost(1f), color = color, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        if (d.byCategory.isNotEmpty()) {
                            Text("Ausgaben nach Kategorie", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                            d.byCategory.forEach { (cat, cents) ->
                                Row(Modifier.fillMaxWidth().clickable { budgetFor = cat }.padding(vertical = 3.dp)) {
                                    Text(cat, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(cents.euro(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                if (d.budgetSuggestions.isNotEmpty() || d.savingsRates.isNotEmpty()) {
                    item {
                        SectionCard {
                            TileHeader(Icons.Rounded.AutoAwesome, "Vorschläge der App", LifeArea.ETSY.color)
                            if (d.budgetSuggestions.isNotEmpty()) {
                                Text(
                                    if (d.transactions.isEmpty()) "Start-Budgets (noch keine Umsätze):" else "Budgets aus deinen letzten 3 Monaten:",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                d.budgetSuggestions.forEach { (c, v) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(c, Modifier.weight(1f)); Text("${v.euro()} / Monat") }
                                }
                                androidx.compose.material3.FilledTonalButton(onClick = { viewModel.adoptBudgets(d.budgetSuggestions) }, modifier = Modifier.padding(top = 6.dp)) {
                                    Text("Budgets übernehmen")
                                }
                            }
                            if (d.savingsRates.isNotEmpty()) {
                                Text("Sparraten, damit deine Ziele pünktlich klappen:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                                d.savingsRates.forEach { (g, rate) ->
                                    val already = d.recurring.any { it.title == "Sparen: ${g.title}" }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${g.title}: ${rate.euro()} / Monat", Modifier.weight(1f))
                                        if (already) Text("fest eingeplant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        else TextButton(onClick = { viewModel.adoptRate(g, rate) }) { Text("Als feste Buchung") }
                                    }
                                }
                                if (d.monthlyIncomeCents > 0) {
                                    Text(
                                        "Faustregel: 25 % deiner festen Einnahmen = ${(d.monthlyIncomeCents / 4).euro()} pro Monat sparen.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Autorenew, "Feste Buchungen & Abos", LifeArea.SCHOOL.color) {
                            TextButton(onClick = { recurringDialog = RecurringEntity(accountId = d.accounts.firstOrNull()?.first?.id ?: 0, title = "", amountCents = -500, dayOfMonth = 1, category = "Abos", active = true, lastGeneratedEpochDay = LocalDate.now().toEpochDay()) }) { Text("+ Neu") }
                        }
                        d.recurring.forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { recurringDialog = r }.padding(vertical = 4.dp)) {
                                Text("${r.title} · am ${r.dayOfMonth}.", modifier = Modifier.weight(1f), color = if (r.active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(r.amountCents.euro(), color = if (r.amountCents >= 0) LifeArea.SPORT.color else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        val known = d.recurring.map { it.title.lowercase() }
                        val detected = d.subscriptions.filter { s -> known.none { it.contains(s.name) } }
                        if (detected.isNotEmpty()) {
                            Text("Erkannte Abos (aus deinen Umsätzen):", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                            detected.forEach { s ->
                                Text("• ${s.name.replaceFirstChar { it.uppercase() }} ≈ ${s.averageCents.euro()} / Monat, nächste ca. ${s.nextExpected.friendly()}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Savings, "Sparziele", LifeArea.PRIVATE.color) {
                            TextButton(onClick = { newSavings = true }) { Text("+ Neu") }
                        }
                        if (d.savings.isEmpty()) {
                            Text("Z. B. Führerschein oder Feriencamp – aus laufendem Geld ansparen, damit der Puffer bleibt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        d.savings.forEach { g ->
                            val ratio = if (g.targetCents > 0) g.savedCents.toFloat() / g.targetCents else 0f
                            Column(Modifier.padding(vertical = 4.dp).clickable { savingsDialog = g }) {
                                Row {
                                    Text(g.title, modifier = Modifier.weight(1f))
                                    Text("${g.savedCents.euro()} / ${g.targetCents.euro()}")
                                }
                                app.lifedashboard.ui.components.Meter(ratio.coerceAtMost(1f), color = LifeArea.PRIVATE.color, modifier = Modifier.padding(vertical = 8.dp))
                                g.deadlineEpochDay?.let { dl ->
                                    val months = java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.now(), java.time.YearMonth.from(LocalDate.ofEpochDay(dl))).coerceAtLeast(1)
                                    val perMonth = ((g.targetCents - g.savedCents).coerceAtLeast(0) / months)
                                    Text("Bis ${LocalDate.ofEpochDay(dl).friendly()}: ${perMonth.euro()} pro Monat zurücklegen", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                wideItem { app.lifedashboard.ui.components.SectionTitle("Umsätze") }
                items(d.transactions.take(60), key = { it.id }) { tx ->
                    SectionCard(onClick = { txDialog = tx }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tx.counterparty.ifBlank { tx.description.take(40) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    LocalDate.ofEpochDay(tx.dateEpochDay).friendly() + " · " + (tx.category ?: "Kategorie wählen"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tx.category == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(tx.amountCents.euro(), fontWeight = FontWeight.SemiBold, color = if (tx.amountCents >= 0) LifeArea.SPORT.color else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }

    val d = data ?: return
    if (newTx || txDialog != null) {
        TransactionDialog(
            original = txDialog,
            accounts = d.accounts.map { it.first },
            categories = d.categories,
            suggest = viewModel::suggest,
            onSave = { tx, learn -> viewModel.save(tx, learn); newTx = false; txDialog = null },
            onDelete = { viewModel.delete(it); txDialog = null },
            onDismiss = { newTx = false; txDialog = null },
        )
    }
    budgetFor?.let { cat ->
        MoneyDialog("Monatsbudget: $cat", d.budgets.firstOrNull { it.category == cat }?.limitCents ?: 0, "0 = kein Budget", { viewModel.setBudget(cat, it); budgetFor = null }) { budgetFor = null }
    }
    if (bufferDialog) {
        MoneyDialog("Geschützter Puffer", d.bufferCents, "Dieses Geld zählt nie als verfügbar. Den Betrag kennt nur dein Handy.", { viewModel.setBuffer(it); bufferDialog = false }) { bufferDialog = false }
    }
    balanceFor?.let { id ->
        val acc = d.accounts.first { it.first.id == id }.first
        MoneyDialog("Startstand ${acc.name}", acc.initialBalanceCents, "Kontostand vor dem ersten eingetragenen Umsatz.", { viewModel.setStartBalance(id, it); balanceFor = null }) { balanceFor = null }
    }
    recurringDialog?.let { r ->
        RecurringDialog(r, d.accounts.map { it.first }, d.categories, { viewModel.saveRecurring(it); recurringDialog = null }, { viewModel.deleteRecurring(it); recurringDialog = null }) { recurringDialog = null }
    }
    if (newSavings || savingsDialog != null) {
        SavingsDialog(savingsDialog, { viewModel.saveSavings(it); newSavings = false; savingsDialog = null }, { viewModel.deleteSavings(it); savingsDialog = null }) { newSavings = false; savingsDialog = null }
    }
}

@Composable
private fun MoneyDialog(title: String, initial: Long, hint: String, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (initial == 0L) "" else "%d,%02d".format(initial / 100, kotlin.math.abs(initial % 100))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Betrag in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text(hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parseEuro(text.ifBlank { "0" }) ?: 0) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(categories: List<String>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
        categories.forEach { c -> FilterChip(selected = c == selected, onClick = { onSelect(c) }, label = { Text(c) }) }
    }
}

@Composable
private fun TransactionDialog(
    original: TransactionEntity?,
    accounts: List<app.lifedashboard.data.db.AccountEntity>,
    categories: List<String>,
    suggest: suspend (String) -> String?,
    onSave: (TransactionEntity, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var accountId by remember { mutableStateOf(original?.accountId ?: accounts.lastOrNull { it.kind == app.lifedashboard.data.db.AccountKind.CASH }?.id ?: accounts.firstOrNull()?.id ?: 0L) }
    var expense by remember { mutableStateOf((original?.amountCents ?: -1) < 0) }
    var amount by remember { mutableStateOf(original?.let { "%d,%02d".format(kotlin.math.abs(it.amountCents) / 100, kotlin.math.abs(it.amountCents) % 100) } ?: "") }
    var who by remember { mutableStateOf(original?.counterparty ?: "") }
    var category by remember { mutableStateOf(original?.category) }
    var categoryTouched by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(original?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: LocalDate.now()) }

    LaunchedEffect(who) {
        if (!categoryTouched && who.length >= 3) suggest(who)?.let { category = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Neuer Umsatz" else "Umsatz bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChips(listOf(true to "Ausgabe", false to "Einnahme"), expense) { expense = it }
                OutlinedTextField(amount, { amount = it }, label = { Text("Betrag in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(who, { who = it }, label = { Text("Wo / wofür? (z. B. REWE)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FieldLabel("Konto")
                ChoiceChips(accounts.map { it.id to it.name }, accountId) { accountId = it }
                ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
                FieldLabel("Kategorie" + if (!categoryTouched && category != null) " (Vorschlag)" else "")
                CategoryPicker(categories, category) {
                    category = it
                    categoryTouched = true
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parseEuro(amount) != null && accountId != 0L,
                onClick = {
                    val cents = kotlin.math.abs(parseEuro(amount) ?: 0)
                    onSave(
                        TransactionEntity(
                            id = original?.id ?: 0,
                            accountId = accountId,
                            dateEpochDay = date.toEpochDay(),
                            amountCents = if (expense) -cents else cents,
                            counterparty = who.trim(),
                            description = original?.description ?: "",
                            category = category,
                            importHash = original?.importHash,
                            recurringId = original?.recurringId,
                        ),
                        categoryTouched,
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (original != null) TextButton(onClick = { onDelete(original.id) }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun RecurringDialog(
    original: RecurringEntity,
    accounts: List<app.lifedashboard.data.db.AccountEntity>,
    categories: List<String>,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(original.title) }
    var income by remember { mutableStateOf(original.amountCents >= 0) }
    var amount by remember { mutableStateOf("%d,%02d".format(kotlin.math.abs(original.amountCents) / 100, kotlin.math.abs(original.amountCents) % 100)) }
    var day by remember { mutableStateOf(original.dayOfMonth.toString()) }
    var accountId by remember { mutableStateOf(original.accountId) }
    var category by remember { mutableStateOf(original.category) }
    var active by remember { mutableStateOf(original.active) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original.id == 0L) "Neue feste Buchung" else "Feste Buchung") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Name, z. B. Spotify") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceChips(listOf(false to "Ausgabe", true to "Einnahme"), income) { income = it }
                OutlinedTextField(amount, { amount = it }, label = { Text("Betrag in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Tag im Monat") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                FieldLabel("Konto")
                ChoiceChips(accounts.map { it.id to it.name }, accountId) { accountId = it }
                FieldLabel("Kategorie")
                CategoryPicker(categories, category) { category = it }
                ChoiceChips(listOf(true to "aktiv", false to "pausiert"), active) { active = it }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && parseEuro(amount) != null && (day.toIntOrNull() ?: 0) in 1..31, onClick = {
                val cents = kotlin.math.abs(parseEuro(amount) ?: 0)
                onSave(original.copy(title = title.trim(), amountCents = if (income) cents else -cents, dayOfMonth = day.toInt(), accountId = accountId, category = category, active = active))
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (original.id != 0L) TextButton(onClick = { onDelete(original.id) }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun SavingsDialog(original: SavingsGoalEntity?, onSave: (SavingsGoalEntity) -> Unit, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(original?.title ?: "") }
    var target by remember { mutableStateOf(original?.let { "%d".format(it.targetCents / 100) } ?: "") }
    var saved by remember { mutableStateOf(original?.let { "%d".format(it.savedCents / 100) } ?: "0") }
    var deadline by remember { mutableStateOf(original?.deadlineEpochDay?.let(LocalDate::ofEpochDay)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Neues Sparziel" else "Sparziel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Wofür? (z. B. Führerschein)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it }, label = { Text("Ziel in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(saved, { saved = it }, label = { Text("Schon zurückgelegt in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                ValueButton("Bis wann", deadline?.friendly() ?: "offen", { context.pickDate(deadline ?: LocalDate.now().plusMonths(6)) { deadline = it } }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && parseEuro(target) != null, onClick = {
                onSave(SavingsGoalEntity(id = original?.id ?: 0, title = title.trim(), targetCents = parseEuro(target) ?: 0, savedCents = parseEuro(saved) ?: 0, deadlineEpochDay = deadline?.toEpochDay()))
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (original != null) TextButton(onClick = { onDelete(original.id) }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}


/** Sparplan: Was die Sparziele pro Monat brauchen und woher es kommt (Taschengeld, Job, Etsy). */
@Composable
private fun SavingsPlanCard(plan: app.lifedashboard.planner.SavingsPlan) {
    val tones = app.lifedashboard.ui.theme.LocalAppColors.current
    SectionCard {
        TileHeader(Icons.Rounded.Savings, "Sparplan", LifeArea.PRIVATE.color) {
            Text(
                if (plan.covered) "gedeckt" else "${plan.gapCents.euro()} fehlen",
                style = MaterialTheme.typography.labelMedium,
                color = if (plan.covered) tones.positive else tones.warning,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(plan.neededCents.euro(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alignByBaseline())
            Text(
                "  pro Monat für deine Sparziele",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        app.lifedashboard.ui.components.Meter(
            plan.fraction,
            color = if (plan.covered) tones.positive else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
        )
        listOf(
            "Taschengeld & feste Einnahmen" to plan.fixedIncomeCents,
            "Job diesen Monat" to plan.jobCents,
            "Etsy-Gewinn diesen Monat" to plan.etsyNetCents,
        ).forEach { (label, cents) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(cents.euro(), style = MaterialTheme.typography.labelLarge, color = if (cents > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val hint = when {
            plan.covered -> "Passt: Deine Einnahmen decken die Sparraten. Der Rest ist frei verfügbar."
            else -> {
                val ways = listOfNotNull(
                    plan.jobHoursForGap?.let { "$it Job-Stunden" },
                    plan.etsySalesForGap?.let { "$it Etsy-Verkäufe" },
                )
                "Die Lücke schließt du mit etwa " + (if (ways.isEmpty()) "${plan.gapCents.euro()} zusätzlich" else ways.joinToString(" oder ")) +
                    " pro Monat." + (if (plan.etsySalesForGap == null) " Sobald dein Shop Verkäufe hat, rechnet die App hier mit deinem echten Gewinn pro Verkauf." else "")
            }
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
