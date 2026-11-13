package app.lifedashboard.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.core.security.SecretStore
import app.lifedashboard.data.backup.BackupManager
import app.lifedashboard.data.backup.BackupWorker
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.listPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    private val secrets: SecretStore,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _autoFolder = MutableStateFlow(secrets.get(BackupWorker.FOLDER_KEY))
    val autoFolder: StateFlow<String?> = _autoFolder.asStateFlow()

    fun export(uri: Uri, password: String) = run("Backup gespeichert ✓") { backup.export(uri, password) }

    fun import(uri: Uri, password: String) = run(null) {
        val count = backup.import(uri, password)
        _message.value = "$count Einträge übernommen ✓ – am besten die App einmal schließen und neu öffnen."
    }

    fun enableAuto(folder: Uri, password: String) {
        context.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        secrets.put(BackupWorker.FOLDER_KEY, folder.toString())
        secrets.put(BackupWorker.PASSWORD_KEY, password)
        _autoFolder.value = folder.toString()
        BackupWorker.schedule(context)
        _message.value = "Automatisches Backup jede Woche eingeschaltet ✓"
    }

    fun disableAuto() {
        secrets.put(BackupWorker.FOLDER_KEY, null)
        secrets.put(BackupWorker.PASSWORD_KEY, null)
        _autoFolder.value = null
        BackupWorker.cancel(context)
        _message.value = "Automatisches Backup ausgeschaltet."
    }

    private fun run(success: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = try {
                block()
                success ?: _message.value
            } catch (e: Exception) {
                e.message ?: "Fehler"
            }
            _busy.value = false
        }
    }
}

private enum class PendingAction { EXPORT, IMPORT, AUTO }

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val autoFolder by viewModel.autoFolder.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { pickedUri = uri; pending = PendingAction.EXPORT }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pickedUri = uri; pending = PendingAction.IMPORT }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { pickedUri = uri; pending = PendingAction.AUTO }
    }

    OneUiScaffold(
        title = "Backup & Übertragen",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                message?.let { item { SectionCard { Text(it, style = MaterialTheme.typography.bodyLarge) } } }
                if (busy) item { SectionCard { Text("Einen Moment …") } }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Backup, "Backup erstellen", LifeArea.SCHOOL.color)
                        Text(
                            "Speichert alle Daten in einer Datei, verschlüsselt mit deinem Passwort. Ohne Passwort kann niemand sie öffnen – auch nicht in Google Drive.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { exportLauncher.launch("LifeDashboard-${LocalDate.now()}.ldbackup") }, modifier = Modifier.padding(top = 10.dp)) {
                            Text("Backup speichern …")
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.TabletAndroid, "Aufs Tablet (oder Handy) übertragen", LifeArea.PRIVATE.color)
                        listOf(
                            "1. Hier „Backup speichern“ – z. B. in „Downloads“.",
                            "2. Datei per Quick Share, Google Drive oder USB aufs andere Gerät bringen.",
                            "3. Dort: Mehr → Backup & Übertragen → „Backup laden“ und dasselbe Passwort eingeben.",
                            "Wichtig: Beide Geräte brauchen dieselbe App-Version. Die Daten auf dem Zielgerät werden ersetzt.",
                        ).forEach { Text(it, modifier = Modifier.padding(vertical = 2.dp)) }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.padding(top = 10.dp)) {
                            Icon(Icons.Rounded.Restore, null)
                            Text("  Backup laden …")
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Schedule, "Automatisch jede Woche", LifeArea.SPORT.color)
                        if (autoFolder == null) {
                            Text("Wähle einen Ordner (z. B. einen mit Google Drive/OneDrive synchronisierten). Das Passwort wird sicher im Keystore gespeichert.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { folderLauncher.launch(null) }, modifier = Modifier.padding(top = 10.dp)) { Text("Ordner wählen …") }
                        } else {
                            Text("Aktiv ✓ – die letzten 6 Backups werden behalten.")
                            TextButton(onClick = viewModel::disableAuto) { Text("Ausschalten") }
                        }
                    }
                }
            }
        }
    }

    val action = pending
    val uri = pickedUri
    if (action != null && uri != null) {
        var password by remember { mutableStateOf("") }
        var repeat by remember { mutableStateOf("") }
        val needsRepeat = action != PendingAction.IMPORT
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (action == PendingAction.IMPORT) "Passwort des Backups" else "Backup-Passwort festlegen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (needsRepeat) {
                        OutlinedTextField(repeat, { repeat = it }, label = { Text("Wiederholen") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                        Text("Mind. 8 Zeichen. Ohne Passwort ist das Backup nicht wiederherstellbar!", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Achtung: Die aktuellen Daten auf diesem Gerät werden ersetzt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.length >= (if (needsRepeat) 8 else 1) && (!needsRepeat || password == repeat),
                    onClick = {
                        when (action) {
                            PendingAction.EXPORT -> viewModel.export(uri, password)
                            PendingAction.IMPORT -> viewModel.import(uri, password)
                            PendingAction.AUTO -> viewModel.enableAuto(uri, password)
                        }
                        pending = null
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Abbrechen") } },
        )
    }
}
