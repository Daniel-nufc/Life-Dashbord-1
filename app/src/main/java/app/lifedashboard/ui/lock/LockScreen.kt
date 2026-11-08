package app.lifedashboard.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lifedashboard.ui.theme.LocalAppColors

@Composable
fun LockScreen(onUnlockRequest: () -> Unit) {
    // Fingerabdruck-Abfrage sofort anzeigen
    LaunchedEffect(Unit) { onUnlockRequest() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.screenBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("Life Dashboard ist gesperrt", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onUnlockRequest) { Text("Entsperren") }
    }
}
