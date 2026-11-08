package app.lifedashboard.ui.editors

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.lifedashboard.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * Schnelleingabe: „Mi 17 Uhr Zahnarzt“, „HA Mathe S. 45“, „Mathe Klausur am 2.12.“, „Schicht Sa 8-14“, „12,50 Döner“.
 * Öffnet einen vorausgefüllten Dialog zum Bestätigen – nichts wird ungefragt gespeichert.
 */
@Composable
fun QuickInputBar(
    onRequest: (EditRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val submit: () -> Unit = {
        scope.launch {
            val request = viewModel.parseQuick(text)
            if (request != null) {
                onRequest(request)
                text = ""
                error = false
            } else {
                error = true
            }
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            placeholder = { Text("Schnell eintragen: „Mi 17 Uhr Zahnarzt“") },
            supportingText = if (error) ({ Text("Nicht verstanden – z. B. „HA Mathe S. 45“, „Schicht Sa 8-14“, „12,50 Döner“") }) else null,
            isError = error,
            trailingIcon = {
                IconButton(onClick = submit, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Rounded.Send, "Eintragen") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = LocalAppColors.current.card,
                focusedContainerColor = LocalAppColors.current.card,
            ),
        )
    }
}

