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

@HiltViewModel
class SportViewModel @Inject constructor(
    routines: RoutineRepository,
    private val sport: SportRepository,
    private val health: HealthRepository,
    private val training: app.lifedashboard.data.repository.TrainingRepository,
    private val settingsRepository: app.lifedashboard.data.settings.SettingsRepository,
    private val engine: app.lifedashboard.data.planning.PlanningEngine,
) : ViewModel() {
    private val _overview = MutableStateFlow<app.lifedashboard.data.repository.TrainingOverview?>(null)
    val overview: StateFlow<app.lifedashboard.data.repository.TrainingOverview?> = _overview.asStateFlow()
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun refreshTraining() {
        viewModelScope.launch { _overview.value = runCatching { training.overview() }.getOrNull() }
    }

    private fun trainingAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
            _overview.value = runCatching { training.overview() }.getOrNull()
            runCatching { engine.replan(LocalDate.now()) }
        }
    }

    fun done(t: app.lifedashboard.data.repository.PlannedTraining) = trainingAction { training.markDone(t.session) }
    fun skip(t: app.lifedashboard.data.repository.PlannedTraining) = trainingAction { training.skip(t.session) }
    fun rate(t: app.lifedashboard.data.repository.PlannedTraining, e: app.lifedashboard.planner.Effort) = trainingAction { training.rate(t.session, e) }
    fun reduce(t: app.lifedashboard.data.repository.PlannedTraining) = trainingAction { training.reduce(t.session) }
    fun saveTrainingSettings(t: app.lifedashboard.data.settings.TrainingSettings) = trainingAction { settingsRepository.setTraining(t) }
    fun syncHealth() {
        viewModelScope.launch {
            val n = runCatching { training.syncFromHealth() }.getOrDefault(0)
            _syncMessage.value = if (n > 0) "$n Training(s) aus Health Connect übernommen ✓" else "Keine neuen Trainings in Health Connect gefunden."
            _overview.value = runCatching { training.overview() }.getOrNull()
        }
    }
    val routines: StateFlow<List<RoutineBlockEntity>> = routines.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val workouts: StateFlow<List<WorkoutWithSets>> = sport.workouts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _health = MutableStateFlow<List<DailyHealth>>(emptyList())
    val healthDays: StateFlow<List<DailyHealth>> = _health.asStateFlow()
    private val _healthState = MutableStateFlow(HealthState.UNKNOWN)
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()

    enum class HealthState { UNKNOWN, NOT_AVAILABLE, NEEDS_PERMISSION, READY, ERROR }

    val healthPermissions get() = health.permissions

    fun refreshHealth() {
        refreshTraining()
        viewModelScope.launch {
            runCatching { training.syncFromHealth() }
            _healthState.value = try {
                when {
                    !health.isAvailable() -> HealthState.NOT_AVAILABLE
                    !health.hasPermissions() -> HealthState.NEEDS_PERMISSION
                    else -> {
                        _health.value = health.lastDays(7)
                        HealthState.READY
                    }
                }
            } catch (e: Exception) {
                HealthState.ERROR
            }
        }
    }

    fun save(workout: WorkoutEntity, sets: List<WorkoutSetEntity>) {
        viewModelScope.launch { sport.save(workout, sets); refreshTraining() }
    }

    fun delete(id: Long) {
        viewModelScope.launch { sport.delete(id) }
    }
}

private val workoutLabels = listOf(
    WorkoutType.FLOORBALL to "🏑 Floorball",
    WorkoutType.RUN to "🏃 Laufen",
    WorkoutType.STRENGTH to "🏋️ Kraft",
    WorkoutType.SWIM to "🏊 Schwimmen",
    WorkoutType.BIKE to "🚴 Rad",
    WorkoutType.OTHER to "⭐ Sonstiges",
)

fun WorkoutType.label() = workoutLabels.first { it.first == this }.second

@Composable
fun SportScreen(onBack: () -> Unit, viewModel: SportViewModel = hiltViewModel()) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val healthDays by viewModel.healthDays.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val training by viewModel.overview.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    var trainingSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }
    var workoutDialog by remember { mutableStateOf<WorkoutWithSets?>(null) }
    var newWorkout by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealth()
    }
    LaunchedEffect(Unit) { viewModel.refreshHealth() }

    OneUiScaffold(
        title = "Sport",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
        floatingActionButton = { AddFab { newWorkout = true } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                training?.let { o ->
                    item { TrainingGoalCard(o, onSettings = { trainingSettings = true }) }
                    item {
                        TrainingWeekCard(
                            o,
                            TrainingActions(done = viewModel::done, skip = viewModel::skip, rate = viewModel::rate, reduce = viewModel::reduce),
                        )
                    }
                    item { StrengthCard(o) }
                    item { SafetyCard() }
                }
                syncMessage?.let { msg -> item { SectionCard { Text(msg) } } }
                // Wochenminuten der letzten 8 Wochen
                item {
                    val thisMonday = LocalDate.now().mondayOfWeek()
                    val weeks = (7 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
                    val minutes = weeks.map { monday ->
                        workouts.filter { !it.date.isBefore(monday) && it.date.isBefore(monday.plusWeeks(1)) }
                            .sumOf { it.workout.durationMinutes }.toFloat()
                    }
                    SectionCard {
                        TileHeader(Icons.Rounded.Insights, "Training pro Woche", LifeArea.SPORT.color) {
                            Pill("${minutes.last().toInt()} Min", LifeArea.SPORT.color)
                        }
                        BarChart(minutes, weeks.map { "KW${it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" }, LifeArea.SPORT.color)
                        val runs = workouts.filter { it.workout.type == WorkoutType.RUN && it.workout.distanceMeters != null }
                        val longest = runs.maxOfOrNull { it.workout.distanceMeters ?: 0 } ?: 0
                        Text(
                            "Längster Lauf: %.1f km · Ziel: 10 km".format(longest / 1000.0),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                // Health Connect
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.DirectionsWalk, "Schritte & Schlaf", LifeArea.SPORT.color)
                        when (healthState) {
                            SportViewModel.HealthState.NOT_AVAILABLE -> Text(
                                "Health Connect ist nicht verfügbar. In Samsung Health unter Einstellungen → Health Connect die Synchronisierung einschalten.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SportViewModel.HealthState.NEEDS_PERMISSION -> {
                                Text(
                                    "Erlaube der App, Schritte und Schlaf zu lesen (Samsung Health synchronisiert dorthin). Nur lesen – nichts wird geändert.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(onClick = { permissionLauncher.launch(viewModel.healthPermissions) }, modifier = Modifier.padding(top = 8.dp)) {
                                    Text("Health Connect verbinden")
                                }
                            }
                            SportViewModel.HealthState.ERROR -> Text("Daten konnten nicht geladen werden.", color = MaterialTheme.colorScheme.error)
                            SportViewModel.HealthState.UNKNOWN -> Text("Lädt …")
                            SportViewModel.HealthState.READY -> {
                                TextButton(onClick = viewModel::syncHealth) { Text("↻ Trainings aus Health Connect holen") }
                                val today = healthDays.lastOrNull()
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Text("${today?.steps ?: 0}", style = MaterialTheme.typography.headlineSmall)
                                        Text("Schritte heute", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Column {
                                        val sleep = today?.sleep
                                        Text(sleep?.let { "%d:%02d h".format(it.toHours(), it.toMinutesPart()) } ?: "–", style = MaterialTheme.typography.headlineSmall)
                                        Text("Schlaf letzte Nacht", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                BarChart(
                                    healthDays.map { it.steps.toFloat() },
                                    healthDays.map { it.date.dayOfWeek.shortName() },
                                    LifeArea.SPORT.color,
                                    Modifier.padding(top = 12.dp),
                                    height = 90.dp,
                                )
                                val sleeps = healthDays.mapNotNull { it.sleep }
                                if (sleeps.isNotEmpty()) {
                                    val avg = sleeps.map { it.toMinutes() }.average()
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                        Icon(Icons.Rounded.Bedtime, null, tint = LifeArea.PRIVATE.color)
                                        Text("  Ø Schlaf 7 Tage: %d:%02d h".format((avg / 60).toInt(), (avg % 60).toInt()))
                                    }
                                }
                            }
                        }
                    }
                }
                // Feste Trainings
                item { Text("Feste Trainings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp)) }
                items(routines, key = { "r${it.id}" }) { routine ->
                    SectionCard(onClick = { editing = EditRequest.Routine(routine) }) {
                        TileHeader(routine.area.icon, routine.title, routine.area.color) {
                            if (!routine.active) Pill("pausiert", MaterialTheme.colorScheme.outline)
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            routine.days().sorted().joinToString(" & ") { it.shortName() } +
                                " · ${minutesToTimeText(routine.startMinute)}–${minutesToTimeText(routine.endMinute)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        listOfNotNull(routine.location, routine.note).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    TextButton(onClick = { editing = EditRequest.Routine(null) }) { Text("+ Festes Training hinzufügen") }
                }
                // Log
                item { Text("Trainings-Log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp)) }
                if (workouts.isEmpty()) {
                    item {
                        SectionCard {
                            Text("Noch kein Training eingetragen. Tippe auf „Neu“ nach Floorball oder einem Lauf.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(workouts.take(30), key = { "w${it.workout.id}" }) { w ->
                    SectionCard(onClick = { workoutDialog = w }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.workout.type.label(), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(
                                        w.date.friendly(),
                                        "${w.workout.durationMinutes} Min",
                                        w.workout.distanceMeters?.let { "%.1f km".format(it / 1000.0) },
                                        w.sets.takeIf { it.isNotEmpty() }?.let { "${it.size} Sätze" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Rounded.FitnessCenter, null, tint = LifeArea.SPORT.color)
                        }
                    }
                }
            }
        }
    }
    EditorHost(editing, onDismiss = { editing = null })
    if (trainingSettings) {
        training?.let { o ->
            val current = app.lifedashboard.data.settings.TrainingSettings(
                startLongestKm = o.profile.startLongestKm, easyPace = o.profile.easyPaceMinPerKm, runsPerWeek = o.profile.runsPerWeek,
                strengthPerWeek = o.profile.strengthPerWeek, hasBike = o.profile.hasBike, canSwim = o.profile.canSwim, planStart = o.profile.planStart,
            )
            TrainingSettingsDialog(current, onSave = { viewModel.saveTrainingSettings(it); trainingSettings = false }, onDismiss = { trainingSettings = false })
        }
    }
    if (newWorkout || workoutDialog != null) {
        WorkoutDialog(
            original = workoutDialog,
            onSave = { w, sets -> viewModel.save(w, sets); newWorkout = false; workoutDialog = null },
            onDelete = { id -> viewModel.delete(id); workoutDialog = null },
            onDismiss = { newWorkout = false; workoutDialog = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutDialog(
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
