package app.lifedashboard.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.lifedashboard.ui.components.EmptyState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.listPadding

/** Platzhalter für Bereiche, die in einer späteren Phase gebaut werden. */
@Composable
fun ComingSoonScreen(
    title: String,
    icon: ImageVector,
    phase: String,
    features: List<String>,
) {
    OneUiScaffold(title = title) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionCard {
                        EmptyState(icon = icon, title = "Kommt in $phase", message = "Hier entsteht gerade etwas.")
                    }
                }
                item {
                    SectionCard(title = "Geplant") {
                        features.forEach {
                            Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}
