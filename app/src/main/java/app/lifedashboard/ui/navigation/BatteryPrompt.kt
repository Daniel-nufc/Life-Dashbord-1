package app.lifedashboard.ui.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BatteryPromptViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    /** null = noch unbekannt (Einstellungen laden) */
    val ask = settings.settings.map { it.training.askBattery }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dontAskAgain() {
        viewModelScope.launch { settings.setAskBattery(false) }
    }
}

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

/** Öffnet die System-Frage „Akku-Optimierung für diese App ausschalten?“ – mit Fallback auf die Liste. */
@SuppressLint("BatteryLife")
fun Context.requestBatteryExemption() {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(direct) }.onFailure {
        runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/**
 * Samsung schickt Apps ohne Ausnahme gern in den „Tiefschlaf“ – dann kommen Erinnerungen
 * und Auto-Backup zu spät. Einmal freundlich fragen (du hast es erlaubt), danach nie wieder.
 */
@Composable
fun BatteryPrompt(enabled: Boolean, viewModel: BatteryPromptViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    if (!enabled || dismissed || ask != true || context.isIgnoringBatteryOptimizations()) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("Erinnerungen zuverlässig machen") },
        text = {
            Text(
                "Samsung spart Akku, indem es Apps im Hintergrund schlafen legt. Dann kommen Erinnerungen, Wochenplan " +
                    "und Auto-Backup manchmal zu spät.\n\nTippe auf „Erlauben“ und bestätige „Zulassen“. Die App braucht trotzdem kaum Akku " +
                    "(sie wacht nur alle 30 Minuten kurz auf).",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                context.requestBatteryExemption()
                viewModel.dontAskAgain()
                dismissed = true
            }) { Text("Erlauben") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { viewModel.dontAskAgain(); dismissed = true }) { Text("Nie fragen") }
                TextButton(onClick = { dismissed = true }) { Text("Später") }
            }
        },
    )
}
