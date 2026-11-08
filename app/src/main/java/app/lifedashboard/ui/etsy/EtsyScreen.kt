package app.lifedashboard.ui.etsy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.lifedashboard.data.db.DesignStatus
import app.lifedashboard.data.db.EtsyDesignEntity
import app.lifedashboard.data.db.EtsySaleEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.repository.EtsyOverview
import app.lifedashboard.data.repository.EtsyRepository
import app.lifedashboard.data.repository.euro
import app.lifedashboard.data.repository.label
import app.lifedashboard.data.repository.nextStepHint
import app.lifedashboard.data.repository.parseEuro
import app.lifedashboard.planner.EtsyFees
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
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EtsyViewModel @Inject constructor(private val repo: EtsyRepository) : ViewModel() {
    val overview: StateFlow<EtsyOverview?> = repo.overview().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun advance(d: EtsyDesignEntity) = launch {
        val next = DesignStatus.entries.getOrNull(d.status.ordinal + 1) ?: return@launch
        repo.saveDesign(d.copy(status = next))
    }
    fun saveDesign(d: EtsyDesignEntity) = launch { repo.saveDesign(d) }
    fun deleteDesign(id: Long) = launch { repo.deleteDesign(id) }
    fun saveSale(s: EtsySaleEntity) = launch { repo.saveSale(s) }
    fun deleteSale(id: Long) = launch { repo.deleteSale(id) }
    fun saveStats(views: Int, favs: Int) = launch { repo.saveStats(YearMonth.now(), views, favs) }
    fun saveFees(f: EtsyFees) = launch { repo.saveFees(f) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private val EtsyGradient = listOf(Color(0xFFF26B1D), Color(0xFFE53935), Color(0xFFC04FD8))

private val uploadChecklist = listOf(
    "KI-Häkchen setzen und „Designed by“ wählen",
    "Motiv selbst geprüft (Schärfe, Ränder, Farben) – keine reinen Bot-Listings",
    "Keine Namen von Figuren, Marken, Filmen oder Shops in Titel/Tags",
    "Nur eigene Text-Prompts, keine Vorlagebilder, keine Canva-Bibliotheksgrafiken",
    "Urheberrechts-Nachweis (Prompt, Datum, Asset-ID) liegt vor",
    "Datei druckfertig: 11 oz = 2850 × 1200 px PNG, weißer Hintergrund",
    "„15oz“ erst im Titel nennen, wenn die 15-oz-Datei dabei ist",
)

@Composable
fun EtsyScreen(onBack: () -> Unit, onOpenTasks: () -> Unit, viewModel: EtsyViewModel = hiltViewModel()) {
    val data by viewModel.overview.collectAsStateWithLifecycle()
    var designDialog by remember { mutableStateOf<EtsyDesignEntity?>(null) }
    var newDesign by remember { mutableStateOf(false) }
    var saleDialog by remember { mutableStateOf<EtsySaleEntity?>(null) }
    var newSale by remember { mutableStateOf(false) }
    var statsDialog by remember { mutableStateOf(false) }
    var feesDialog by remember { mutableStateOf(false) }

    OneUiScaffold(
        title = "Etsy-Shop",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
        actions = { IconButton(onClick = { feesDialog = true }) { Icon(Icons.Rounded.Settings, "Gebühren einstellen") } },
        floatingActionButton = { AddFab { newSale = true } },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingState()
            return@OneUiScaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GradientCard(EtsyGradient) {
                        Text("Diesen Monat", color = Color.White.copy(alpha = 0.85f))
                        Text(d.thisMonth.revenueCents.euro() + " Umsatz", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Netto nach Gebühren: ${d.thisMonth.netCents.euro()} · ${d.thisMonth.orders} Bestellungen",
                            color = Color.White,
                        )
                        Text(
                            d.revenueChange?.let { (if (it >= 0) "▲ +" else "▼ ") + "$it % zum Vormonat" } ?: "Vormonat: ${d.lastMonth.revenueCents.euro()}",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("💡 ${d.tip}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Visibility, "Aufrufe & Favoriten", LifeArea.ETSY.color) {
                            TextButton(onClick = { statsDialog = true }) { Text("Eintragen") }
                        }
                        val current = d.stats.firstOrNull { it.monthKey == YearMonth.now().toString() }
                        val previous = d.stats.firstOrNull { it.monthKey == YearMonth.now().minusMonths(1).toString() }
                        if (current == null) {
                            Text("Noch nichts eingetragen – Zahlen aus der Etsy-Statistik übernehmen (bis die API verbunden ist).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("👁 ${current.views} Aufrufe · ❤️ ${current.favorites} Favoriten" + (previous?.let { " (Vormonat ${it.views} / ${it.favorites})" } ?: ""))
                            if (current.views > 0) {
                                Text(
                                    "Conversion: %.1f %% der Aufrufe kaufen".format(d.thisMonth.orders * 100.0 / current.views),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Brush, "Motiv-Pipeline", LifeArea.ETSY.color) {
                            Pill("${d.online} online", LifeArea.ETSY.color)
                            TextButton(onClick = { newDesign = true }) { Text("+ Motiv") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                            DesignStatus.entries.forEach { status ->
                                val count = d.designs.count { it.status == status }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$count", style = MaterialTheme.typography.titleMedium, color = if (count > 0) LifeArea.ETSY.color else MaterialTheme.colorScheme.outline)
                                    Text(status.label(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                        d.designs.groupBy { it.setName }.forEach { (set, list) ->
                            Text(set, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                            list.forEach { design ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { designDialog = design }.padding(vertical = 4.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(design.title, style = MaterialTheme.typography.bodyLarge)
                                        Text("${design.status.label()} → ${design.status.nextStepHint()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (design.status != DesignStatus.ONLINE) {
                                        FilledTonalButton(onClick = { viewModel.advance(design) }) { Text("✓ weiter") }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = onOpenTasks) { Text("Etsy-To-dos ansehen") }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Checklist, "Upload-Checkliste", LifeArea.SCHOOL.color)
                        uploadChecklist.forEach { Text("☐  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
                if (d.top.isNotEmpty()) {
                    item {
                        SectionCard(title = "Top-Produkte") {
                            d.top.forEach { (title, cents) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(title, modifier = Modifier.weight(1f))
                                    Text(cents.euro())
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Link, "Etsy-API verbinden", LifeArea.ETSY.color)
                        Text(
                            "Sobald dein Shop live ist, können Bestellungen und Statistiken automatisch kommen (Etsy Open API v3, nur lesen). Die Schritt-für-Schritt-Anleitung steht im Projekt unter docs/ETSY_API.md. Bis dahin trägst du Verkäufe mit „Neu“ ein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item { Text("Verkäufe", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp)) }
                if (d.sales.isEmpty()) {
                    item { SectionCard { Text("Noch keine Verkäufe – das erste kommt! 🎉", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
                items(d.sales.take(50), key = { it.id }) { sale ->
                    SectionCard(onClick = { saleDialog = sale }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sale.title, style = MaterialTheme.typography.bodyLarge)
                                Text("${LocalDate.ofEpochDay(sale.dateEpochDay).friendly()} · Bestellung ${sale.orderRef}" + if (sale.quantity > 1) " · ${sale.quantity}×" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text((sale.priceCents * sale.quantity).euro(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    val d = data ?: return
    if (newDesign || designDialog != null) {
        DesignDialog(designDialog, d.designs.map { it.setName }.distinct(), { viewModel.saveDesign(it); newDesign = false; designDialog = null }, { viewModel.deleteDesign(it); designDialog = null }) { newDesign = false; designDialog = null }
    }
    if (newSale || saleDialog != null) {
        SaleDialog(saleDialog, d.designs.map { it.setName } .distinct() + d.designs.map { it.title }, { viewModel.saveSale(it); newSale = false; saleDialog = null }, { viewModel.deleteSale(it); saleDialog = null }) { newSale = false; saleDialog = null }
    }
    if (statsDialog) {
        var views by remember { mutableStateOf("") }
        var favs by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { statsDialog = false },
            title = { Text("Statistik ${YearMonth.now()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(views, { views = it.filter(Char::isDigit) }, label = { Text("Aufrufe") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(favs, { favs = it.filter(Char::isDigit) }, label = { Text("Favoriten") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveStats(views.toIntOrNull() ?: 0, favs.toIntOrNull() ?: 0); statsDialog = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { statsDialog = false }) { Text("Abbrechen") } },
        )
    }
    if (feesDialog) FeesDialog(d.fees, { viewModel.saveFees(it); feesDialog = false }) { feesDialog = false }
}

@Composable
private fun DesignDialog(original: EtsyDesignEntity?, sets: List<String>, onSave: (EtsyDesignEntity) -> Unit, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(original?.title ?: "") }
    var set by remember { mutableStateOf(original?.setName ?: sets.firstOrNull() ?: "Neues Set") }
    var status by remember { mutableStateOf(original?.status ?: DesignStatus.IDEA) }
    var canva by remember { mutableStateOf(original?.canvaRef ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Neues Motiv" else "Motiv") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Motiv") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(set, { set = it }, label = { Text("Set / Thema") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FieldLabel("Status")
                ChoiceChips(DesignStatus.entries.map { it to it.label() }, status) { status = it }
                OutlinedTextField(canva, { canva = it }, label = { Text("Canva-Design / Seite (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(EtsyDesignEntity(id = original?.id ?: 0, setName = set.trim().ifBlank { "Ohne Set" }, title = title.trim(), status = status, canvaRef = canva.trim().ifBlank { null }, note = note.trim().ifBlank { null }, createdAtMillis = original?.createdAtMillis ?: System.currentTimeMillis()))
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

@Composable
private fun SaleDialog(original: EtsySaleEntity?, suggestions: List<String>, onSave: (EtsySaleEntity) -> Unit, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(original?.title ?: suggestions.firstOrNull() ?: "") }
    var price by remember { mutableStateOf(original?.let { "%d,%02d".format(it.priceCents / 100, it.priceCents % 100) } ?: "") }
    var quantity by remember { mutableStateOf((original?.quantity ?: 1).toString()) }
    var order by remember { mutableStateOf(original?.orderRef ?: "") }
    var date by remember { mutableStateOf(original?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Verkauf eintragen" else "Verkauf") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Produkt") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (suggestions.isNotEmpty()) ChoiceChips(suggestions.distinct().take(6).map { it to it }, title) { title = it }
                OutlinedTextField(price, { price = it }, label = { Text("Preis pro Stück in €") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, label = { Text("Anzahl") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(order, { order = it }, label = { Text("Bestellnummer (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && parseEuro(price) != null, onClick = {
                onSave(EtsySaleEntity(id = original?.id ?: 0, dateEpochDay = date.toEpochDay(), orderRef = order.trim().ifBlank { "#${System.currentTimeMillis() % 100000}" }, title = title.trim(), priceCents = parseEuro(price) ?: 0, quantity = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1))
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

@Composable
private fun FeesDialog(fees: EtsyFees, onSave: (EtsyFees) -> Unit, onDismiss: () -> Unit) {
    var listing by remember { mutableStateOf("%d,%02d".format(fees.listingFeeCents / 100, fees.listingFeeCents % 100)) }
    var transaction by remember { mutableStateOf(fees.transactionPercent.toString().replace('.', ',')) }
    var processing by remember { mutableStateOf(fees.processingPercent.toString().replace('.', ',')) }
    var fixed by remember { mutableStateOf("%d,%02d".format(fees.processingFixedCents / 100, fees.processingFixedCents % 100)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etsy-Gebühren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Standardwerte – bitte mit deiner ersten Etsy-Abrechnung abgleichen.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(listing, { listing = it }, label = { Text("Einstellgebühr pro Stück (€)") }, singleLine = true)
                OutlinedTextField(transaction, { transaction = it }, label = { Text("Transaktionsgebühr (%)") }, singleLine = true)
                OutlinedTextField(processing, { processing = it }, label = { Text("Zahlungsabwicklung (%)") }, singleLine = true)
                OutlinedTextField(fixed, { fixed = it }, label = { Text("Zahlungsabwicklung fix (€)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    EtsyFees(
                        listingFeeCents = parseEuro(listing) ?: fees.listingFeeCents,
                        transactionPercent = transaction.replace(',', '.').toDoubleOrNull() ?: fees.transactionPercent,
                        processingPercent = processing.replace(',', '.').toDoubleOrNull() ?: fees.processingPercent,
                        processingFixedCents = parseEuro(fixed) ?: fees.processingFixedCents,
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
