package app.lifedashboard.ui.sport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutSetEntity
import app.lifedashboard.data.db.WorkoutType
import app.lifedashboard.data.repository.RoutineRepository
import app.lifedashboard.data.repository.SportRepository
import app.lifedashboard.data.repository.WorkoutWithSets
import app.lifedashboard.data.sync.DailyHealth
import app.lifedashboard.data.sync.HealthRepository
import app.lifedashboard.planner.mondayOfWeek
import app.lifedashboard.ui.components.BarChart
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.icon
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.minutesToTimeText
import app.lifedashboard.ui.components.pickDate
import app.lifedashboard.ui.components.shortName
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.ChoiceChips
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import app.lifedashboard.ui.editors.FieldLabel
import app.lifedashboard.ui.editors.ValueButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal val workoutLabels = listOf(
    WorkoutType.FLOORBALL to "🏑 Floorball",
    WorkoutType.RUN to "🏃 Laufen",
    WorkoutType.STRENGTH to "🏋️ Kraft",
    WorkoutType.SWIM to "🏊 Schwimmen",
    WorkoutType.BIKE to "🚴 Rad",
    WorkoutType.OTHER to "⭐ Sonstiges",
)

fun WorkoutType.label() = workoutLabels.first { it.first == this }.second

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutDialog(
    original: WorkoutWithSets?,
    onSave: (WorkoutEntity, List<WorkoutSetEntity>) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(original?.workout?.type ?: WorkoutType.FLOORBALL) }
    var date by remember { mutableStateOf(original?.date ?: LocalDate.now()) }
    var minutes by remember { mutableIntStateOf(original?.workout?.durationMinutes ?: 90) }
    var distance by remember { mutableStateOf(original?.workout?.distanceMeters?.let { "%.1f".format(it / 1000.0).replace('.', ',') } ?: "") }
    var note by remember { mutableStateOf(original?.workout?.note ?: "") }
    val sets = remember { mutableStateListOf<WorkoutSetEntity>().apply { addAll(original?.sets.orEmpty()) } }
    var exercise by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("10") }
    var weight by remember { mutableStateOf("20") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Training eintragen" else "Training bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Art")
                ChoiceChips(workoutLabels, type) {
                    type = it
                    if (original == null) minutes = if (it == WorkoutType.FLOORBALL) 90 else 30
                }
                ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
                FieldLabel("Dauer: $minutes Min")
                ChoiceChips(listOf(15 to "15", 30 to "30", 45 to "45", 60 to "60", 90 to "90", 120 to "120"), minutes) { minutes = it }
                if (type == WorkoutType.RUN || type == WorkoutType.BIKE || type == WorkoutType.SWIM) {
                    OutlinedTextField(
                        distance, { distance = it },
                        label = { Text("Strecke in km") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (type == WorkoutType.STRENGTH) {
                    FieldLabel("Sätze")
                    sets.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.exercise}: ${s.reps} × ${s.weightKg} kg", modifier = Modifier.weight(1f))
                            TextButton(onClick = { sets.removeAt(i) }) { Text("✕") }
                        }
                    }
                    OutlinedTextField(exercise, { exercise = it }, label = { Text("Übung, z. B. Kniebeugen") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(reps, { reps = it }, label = { Text("Wdh.") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(weight, { weight = it }, label = { Text("kg") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    OutlinedButton(
                        enabled = exercise.isNotBlank() && reps.toIntOrNull() != null,
                        onClick = {
                            sets += WorkoutSetEntity(workoutId = 0, exercise = exercise.trim(), reps = reps.toInt(), weightKg = weight.replace(',', '.').toDoubleOrNull() ?: 0.0)
                        },
                    ) { Icon(Icons.Rounded.Add, null); Text(" Satz hinzufügen") }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    WorkoutEntity(
                        id = original?.workout?.id ?: 0,
                        dateEpochDay = date.toEpochDay(),
                        type = type,
                        durationMinutes = minutes,
                        distanceMeters = distance.replace(',', '.').toDoubleOrNull()?.let { (it * 1000).toInt() },
                        note = note.trim().ifBlank { null },
                    ),
                    sets.toList(),
                )
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (original != null) TextButton(onClick = { onDelete(original.workout.id) }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
