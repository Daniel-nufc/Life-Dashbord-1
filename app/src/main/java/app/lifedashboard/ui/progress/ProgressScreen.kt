package app.lifedashboard.ui.progress

import androidx.compose.runtime.setValue
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.RestartAlt
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
    val name by viewModel.userName.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    var editGoals by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmRestart by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
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
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                item { ProgressCard(s, onClick = {}, onRedeem = viewModel::redeemGaming, onEditGoals = { editGoals = true }) }
                item {
                    app.lifedashboard.ui.components.SectionCard(onClick = { confirmRestart = true }) {
                        TileHeader(Icons.Rounded.RestartAlt, "Neu starten", MaterialTheme.colorScheme.primary)
                        Text(
                            "Rückstand, Strafpunkte und Zockzeit-Sperre vergessen – ab heute zählt es. Level, Serie und Abzeichen bleiben." +
                                (appSettings?.coachStart?.let { " Letzter Start: " + it.format(java.time.format.DateTimeFormatter.ofPattern("d.M.yyyy")) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    app.lifedashboard.ui.components.SectionCard(onClick = { WeeklyReport.share(context, s, name) }) {
                        TileHeader(Icons.Rounded.Share, "Wochenbericht teilen", MaterialTheme.colorScheme.primary)
                        Text(
                            "Schick deine Woche als Bild an Eltern, Freunde oder Trainer. Wer mitschaut, hilft dir dranzubleiben.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                        PointsRow("Schul-Block verpasst", "−${Progress.PENALTY_PER_MISSED_MINUTE} pro Minute")
                        PointsRow("25 Min Schule / 45 Min Sport", "+15 Min Zockzeit")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Die Serie zählt Tage mit Sport oder Lernen. Strenger Coach: Ein verpasster Schul-Block bricht die Serie, ab 1 Std Rückstand ist die Zockzeit gesperrt. " +
                                "Alles wird aus deinen Einträgen berechnet – es geht nichts verloren.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    val st = appSettings
    val p = state
    if (editGoals && st != null && p != null) {
        GoalsDialog(
            stepGoal = st.training.stepGoal, sportWeek = st.goalSportWeek, studyWeek = st.goalStudyWeek,
            autoSport = p.sportWeek.goalMinutes, autoStudy = p.studyWeek.goalMinutes,
            onSave = { a, b, c -> viewModel.setGoals(a, b, c); editGoals = false },
            onDismiss = { editGoals = false },
        )
    }
    if (confirmRestart) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Neu starten?") },
            text = { Text("Ab heute zählt jeder verpasste Block wieder. Kein Neustart als Ausrede – nutz ihn für einen echten Anfang.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { viewModel.restartCoach(); confirmRestart = false }) { Text("Ab heute") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRestart = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PointsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
