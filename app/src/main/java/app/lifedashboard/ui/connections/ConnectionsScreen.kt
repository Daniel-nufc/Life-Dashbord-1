package app.lifedashboard.ui.connections

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.IcalSourceEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.data.sync.IcalSyncRepository
import app.lifedashboard.data.sync.SystemCalendar
import app.lifedashboard.data.sync.SystemCalendarRepository
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.listPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val systemCalendar: SystemCalendarRepository,
    private val ical: IcalSyncRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _calendars = MutableStateFlow<List<SystemCalendar>>(emptyList())
    val calendars: StateFlow<List<SystemCalendar>> = _calendars.asStateFlow()
    val selectedIds: StateFlow<Set<Long>> = settings.settings.map { it.systemCalendarIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val sources: StateFlow<List<IcalSourceEntity>> = ical.all
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun hasCalendarPermission() = systemCalendar.hasPermission()

    fun loadCalendars() {
        viewModelScope.launch { _calendars.value = systemCalendar.calendars() }
    }

    fun toggleCalendar(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            val current = selectedIds.value
            settings.setSystemCalendarIds(if (enabled) current + id else current - id)
        }
    }

    fun addSource(name: String, url: String, asTasks: Boolean) {
        viewModelScope.launch {
            val id = ical.save(IcalSourceEntity(name = name.trim(), url = url.trim(), importAsTasks = asTasks, lastSyncMillis = null, lastError = null))
            _syncing.value = true
            ical.sync(IcalSourceEntity(id = id, name = name.trim(), url = url.trim(), importAsTasks = asTasks, lastSyncMillis = null, lastError = null))
            _syncing.value = false
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _syncing.value = true
            ical.syncAll()
            _syncing.value = false
        }
    }

    fun delete(source: IcalSourceEntity) {
        viewModelScope.launch { ical.delete(source) }
    }
}

@Composable
fun ConnectionsScreen(onBack: () -> Unit, viewModel: ConnectionsViewModel = hiltViewModel()) {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val selected by viewModel.selectedIds.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    var permission by remember { mutableStateOf(viewModel.hasCalendarPermission()) }
    var showAdd by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) viewModel.loadCalendars()
    }
    LaunchedEffect(permission) { if (permission) viewModel.loadCalendars() }

    OneUiScaffold(
        title = "Kalender & Abos",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.CalendarMonth, "Handy-Kalender", LifeArea.PRIVATE.color)
                        Text(
                            "Termine aus deinem Samsung- oder Google-Kalender erscheinen in „Heute“ und im Kalender und werden bei der Planung freigehalten. Die App liest nur, sie ändert nichts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!permission) {
                            Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Zugriff auf Kalender erlauben")
                            }
                        } else if (calendars.isEmpty()) {
                            Text("Keine Kalender gefunden.", modifier = Modifier.padding(top = 8.dp))
                        }
                        calendars.forEach { cal ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                app.lifedashboard.ui.components.CheckMark(checked = cal.id in selected, onCheckedChange = { viewModel.toggleCalendar(cal.id, it) })
                                Box(Modifier.size(12.dp).background(Color(cal.color), CircleShape))
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                                    if (cal.account.isNotBlank()) {
                                        Text(cal.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.CloudSync, "Kalender-Abos (Lernraum, IServ, WebUntis)", LifeArea.SCHOOL.color) {
                            IconButton(onClick = viewModel::syncAll, enabled = !syncing) { Icon(Icons.Rounded.Sync, "Jetzt aktualisieren") }
                        }
                        Text(
                            "Viele Schulplattformen bieten einen Kalender-Link (iCal) an. Damit landen Abgaben automatisch als Aufgaben in der App – alle 6 Stunden aktualisiert.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (syncing) Text("Wird aktualisiert …", modifier = Modifier.padding(top = 8.dp))
                        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.padding(top = 12.dp)) { Text("Abo hinzufügen") }
                    }
                }
                items(sources, key = { it.id }) { source ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (source.importAsTasks) "→ als Aufgaben" else "→ als Termine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val status = when {
                                    source.lastError != null -> "Fehler: ${source.lastError}"
                                    source.lastSyncMillis != null -> "Zuletzt: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(source.lastSyncMillis))
                                    else -> "Noch nicht synchronisiert"
                                }
                                Text(
                                    status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (source.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.delete(source) }) { Icon(Icons.Rounded.Delete, "Abo löschen") }
                        }
                    }
                }
                item {
                    SectionCard(title = "Wo finde ich den Link?") {
                        listOf(
                            "Lernraum Berlin (Moodle): Kalender öffnen → „Kalender exportieren“ bzw. „Importieren oder exportieren“ → Ereignisse „Alle Kurse“, Zeitraum „Kürzlich und nächste 60 Tage“ → „Kalender-URL abrufen“ → Link kopieren.",
                            "IServ: Modul „Kalender“ öffnen und nach „Abonnieren“, „Exportieren“ oder „iCal“ suchen (nicht jede Schule hat das freigeschaltet).",
                            "WebUntis: Im Profil unter „Freigaben“ nach einem Kalender-/iCal-Link suchen (falls deine Schule das erlaubt).",
                            "Der Link ist wie ein Passwort – teile ihn mit niemandem.",
                        ).forEach {
                            Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("Lernraum") }
        var url by remember { mutableStateOf("") }
        var asTasks by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Kalender-Abo hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it }, label = { Text("Link (https://… oder webcal://…)") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Als Aufgaben (Abgaben) übernehmen", modifier = Modifier.weight(1f))
                        Switch(checked = asTasks, onCheckedChange = { asTasks = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && (url.startsWith("http") || url.startsWith("webcal")),
                    onClick = {
                        viewModel.addSource(name, url, asTasks)
                        showAdd = false
                    },
                ) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Abbrechen") } },
        )
    }
}
