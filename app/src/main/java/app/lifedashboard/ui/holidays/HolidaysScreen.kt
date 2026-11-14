package app.lifedashboard.ui.holidays

import app.lifedashboard.ui.components.wideItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.HolidayKind
import app.lifedashboard.data.db.SchoolHolidayEntity
import app.lifedashboard.data.repository.SchoolCalendarRepository
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.pickDate
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.ChoiceChips
import app.lifedashboard.ui.editors.ValueButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HolidaysViewModel @Inject constructor(private val repo: SchoolCalendarRepository) : ViewModel() {
    val holidays: StateFlow<List<SchoolHolidayEntity>> = repo.holidays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(item: SchoolHolidayEntity) { viewModelScope.launch { repo.save(item) } }
    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
}

private fun HolidayKind.label() = when (this) {
    HolidayKind.SCHOOL_HOLIDAY -> "Ferien"
    HolidayKind.PUBLIC_HOLIDAY -> "Feiertag"
    HolidayKind.SCHOOL_FREE -> "Schulfrei"
}

@Composable
fun HolidaysScreen(onBack: () -> Unit, viewModel: HolidaysViewModel = hiltViewModel()) {
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SchoolHolidayEntity?>(null) }
    OneUiScaffold(
        title = "Ferien & freie Tage",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
        floatingActionButton = {
            AddFab {
                val today = LocalDate.now().toEpochDay()
                editing = SchoolHolidayEntity(name = "Studientag", startEpochDay = today, endEpochDay = today, kind = HolidayKind.SCHOOL_FREE)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                wideItem {
                    Text(
                        "Berliner Ferien und Feiertage sind eingetragen (ohne Gewähr – bitte mit dem Schulkalender abgleichen). " +
                            "Zusätzliche freie Tage wie Studientage mit „Neu“ eintragen. An diesen Tagen gibt es keinen Unterricht im Plan, " +
                            "und reine Ferienwochen zählen bei A/B nicht mit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Erinnerung: Ferien fürs nächste Schuljahr eintragen, bevor sie auslaufen (A/B-Woche und Planung hängen daran)
                val coveredUntil = holidays.filter { it.kind == app.lifedashboard.data.db.HolidayKind.SCHOOL_HOLIDAY }
                    .maxOfOrNull { it.endEpochDay }?.let(java.time.LocalDate::ofEpochDay)
                if (coveredUntil != null && coveredUntil.isBefore(java.time.LocalDate.now().plusDays(150))) {
                    wideItem {
                        app.lifedashboard.ui.components.SectionCard {
                            Text(
                                "Ferien sind nur bis ${coveredUntil.dayOfMonth}.${coveredUntil.monthValue}.${coveredUntil.year} eingetragen. " +
                                    "Trag die Ferien fürs nächste Schuljahr ein (berlin.de → Ferientermine), sonst stimmen A/B-Woche und Planung danach nicht.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (holidays.any { it.name.contains("vorläufig") }) {
                    wideItem {
                        Text(
                            "„vorläufig“ = aus der KMK-Übersicht, noch nicht von der Schule bestätigt. Bei Abweichung antippen und ändern.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(holidays, key = { it.id }) { h ->
                    SectionCard(onClick = { editing = h }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.name, style = MaterialTheme.typography.bodyLarge)
                                val start = LocalDate.ofEpochDay(h.startEpochDay)
                                val end = LocalDate.ofEpochDay(h.endEpochDay)
                                Text(
                                    h.kind.label() + " · " + if (start == end) start.friendly() else "${start.friendly()} – ${end.friendly()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(h.id) }) { Icon(Icons.Rounded.Delete, "Löschen") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { item ->
        val context = LocalContext.current
        var name by remember(item) { mutableStateOf(item.name) }
        var start by remember(item) { mutableStateOf(LocalDate.ofEpochDay(item.startEpochDay)) }
        var end by remember(item) { mutableStateOf(LocalDate.ofEpochDay(item.endEpochDay)) }
        var kind by remember(item) { mutableStateOf(item.kind) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (item.id == 0L) "Freier Tag / Ferien" else item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ChoiceChips(HolidayKind.entries.map { it to it.label() }, kind) { kind = it }
                    ValueButton("Von", start.friendly(), { context.pickDate(start) { start = it; if (end.isBefore(it)) end = it } }, Modifier.fillMaxWidth())
                    ValueButton("Bis", end.friendly(), { context.pickDate(end) { end = it } }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !end.isBefore(start), onClick = {
                    viewModel.save(item.copy(name = name.trim(), startEpochDay = start.toEpochDay(), endEpochDay = end.toEpochDay(), kind = kind))
                    editing = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    if (item.id != 0L) TextButton(onClick = { viewModel.delete(item.id); editing = null }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("Abbrechen") }
                }
            },
        )
    }
}
