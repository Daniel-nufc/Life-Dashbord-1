package app.lifedashboard.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Erlaubte Fokus-Dauer in Minuten (eigene Eingabe) */
val FOCUS_MINUTES_RANGE = 1..240

/**
 * Fokus-Dauer wählen: Schnellauswahl oder eigene Minutenzahl (1–240).
 * [title] z. B. „Fokus-Dauer“ oder „Verlängern um …“.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    hint: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initialMinutes.toString()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in FOCUS_MINUTES_RANGE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(10, 15, 25, 30, 45, 50, 60, 90).forEach { m ->
                        FilterChip(selected = minutes == m, onClick = { text = m.toString() }, label = { Text("$m Min") })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("Eigene Dauer in Minuten") },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    supportingText = { Text(if (text.isNotEmpty() && !valid) "1 bis 240 Minuten" else hint ?: "z. B. 40") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onPick(minutes!!) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}
