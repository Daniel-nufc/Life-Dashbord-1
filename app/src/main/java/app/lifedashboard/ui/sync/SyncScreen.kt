package app.lifedashboard.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.devicesync.DeviceSyncRepository
import app.lifedashboard.data.devicesync.DeviceSyncSettings
import app.lifedashboard.data.devicesync.LanState
import app.lifedashboard.data.devicesync.LanSyncManager
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.listPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val repo: DeviceSyncRepository,
    private val lan: LanSyncManager,
) : ViewModel() {
    val settings: StateFlow<DeviceSyncSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lanState: StateFlow<LanState> = lan.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { repo.deviceId() }
    }

    fun savePassword(pw: String) = viewModelScope.launch {
        repo.setPassword(pw)
        _message.value = "Passwort gespeichert ✓ – auf dem anderen Gerät dasselbe eintragen."
        lan.restart()
    }

    fun setName(name: String) = viewModelScope.launch { repo.setDeviceName(name) }

    fun setLan(on: Boolean) = viewModelScope.launch {
        repo.setLanEnabled(on)
        if (on) lan.restart() else lan.stop()
    }

    fun chooseFile(uri: android.net.Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            repo.setFile(uri)
            syncNow()
        }
    }

    fun removeFile() = viewModelScope.launch { repo.setFile(null) }

    fun syncNow() {
        viewModelScope.launch {
            _busy.value = true
            val parts = mutableListOf<String>()
            if (settings.value?.fileUri != null) parts += "Cloud-Datei: " + repo.syncFile()
            if (lan.exchangeNow()) parts += "WLAN: " + (lan.state.value.message ?: "")
            _message.value = parts.joinToString("\n").ifBlank { "Noch nichts eingerichtet – Cloud-Datei wählen oder anderes Gerät im WLAN öffnen." }
            _busy.value = false
        }
    }
}

private val SYNC = Color(0xFF3D7BF7)
private val timeFormat = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
private fun Long.whenText() = if (this == 0L) "noch nie" else Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFormat)

@Composable
fun SyncScreen(onBack: () -> Unit, viewModel: SyncViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lan by viewModel.lanState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { viewModel.chooseFile(it) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.chooseFile(it) }

    OneUiScaffold(
        title = "Handy ↔ Tablet",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
    ) { padding ->
        val s = settings ?: return@OneUiScaffold
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GradientCard(listOf(Color(0xFF3D7BF7), Color(0xFF8B5CF6))) {
                        Text("Automatischer Abgleich", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Was du auf einem Gerät einträgst, erscheint auch auf dem anderen. Nichts wird überschrieben: Einträge werden " +
                                "zusammengeführt, bei Änderungen am selben Eintrag gewinnt die neuere.",
                            color = Color.White.copy(alpha = 0.92f),
                        )
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = viewModel::syncNow, enabled = !busy && s.hasPassword) {
                                Icon(Icons.Rounded.Sync, null)
                                Text(if (busy) "  Gleiche ab …" else "  Jetzt abgleichen")
                            }
                        }
                        message?.let { Text(it, color = Color.White, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                item { PasswordCard(s, viewModel) }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Cloud, "1 · Über Cloud-Ordner (auch unterwegs)", SYNC) {
                            Pill(if (s.fileUri != null) "aktiv" else "aus", if (s.fileUri != null) SYNC else MaterialTheme.colorScheme.outline)
                        }
                        Text(
                            "Beide Geräte nutzen dieselbe verschlüsselte Datei, z. B. in Google Drive. Abgleich automatisch beim Öffnen, beim Verlassen " +
                                "und alle 30 Minuten. Google sieht nur unlesbaren Datensalat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (s.fileUri == null) {
                            Text("Auf dem ERSTEN Gerät:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                            OutlinedButton(enabled = s.hasPassword, onClick = { createFile.launch("LifeDashboard-Abgleich.ldsync") }) { Text("Neue Abgleich-Datei anlegen (in Drive speichern)") }
                            Text("Auf dem ZWEITEN Gerät:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                            OutlinedButton(enabled = s.hasPassword, onClick = { openFile.launch(arrayOf("*/*")) }) { Text("Vorhandene Abgleich-Datei wählen") }
                            if (!s.hasPassword) Text("Zuerst oben das Abgleich-Passwort festlegen.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Letzter Abgleich: ${s.lastFileSync.whenText()}", modifier = Modifier.padding(top = 8.dp))
                            s.lastFileMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = viewModel::removeFile) { Text("Cloud-Abgleich beenden") }
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Wifi, "2 · Direkt im WLAN (ohne Cloud)", SYNC) {
                            Switch(s.lanEnabled, viewModel::setLan)
                        }
                        Text(
                            "Sind beide Geräte im selben WLAN und die App ist auf beiden offen, gleichen sie sich direkt ab – sofort und danach alle 2 Minuten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            when {
                                !s.lanEnabled -> "Aus"
                                !s.hasPassword -> "Wartet auf Abgleich-Passwort"
                                lan.peerName != null -> "Verbunden mit ${lan.peerName}"
                                lan.running -> "Sucht das andere Gerät …"
                                else -> "Startet beim Öffnen der App"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        (lan.message ?: s.lastLanMessage)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("Letzter WLAN-Abgleich: ${s.lastLanSync.whenText()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    SectionCard(title = "Gut zu wissen") {
                        listOf(
                            "Abgeglichen werden alle Einträge: Stundenplan, Termine, Aufgaben, Klausuren & Themen, Noten, Fitness-Log, Gewohnheiten, Ziele, Finanzen, Etsy, Ferien.",
                            "Einstellungen (Schulweg, A/B-Woche, Karten …) bleiben pro Gerät – das Tablet darf anders aussehen.",
                            "Tagesplan-Vorschläge rechnet jedes Gerät selbst aus den gemeinsamen Daten.",
                            "Beim ersten Abgleich werden Einträge, die auf beiden Geräten gleich sind (z. B. Stundenplan), automatisch zusammengelegt – nichts doppelt.",
                            "Beide Geräte brauchen dieselbe App-Version.",
                        ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordCard(s: DeviceSyncSettings, viewModel: SyncViewModel) {
    var pw by remember { mutableStateOf("") }
    var name by remember(s.deviceName) { mutableStateOf(s.deviceName) }
    SectionCard {
        TileHeader(Icons.Rounded.Key, "Abgleich-Passwort & Gerät", SYNC) {
            Pill(if (s.hasPassword) "gesetzt" else "fehlt", if (s.hasPassword) SYNC else MaterialTheme.colorScheme.error)
        }
        Text(
            "Auf BEIDEN Geräten dasselbe Passwort eintragen. Damit wird alles verschlüsselt – ohne Passwort kann niemand etwas lesen. " +
                "Es wird sicher im Android-Schlüsselspeicher abgelegt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                pw, { pw = it },
                label = { Text(if (s.hasPassword) "Neues Passwort (optional)" else "Abgleich-Passwort (mind. 8 Zeichen)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(enabled = pw.length >= 8, onClick = { viewModel.savePassword(pw); pw = "" }) { Text("Passwort speichern") }
            OutlinedTextField(
                name, { name = it },
                label = { Text("Name dieses Geräts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { viewModel.setName(name) }) { Text("OK") } },
            )
        }
    }
}
