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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.IcalSourceEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.data.sync.IcalSyncRepository
import app.lifedashboard.data.sync.isFamilyWall
import app.lifedashboard.data.sync.PersonStats
import app.lifedashboard.data.sync.SystemCalendar
import app.lifedashboard.data.sync.SystemCalendarRepository
import app.lifedashboard.data.sync.TitleItem
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    val userName: StateFlow<String> = settings.settings.map { it.userName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val onlyMineIds: StateFlow<Set<Long>> = settings.settings.map { it.onlyMineCalendarIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** „Nur meine Termine“: Auswertung der nächsten 31 Tage je Kalender. */
    private val _stats = MutableStateFlow<Map<Long, PersonStats>>(emptyMap())
    val stats: StateFlow<Map<Long, PersonStats>> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings.distinctUntilChanged { a, b ->
                a.systemCalendarIds == b.systemCalendarIds && a.onlyMineCalendarIds == b.onlyMineCalendarIds &&
                    a.userName == b.userName && a.mineTitles == b.mineTitles && a.notMineTitles == b.notMineTitles
            }.collectLatest { refreshStats(it) }
        }
    }

    private suspend fun refreshStats(s: app.lifedashboard.data.settings.AppSettings) {
        _stats.value = (s.systemCalendarIds intersect s.onlyMineCalendarIds)
            .associateWith { systemCalendar.personStats(it, s.userName, s.mineTitles, s.notMineTitles) }
        // offene Zuordnungsliste aktuell halten
        titlesFor?.let { id -> _titles.value = systemCalendar.titles(id, s.userName, s.mineTitles, s.notMineTitles) }
    }

    fun hasCalendarPermission() = systemCalendar.hasPermission()

    fun loadCalendars() {
        viewModelScope.launch {
            _calendars.value = systemCalendar.calendars()
            refreshStats(settings.settings.first())
        }
    }

    fun toggleCalendar(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            val current = selectedIds.value
            settings.setSystemCalendarIds(if (enabled) current + id else current - id)
            // FamilyWall-Familienkalender: standardmäßig nur meine Termine
            if (enabled && _calendars.value.any { it.id == id && it.isFamilyWall }) settings.setOnlyMineCalendarIds(onlyMineIds.value + id)
        }
    }

    /** „Termine zuordnen“: die Titel der nächsten 31 Tage, jeweils meins / nicht meins. */
    private var titlesFor: Long? = null
    private val _titles = MutableStateFlow<List<TitleItem>?>(null)
    val titles: StateFlow<List<TitleItem>?> = _titles.asStateFlow()

    fun openTitles(calendarId: Long) {
        titlesFor = calendarId
        viewModelScope.launch {
            val s = settings.settings.first()
            _titles.value = systemCalendar.titles(calendarId, s.userName, s.mineTitles, s.notMineTitles)
        }
    }

    fun closeTitles() {
        titlesFor = null
        _titles.value = null
    }

    /** Merkt sich die Wahl für den Titel – gilt für alle Termine mit diesem Titel, auch künftige. */
    fun choose(title: String, mine: Boolean?) {
        viewModelScope.launch { settings.setTitleChoice(title, mine) }
    }

    fun setOnlyMine(id: Long, on: Boolean) {
        viewModelScope.launch { settings.setOnlyMineCalendarIds(if (on) onlyMineIds.value + id else onlyMineIds.value - id) }
    }

    /** FamilyWall über den Handy-Kalender (vollständig, immer aktuell). Ein alter iCal-Link wird entfernt, damit nichts doppelt ist. */
    fun useFamilyWallCalendar(calendar: SystemCalendar) {
        viewModelScope.launch {
            settings.setSystemCalendarIds(selectedIds.value + calendar.id)
            settings.setOnlyMineCalendarIds(onlyMineIds.value + calendar.id)
            sources.value.filter(::isFamilyWall).forEach { ical.delete(it) }
        }
    }

    fun addSource(name: String, url: String, asTasks: Boolean, area: LifeArea = LifeArea.SCHOOL) {
        viewModelScope.launch {
            val source = IcalSourceEntity(
                name = name.trim(), url = url.trim(), importAsTasks = asTasks, lastSyncMillis = null, lastError = null,
                area = if (asTasks) LifeArea.SCHOOL else area,
            )
            val id = ical.save(source)
            _syncing.value = true
            ical.sync(source.copy(id = id))
            _syncing.value = false
        }
    }

    /** Neuen FamilyWall-Link übernehmen (ersetzt einen alten, z. B. nach „iCal neu generieren“). */
    fun connectFamilyWall(url: String, onlyFor: String?) {
        viewModelScope.launch {
            val old = sources.value.firstOrNull(::isFamilyWall)
            val source = (old ?: IcalSourceEntity(name = "FamilyWall", url = "", importAsTasks = false, lastSyncMillis = null, lastError = null, area = LifeArea.PRIVATE))
                .copy(url = url.trim(), lastError = null, filterName = onlyFor?.trim()?.ifBlank { null })
            val id = ical.save(source)
            _syncing.value = true
            ical.sync(source.copy(id = if (old != null) old.id else id))
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
    val onlyMine by viewModel.onlyMineIds.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    var permission by remember { mutableStateOf(viewModel.hasCalendarPermission()) }
    var showAdd by remember { mutableStateOf(false) }
    var showFamilyWall by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

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
            app.lifedashboard.ui.components.AdaptiveList(padding) {
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
                                    if (cal.id in selected && cal.id in onlyMine) {
                                        Text("Nur Termine für $userName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    val family = sources.firstOrNull(::isFamilyWall)
                    val fwCalendar = calendars.firstOrNull { it.isFamilyWall }
                    val usingDevice = fwCalendar != null && fwCalendar.id in selected
                    val hint = @Composable { text: String ->
                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    SectionCard {
                        TileHeader(Icons.Rounded.FamilyRestroom, "FamilyWall", LifeArea.PRIVATE.color) {
                            if (family != null && !usingDevice) IconButton(onClick = viewModel::syncAll, enabled = !syncing) { Icon(Icons.Rounded.Sync, "Jetzt aktualisieren") }
                        }
                        Text(
                            "Deine Termine aus dem Familienkalender erscheinen automatisch in „Heute“ und im Kalender, auch Serien wie Geburtstage. Die App plant nichts in diese Zeiten. Sie liest nur, in FamilyWall ändert sich nichts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            usingDevice -> {
                                Text(
                                    "Verbunden über den Handy-Kalender „${fwCalendar!!.name}“ · immer aktuell",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                                val mine = fwCalendar.id in onlyMine
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    Text("Nur Termine für $userName", modifier = Modifier.weight(1f))
                                    Switch(checked = mine, onCheckedChange = { viewModel.setOnlyMine(fwCalendar.id, it) })
                                }
                                val st = stats[fwCalendar.id]
                                if (mine && st != null) {
                                    hint(
                                        "Nächste 31 Tage: ${st.kept} von ${st.total} Terminen sind für dich." +
                                            if (st.people.isNotEmpty()) " Personen in FamilyWall: ${st.people.joinToString(", ")}" else "",
                                    )
                                    if (st.openTitles > 0) {
                                        Text(
                                            "${st.openTitles} ${if (st.openTitles == 1) "Termin ist" else "Termine sind"} noch nicht zugeordnet – gehören sie dir? FamilyWall sagt dem Handy nicht, wer dabei ist, deshalb sagst du es einmal. Die App merkt sich den Titel für alle künftigen Termine.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    if (mine) {
                                        Button(onClick = { viewModel.openTitles(fwCalendar.id) }) { Text("Termine zuordnen") }
                                    }
                                    TextButton(onClick = { viewModel.toggleCalendar(fwCalendar.id, false) }) { Text("Nicht mehr anzeigen") }
                                }
                            }
                            fwCalendar != null -> {
                                hint("Auf diesem Gerät gefunden: „${fwCalendar.name}“. Die FamilyWall-App überträgt dorthin alle Termine – vollständig und immer aktuell, ohne Link.")
                                Button(onClick = { viewModel.useFamilyWallCalendar(fwCalendar) }, modifier = Modifier.padding(top = 10.dp)) {
                                    Text("Übernehmen – nur meine Termine")
                                }
                            }
                            !permission -> {
                                hint("FamilyWall überträgt die Termine in den Handy-Kalender. Erlaube den Zugriff, dann findet die App sie.")
                                Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }, modifier = Modifier.padding(top = 10.dp)) {
                                    Text("Zugriff auf Kalender erlauben")
                                }
                            }
                            family == null -> {
                                hint("Kein FamilyWall-Kalender auf diesem Gerät. Schalte in FamilyWall die Übertragung in den Handy-Kalender ein – oder nutze den iCal-Link (enthält evtl. nicht alle Termine).")
                                OutlinedButton(onClick = { showFamilyWall = true }, modifier = Modifier.padding(top = 10.dp)) { Text("Mit iCal-Link verbinden") }
                            }
                        }
                        if (family != null) {
                            val status = when {
                                syncing -> "iCal-Link: wird aktualisiert …"
                                family.lastError != null -> "iCal-Link: Fehler: ${family.lastError}"
                                family.lastSyncMillis != null -> "iCal-Link verbunden · zuletzt " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(family.lastSyncMillis))
                                else -> "iCal-Link verbunden"
                            }
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (family.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            family.filterName?.let { hint("Nur Termine für $it") }
                            if (family.lastError == null && family.lastNote != null) hint(family.lastNote)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedButton(onClick = { showFamilyWall = true }) { Text("Ändern") }
                                TextButton(onClick = { viewModel.delete(family) }) { Text("Link trennen") }
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
                            "Viele Schulplattformen bieten einen Kalender-Link (iCal) an. Damit landen Abgaben automatisch als Aufgaben in der App – alle 2 Stunden und beim Öffnen aktualisiert.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (syncing) Text("Wird aktualisiert …", modifier = Modifier.padding(top = 8.dp))
                        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.padding(top = 12.dp)) { Text("Abo hinzufügen") }
                    }
                }
                items(sources.filterNot(::isFamilyWall), key = { it.id }) { source ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        source.importAsTasks -> "→ als Aufgaben"
                                        source.area == LifeArea.PRIVATE -> "→ als Termine · Privat"
                                        else -> "→ als Termine · Schule"
                                    },
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
        var privateArea by remember { mutableStateOf(false) }
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
                    if (!asTasks) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !privateArea, onClick = { privateArea = false }, label = { Text("Schule") })
                            FilterChip(selected = privateArea, onClick = { privateArea = true }, label = { Text("Privat / Familie") })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && (url.startsWith("http") || url.startsWith("webcal")),
                    onClick = {
                        viewModel.addSource(name, url, asTasks, if (privateArea) LifeArea.PRIVATE else LifeArea.SCHOOL)
                        showAdd = false
                    },
                ) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Abbrechen") } },
        )
    }

    if (showFamilyWall) {
        val existing = sources.firstOrNull(::isFamilyWall)
        val defaultName by viewModel.userName.collectAsStateWithLifecycle()
        var url by remember { mutableStateOf(existing?.url.orEmpty()) }
        var onlyMine by remember { mutableStateOf(existing == null || existing.filterName != null) }
        var person by remember(defaultName) { mutableStateOf(existing?.filterName ?: defaultName) }
        val valid = url.trim().let { it.startsWith("http") || it.startsWith("webcal") }
        AlertDialog(
            onDismissRequest = { showFamilyWall = false },
            title = { Text(if (existing == null) "FamilyWall verbinden" else "FamilyWall ändern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "1. FamilyWall öffnen und unten „Kalender“ wählen.",
                        "2. Oben rechts auf das Zahnrad (Einstellungen) tippen.",
                        "3. Den ersten Kalender antippen – er heißt wie euer Familien-Kreis.",
                        "4. „iCal generieren“ antippen und den Link kopieren.",
                        "5. Zurück hierher und „Link einfügen“ antippen.",
                    ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                        Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Text("Link einfügen", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedTextField(url, { url = it }, label = { Text("iCal-Link") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nur Termine, bei denen ich ausgewählt bin", modifier = Modifier.weight(1f))
                        Switch(checked = onlyMine, onCheckedChange = { onlyMine = it })
                    }
                    if (onlyMine) {
                        OutlinedTextField(
                            person, { person = it },
                            label = { Text("Dein Name in FamilyWall") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (url.isNotBlank() && !valid) {
                        Text("Das ist kein Link. Er beginnt mit https:// oder webcal://", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Der Link ist wie ein Passwort – teile ihn mit niemandem. Er bleibt nur auf diesem Gerät.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid && (!onlyMine || person.isNotBlank()), onClick = {
                    viewModel.connectFamilyWall(url, if (onlyMine) person else null)
                    showFamilyWall = false
                }) { Text("Verbinden") }
            },
            dismissButton = { TextButton(onClick = { showFamilyWall = false }) { Text("Abbrechen") } },
        )
    }

    val titleList by viewModel.titles.collectAsStateWithLifecycle()
    titleList?.let { list ->
        AlertDialog(
            onDismissRequest = viewModel::closeTitles,
            title = { Text("Welche Termine sind deine?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Nächste 31 Tage. Deine Wahl gilt für alle Termine mit diesem Titel – auch künftige (z. B. jeder „Spieltag“). Neue Titel ordnet die App nach bekannten Wörtern zu („Turnier“ = deins, „Marie“ = nicht deins). Nur deine Termine kommen in den Tagesplan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (list.isEmpty()) Text("Keine Termine in den nächsten 31 Tagen.")
                    list.forEach { item ->
                        Column {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                item.dates.take(6).joinToString(", ") { "${it.dayOfMonth}.${it.monthValue}." } +
                                    (if (item.dates.size > 6) " …" else "") +
                                    when {
                                        item.choice != null -> ""
                                        item.guess != null -> " · vermutet – tippe zum Bestätigen"
                                        item.autoMine -> " · erkannt am Namen"
                                        else -> " · offen – gehört das dir?"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = item.mine,
                                    onClick = { viewModel.choose(item.title, if (item.choice == true) null else true) },
                                    label = { Text("Meins") },
                                )
                                FilterChip(
                                    selected = !item.mine && !item.open,
                                    onClick = { viewModel.choose(item.title, if (item.choice == false) null else false) },
                                    label = { Text("Nicht meins") },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeTitles) { Text("Fertig") } },
        )
    }
}

private fun isFamilyWall(source: IcalSourceEntity) = source.isFamilyWall()
