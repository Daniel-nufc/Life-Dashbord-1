package app.lifedashboard.ui.sport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.repository.PlannedTraining
import app.lifedashboard.data.repository.TrainingOverview
import app.lifedashboard.data.settings.TrainingSettings
import app.lifedashboard.planner.Effort
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.shortName
import app.lifedashboard.ui.editors.ChoiceChips
import app.lifedashboard.ui.editors.FieldLabel
import java.time.LocalDate

class TrainingActions(
    val done: (PlannedTraining) -> Unit,
    val skip: (PlannedTraining) -> Unit,
    val rate: (PlannedTraining, Effort) -> Unit,
    val reduce: (PlannedTraining) -> Unit,
)

@Composable
fun TrainingGoalCard(o: TrainingOverview, onSettings: () -> Unit) {
    val target = o.goal.runKm
    GradientCard(listOf(Color(0xFF1DB370), Color(0xFF0F9D58), Color(0xFF3D7BF7))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Nächstes Ziel", color = Color.White.copy(alpha = 0.85f))
                Text(o.goal.label, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Trainingsplan einstellen", tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (o.currentLongestKm / target).toFloat().coerceIn(0f, 1f) },
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            "Längster Lauf: %.1f km von %.1f km · nächster langer Lauf: %.1f km".format(o.currentLongestKm, target, o.nextLongRunKm),
            color = Color.White, modifier = Modifier.padding(top = 6.dp),
        )
        Text("Geschätzt erreicht: ${o.estimatedGoalDate.friendly()}", color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TrainingWeekCard(o: TrainingOverview, actions: TrainingActions) {
    SectionCard {
        TileHeader(Icons.Rounded.DirectionsRun, "Diese Woche", LifeArea.SPORT.color) {
            Pill("${o.week.count { it.done }}/${o.week.size}", LifeArea.SPORT.color)
        }
        if (o.sleepWarning) {
            Text(
                "😴 Du hast letzte Nacht wenig geschlafen. Heute lieber leichter trainieren?",
                color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium,
            )
            o.week.firstOrNull { it.session.date == LocalDate.now() && it.session.type.hard }?.let { t ->
                OutlinedButton(onClick = { actions.reduce(t) }) { Text("Ja, leichter machen") }
            }
        }
        if (o.week.isEmpty()) Text("Diese Woche ist kein Training geplant (z. B. Klausuren oder volle Tage).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        o.week.forEach { t -> TrainingRow(t, actions) }
        Text(
            "Floorball (Mo/Mi) zählt als hartes Training. Ausgefallenes wird nicht nachgeholt – der Plan passt sich an.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TrainingRow(t: PlannedTraining, actions: TrainingActions) {
    val s = t.session
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.date.dayOfWeek.shortName(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.title + if (s.reduced) " (leichter)" else "",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    textDecoration = if (t.skipped) TextDecoration.LineThrough else null,
                )
                Text("${s.minutes} Min · ${s.details}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                t.done -> Pill("✓", LifeArea.SPORT.color)
                t.skipped -> Pill("ausgefallen", MaterialTheme.colorScheme.outline)
                else -> Unit
            }
        }
        if (!t.done && !t.skipped && !s.date.isAfter(today)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilledTonalButton(onClick = { actions.done(t) }) { Text("✓ Erledigt") }
                TextButton(onClick = { actions.skip(t) }) { Text("Fällt aus") }
            }
        }
        if (t.needsRating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text("Wie war's?", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { actions.rate(t, Effort.EASY) }) { Text("Leicht") }
                OutlinedButton(onClick = { actions.rate(t, Effort.OK) }) { Text("Passt") }
                OutlinedButton(onClick = { actions.rate(t, Effort.HARD) }) { Text("Zu hart") }
            }
        }
        t.result?.effort?.let {
            Text(
                "Bewertet: " + when (it) { Effort.EASY -> "leicht"; Effort.OK -> "passt"; Effort.HARD -> "zu hart – nächste Woche keine Steigerung" },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StrengthCard(o: TrainingOverview) {
    SectionCard {
        TileHeader(Icons.Rounded.FitnessCenter, "Kraft & Stabi (ohne Geräte)", LifeArea.SPORT.color)
        o.strength.forEach { ex ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(ex.name, modifier = Modifier.weight(1f))
                Text("${ex.sets} × ${ex.reps}", fontWeight = FontWeight.Medium)
            }
        }
        Text("Wird gesteigert, wenn du Kraft-Einheiten als „Leicht“ bewertest.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SafetyCard() {
    SectionCard {
        TileHeader(Icons.Rounded.HealthAndSafety, "Wichtig", MaterialTheme.colorScheme.error)
        Text(
            "Der Plan ist bewusst vorsichtig. Bei Schmerzen (Knie, Schienbein, Ferse …) sofort Pause machen und die Einheit als „Fällt aus“ markieren. " +
                "Halten Schmerzen an: zum Arzt. Krank oder Fieber = kein Training.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun TrainingSettingsDialog(current: TrainingSettings, onSave: (TrainingSettings) -> Unit, onDismiss: () -> Unit) {
    var longest by remember { mutableStateOf(current.startLongestKm.toString().replace('.', ',')) }
    var pace by remember { mutableStateOf(current.easyPace.toString().replace('.', ',')) }
    var runs by remember { mutableStateOf(current.runsPerWeek) }
    var strength by remember { mutableStateOf(current.strengthPerWeek) }
    var bike by remember { mutableStateOf(current.hasBike) }
    var swim by remember { mutableStateOf(current.canSwim) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trainingsplan einstellen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(longest, { longest = it }, label = { Text("Längster Lauf bisher (km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(pace, { pace = it }, label = { Text("Lockeres Tempo (Min pro km)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FieldLabel("Läufe pro Woche (zusätzlich zu Floorball)")
                ChoiceChips(listOf(2 to "2", 3 to "3", 4 to "4"), runs) { runs = it }
                FieldLabel("Kraft & Stabi pro Woche")
                ChoiceChips(listOf(0 to "0", 1 to "1", 2 to "2"), strength) { strength = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Fahrrad vorhanden", Modifier.weight(1f)); Switch(bike, { bike = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Kann kraulen", Modifier.weight(1f)); Switch(swim, { swim = it }) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    current.copy(
                        startLongestKm = longest.replace(',', '.').toDoubleOrNull()?.coerceIn(1.0, 42.0) ?: current.startLongestKm,
                        easyPace = pace.replace(',', '.').toDoubleOrNull()?.coerceIn(4.0, 10.0) ?: current.easyPace,
                        runsPerWeek = runs, strengthPerWeek = strength, hasBike = bike, canSwim = swim,
                    ),
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
