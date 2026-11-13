package app.lifedashboard.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.planner.Progress
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.navigation.RootViewModel

/** Alles zur Motivation an einem Ort: Level, Wochenziele, Serie, Ironman-Weg, Abzeichen. */
@Composable
fun ProgressScreen(onBack: () -> Unit, viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.progress.collectAsStateWithLifecycle()
    OneUiScaffold(
        title = "Fortschritt",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
    ) { padding ->
        val s = state
        if (s == null) {
            LoadingState()
            return@OneUiScaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ProgressCard(s, onClick = {}) }
                item { IronmanPathCard(s.ironman) }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.EmojiEvents, "Abzeichen", MaterialTheme.colorScheme.primary) {
                            Text("${s.earnedBadges}/${s.badges.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BadgeGrid(s.badges)
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Insights, "So gibt es Punkte", MaterialTheme.colorScheme.primary)
                        PointsRow("1 Minute Sport", "${Progress.XP_PER_SPORT_MINUTE} Punkte")
                        PointsRow("1 Minute Lernen", "${Progress.XP_PER_STUDY_MINUTE} Punkte")
                        PointsRow("Aufgabe abgehakt", "${Progress.XP_PER_TASK} Punkte")
                        PointsRow("Gewohnheit abgehakt", "${Progress.XP_PER_HABIT} Punkte")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Die Serie zählt Tage mit Sport oder Lernen. Ein Tag pro Woche darf fehlen, ohne dass sie reißt. " +
                                "Alles wird aus deinen Einträgen berechnet – es geht nichts verloren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
