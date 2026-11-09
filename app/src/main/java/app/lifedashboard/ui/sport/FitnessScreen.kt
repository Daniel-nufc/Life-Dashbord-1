package app.lifedashboard.ui.sport

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.WorkoutEntity
import app.lifedashboard.data.db.WorkoutType
import app.lifedashboard.data.repository.PlannedTraining
import app.lifedashboard.data.repository.TrainingOverview
import app.lifedashboard.data.repository.WorkoutWithSets
import app.lifedashboard.data.settings.TrainingSettings
import app.lifedashboard.data.sync.FitnessSnapshot
import app.lifedashboard.data.sync.HealthRepository
import app.lifedashboard.planner.mondayOfWeek
import app.lifedashboard.ui.components.BarChart
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.IconBadge
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.icon
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.minutesToTimeText
import app.lifedashboard.ui.components.shortName
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.ChoiceChips
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import app.lifedashboard.ui.editors.FieldLabel
import java.time.Duration
import java.time.LocalDate

private val SPORT = LifeArea.SPORT.color
private val SLEEP = Color(0xFF6C63FF)
private val FOOD = Color(0xFFFF8A3D)
private val WATER = Color(0xFF2EA8F5)
private val HEART = Color(0xFFE5486A)

/** Fitness: alles an einem Ort – Health Connect (Samsung Health, MyFitnessPal …), Sport-Zeitfenster, Log. */
@Composable
fun FitnessScreen(onBack: () -> Unit, viewModel: FitnessViewModel = hiltViewModel()) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val healthState by viewModel.healthState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditRequest?>(null) }
    var workoutDialog by remember { mutableStateOf<WorkoutWithSets?>(null) }
    var newWorkout by remember { mutableStateOf(false) }
    var settingsDialog by remember { mutableStateOf(false) }
    var logWindow by remember { mutableStateOf<PlannedTraining?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { viewModel.refresh() }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    OneUiScaffold(
        title = "Fitness",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
        actions = { IconButton(onClick = { settingsDialog = true }) { Icon(Icons.Rounded.Settings, "Sport-Einstellungen") } },
        floatingActionButton = { AddFab { newWorkout = true } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { TodayHero(snapshot, overview, settings.stepGoal) }
                if (snapshot != null) item { StatTiles(snapshot!!) }
                message?.let { msg -> item { SectionCard { Text(msg) } } }
                overview?.let { o ->
                    item {
                        WindowsCard(
                            o,
                            onDone = { logWindow = it },
                            onSkip = viewModel::skip,
                            onUndo = viewModel::undo,
                            onShorten = viewModel::shorten,
                            onSettings = { settingsDialog = true },
                        )
                    }
                }
                snapshot?.let { s -> item { WeekCharts(s) } }
                item { TrainingMinutesCard(workouts, overview) }
                item {
                    ConnectionsCard(
                        state = healthState,
                        snapshot = snapshot,
                        onConnect = { permissionLauncher.launch(viewModel.healthPermissions) },
                        onSync = viewModel::syncNow,
                    )
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
                item { TextButton(onClick = { editing = EditRequest.Routine(null) }) { Text("+ Festes Training hinzufügen") } }
                // Log
                item { Text("Trainings-Log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp)) }
                if (workouts.isEmpty()) {
                    item {
                        SectionCard {
                            Text(
                                "Noch kein Training eingetragen. Tippe auf „Neu“ – oder lass Trainings aus Samsung Health & Co. automatisch kommen.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(workouts.take(40), key = { "w${it.workout.id}" }) { w -> WorkoutRow(w) { workoutDialog = w } }
            }
        }
    }

    EditorHost(editing, onDismiss = { editing = null })
    if (settingsDialog) {
        SportSettingsDialog(settings, onSave = { viewModel.saveSettings(it); settingsDialog = false }, onDismiss = { settingsDialog = false })
    }
    logWindow?.let { t ->
        LogWindowDialog(
            t,
            onSave = { what -> viewModel.done(t, what); logWindow = null },
            onDismiss = { logWindow = null },
        )
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

// ------------------------------------------------------------------ Heute

@Composable
private fun TodayHero(s: FitnessSnapshot?, o: TrainingOverview?, stepGoal: Int) {
    val today = s?.today
    val steps = today?.steps ?: 0L
    GradientCard(listOf(Color(0xFF1DB370), Color(0xFF0E9AA7))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Heute", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (s == null) "Verbinde Health Connect, dann siehst du hier Schritte, Schlaf, Kalorien und mehr."
                    else "%,d von %,d Schritten".format(steps, stepGoal).replace(',', '.'),
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(Modifier.height(6.dp))
                val bits = listOfNotNull(
                    today?.sleep?.let { "😴 ${it.hm()}" },
                    today?.activeKcal?.let { "🔥 $it kcal aktiv" },
                    s?.avgHeartRateToday?.let { "❤️ Ø $it" },
                    o?.let { "🏃 ${it.minutesThisWeek} Min Sport diese Woche" },
                )
                if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            ProgressRing((steps.toFloat() / stepGoal).coerceIn(0f, 1f), Color.White, size = 84.dp, trackColor = Color.White.copy(alpha = 0.25f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(steps * 100 / stepGoal.coerceAtLeast(1)).coerceAtMost(999)}%", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Schritte", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatTiles(s: FitnessSnapshot) {
    val t = s.today
    val tiles = buildList {
        add(Tile(Icons.Rounded.DirectionsWalk, SPORT, "Schritte", t?.steps?.let { "%,d".format(it).replace(',', '.') }))
        add(Tile(Icons.Rounded.Bedtime, SLEEP, "Schlaf", t?.sleep?.hm()))
        add(Tile(Icons.Rounded.LocalFireDepartment, FOOD, "Aktiv verbrannt", t?.activeKcal?.let { "$it kcal" }))
        add(Tile(Icons.Rounded.LocalFireDepartment, Color(0xFFFF5B5B), "Gesamt verbrannt", t?.burnedKcal?.let { "$it kcal" }))
        add(Tile(Icons.Rounded.Restaurant, FOOD, "Gegessen", t?.eatenKcal?.let { "$it kcal" }))
        add(
            Tile(
                Icons.Rounded.Restaurant, Color(0xFFB7791F), "Eiweiß · KH · Fett",
                if (s.proteinG == null && s.carbsG == null && s.fatG == null) null else "${s.proteinG ?: 0} · ${s.carbsG ?: 0} · ${s.fatG ?: 0} g",
            ),
        )
        add(Tile(Icons.Rounded.WaterDrop, WATER, "Wasser", s.waterMl?.let { "%.1f l".format(it / 1000.0) }))
        add(Tile(Icons.Rounded.Favorite, HEART, "Ruhepuls (7 T.)", s.restingHeartRate?.let { "$it bpm" }))
        add(Tile(Icons.Rounded.Favorite, HEART, "Puls heute Ø", s.avgHeartRateToday?.let { "$it bpm" }))
        add(Tile(Icons.Rounded.MonitorWeight, Color(0xFF7A869A), "Gewicht", s.weightKg?.let { "%.1f kg".format(it) }, s.weightDate?.friendly()))
        add(Tile(Icons.Rounded.Schedule, SPORT, "Trainingszeit heute", t?.exerciseMinutes?.let { "$it Min" }))
    }
    SectionCard {
        TileHeader(Icons.Rounded.Insights, "Deine Werte heute", SPORT)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.forEach { StatTile(it) }
        }
        if (tiles.any { it.value == null }) {
            Text(
                "„–“ = keine Daten. Diese Werte kommen aus Apps wie Samsung Health oder MyFitnessPal – siehe „Verbindungen“ unten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private data class Tile(val icon: ImageVector, val color: Color, val label: String, val value: String?, val hint: String? = null)

@Composable
private fun StatTile(t: Tile) {
    Column(Modifier.width(148.dp).padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(t.icon, t.color, size = 28.dp)
            Text(" ${t.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(t.value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        t.hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ------------------------------------------------------------------ Sport-Zeitfenster

@Composable
private fun WindowsCard(
    o: TrainingOverview,
    onDone: (PlannedTraining) -> Unit,
    onSkip: (PlannedTraining) -> Unit,
    onUndo: (PlannedTraining) -> Unit,
    onShorten: (PlannedTraining) -> Unit,
    onSettings: () -> Unit,
) {
    SectionCard {
        TileHeader(Icons.Rounded.Schedule, "Sport-Zeitfenster diese Woche", SPORT) {
            Pill("${o.week.count { it.done }}/${o.week.size}", SPORT)
        }
        Text(
            "Die App hält dir Zeit frei – was du machst (Laufen, Rad, Kraft, Schwimmen, Spazieren …), entscheidest du. " +
                "Floorball-Tage, Klausuren und lange Schichten werden berücksichtigt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (o.sleepWarning) {
            Text(
                "😴 Du hast kurz geschlafen. Willst du das Zeitfenster heute kürzer machen?",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (o.week.isEmpty()) {
            Text("Diese Woche keine Zeitfenster geplant.", modifier = Modifier.padding(top = 8.dp))
        }
        val today = LocalDate.now()
        o.week.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.session.date.friendly(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (t.session.date == today) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        when {
                            t.done -> "Genutzt ✓"
                            t.skipped -> "Fällt aus"
                            else -> "${t.session.minutes} Min frei für Sport" + when {
                                t.result?.reduced == true -> " (von dir gekürzt)"
                                t.session.reduced -> " · Klausurphase: etwas weniger, Lernen geht vor"
                                else -> ""
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (t.done || t.skipped) {
                    TextButton(onClick = { onUndo(t) }) { Text("Rückgängig") }
                } else {
                    if (!t.session.date.isAfter(today)) {
                        FilledTonalButton(onClick = { onDone(t) }) { Text("✓") }
                    }
                    if (t.result?.reduced != true) TextButton(onClick = { onShorten(t) }) { Text("Kürzer") }
                    TextButton(onClick = { onSkip(t) }) { Text("✕") }
                }
            }
        }
        TextButton(onClick = onSettings, modifier = Modifier.padding(top = 4.dp)) { Text("Anzahl & Länge ändern") }
    }
}

@Composable
private fun LogWindowDialog(t: PlannedTraining, onSave: (WorkoutEntity?) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf<WorkoutType?>(null) }
    var minutes by remember { mutableIntStateOf(t.session.minutes) }
    var distance by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Was hast du gemacht?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Optional – für dein Log. Du kannst auch einfach nur abhaken.", style = MaterialTheme.typography.bodySmall)
                ChoiceChips(workoutLabels.filter { it.first != WorkoutType.FLOORBALL }, type) { type = it }
                if (type != null) {
                    FieldLabel("Dauer: $minutes Min")
                    ChoiceChips(listOf(15 to "15", 30 to "30", 45 to "45", 60 to "60", 90 to "90"), minutes) { minutes = it }
                    if (type == WorkoutType.RUN || type == WorkoutType.BIKE || type == WorkoutType.SWIM) {
                        OutlinedTextField(
                            distance, { distance = it },
                            label = { Text("Strecke in km (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    type?.let {
                        WorkoutEntity(
                            dateEpochDay = t.session.date.toEpochDay(),
                            type = it,
                            durationMinutes = minutes,
                            distanceMeters = distance.replace(',', '.').toDoubleOrNull()?.let { km -> (km * 1000).toInt() },
                            note = "Sport-Zeitfenster",
                        )
                    },
                )
            }) { Text(if (type == null) "Nur abhaken" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun SportSettingsDialog(current: TrainingSettings, onSave: (TrainingSettings) -> Unit, onDismiss: () -> Unit) {
    var windows by remember { mutableIntStateOf(current.windowsPerWeek) }
    var minutes by remember { mutableIntStateOf(current.windowMinutes) }
    var sunday by remember { mutableStateOf(current.sundayRest) }
    var stepGoal by remember { mutableIntStateOf(current.stepGoal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sport-Zeitfenster") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Zeitfenster pro Woche: $windows (zusätzlich zu Floorball)")
                Slider(windows.toFloat(), { windows = it.toInt() }, valueRange = 0f..6f, steps = 5)
                FieldLabel("Länge: $minutes Min")
                ChoiceChips(listOf(30 to "30", 45 to "45", 60 to "60", 75 to "75", 90 to "90", 120 to "120"), minutes) { minutes = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sonntag frei halten", modifier = Modifier.weight(1f))
                    Switch(sunday, { sunday = it })
                }
                FieldLabel("Schritte-Ziel pro Tag: ${"%,d".format(stepGoal).replace(',', '.')}")
                Slider(stepGoal.toFloat(), { stepGoal = (it / 500).toInt() * 500 }, valueRange = 3000f..15000f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(current.copy(windowsPerWeek = windows, windowMinutes = minutes, sundayRest = sunday, stepGoal = stepGoal)) }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ------------------------------------------------------------------ Verlauf

@Composable
private fun WeekCharts(s: FitnessSnapshot) {
    val labels = s.days.map { it.date.dayOfWeek.shortName() }
    SectionCard {
        TileHeader(Icons.Rounded.Insights, "Letzte 7 Tage", SPORT)
        Chart("Schritte", s.days.map { it.steps?.toFloat() }, labels, SPORT) { "Ø %,d".format(it.toLong()).replace(',', '.') }
        Chart("Schlaf (Std)", s.days.map { it.sleep?.toMinutes()?.div(60f) }, labels, SLEEP) { "Ø %.1f h".format(it) }
        Chart("Gegessen (kcal)", s.days.map { it.eatenKcal?.toFloat() }, labels, FOOD) { "Ø %.0f kcal".format(it) }
        Chart("Aktiv verbrannt (kcal)", s.days.map { it.activeKcal?.toFloat() }, labels, Color(0xFFFF5B5B)) { "Ø %.0f kcal".format(it) }
        Chart("Trainingszeit (Min)", s.days.map { it.exerciseMinutes?.toFloat() }, labels, SPORT) { "Ø %.0f Min".format(it) }
    }
}

@Composable
private fun Chart(title: String, values: List<Float?>, labels: List<String>, color: Color, avg: (Float) -> String) {
    val present = values.filterNotNull().filter { it > 0f }
    if (present.isEmpty()) return
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(avg(present.average().toFloat()), style = MaterialTheme.typography.labelLarge, color = color)
    }
    BarChart(values.map { it ?: 0f }, labels, color, Modifier.padding(top = 4.dp), height = 80.dp)
}

@Composable
private fun TrainingMinutesCard(workouts: List<WorkoutWithSets>, o: TrainingOverview?) {
    val thisMonday = LocalDate.now().mondayOfWeek()
    val weeks = (7 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }
    val minutes = weeks.map { monday ->
        workouts.filter { !it.date.isBefore(monday) && it.date.isBefore(monday.plusWeeks(1)) }.sumOf { it.workout.durationMinutes }.toFloat()
    }
    SectionCard {
        TileHeader(Icons.Rounded.FitnessCenter, "Sport pro Woche", SPORT) { Pill("${minutes.last().toInt()} Min", SPORT) }
        BarChart(minutes, weeks.map { "KW${it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" }, SPORT)
        if (o != null) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = SPORT)
                Text(
                    "  Ironman-Weg: " + (o.nextGoalStep?.let { "nächster Schritt „$it“" } ?: "alle Schritte erledigt 🎉") +
                        (if (o.longestRunKm > 0) " · längster Lauf %.1f km".format(o.longestRunKm) else ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Verbindungen

private data class Connector(val name: String, val data: String, val howTo: String)

private val CONNECTORS = listOf(
    Connector(
        "Samsung Health",
        "Schritte, Schlaf, Puls, Trainings, Kalorien, Gewicht, Wasser, Ernährung",
        "Samsung Health öffnen → ☰/⋮ → Einstellungen → Health Connect → Berechtigungen → alles erlauben.",
    ),
    Connector(
        "MyFitnessPal",
        "Ernährung (Kalorien, Eiweiß, Kohlenhydrate, Fett), Gewicht, Wasser",
        "MyFitnessPal → Mehr/Profil → Einstellungen → Apps & Geräte (bzw. „Verbundene Apps“) → Health Connect → verbinden und Schreiben erlauben.",
    ),
    Connector(
        "Strava, Garmin Connect, Fitbit, YAZIO, Google Fit …",
        "Trainings, Strecken, Puls, Ernährung – je nach App",
        "In der jeweiligen App nach „Health Connect“ suchen und Daten teilen. Alles, was dort ankommt, siehst du hier automatisch.",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionsCard(
    state: FitnessViewModel.HealthState,
    snapshot: FitnessSnapshot?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
) {
    val context = LocalContext.current
    var showHow by remember { mutableStateOf(false) }
    SectionCard {
        TileHeader(Icons.Rounded.Link, "Verbindungen", SPORT) {
            Pill(
                when (state) {
                    FitnessViewModel.HealthState.READY -> "verbunden"
                    FitnessViewModel.HealthState.PARTIAL -> "teilweise"
                    FitnessViewModel.HealthState.UNKNOWN -> "…"
                    else -> "nicht verbunden"
                },
                if (state == FitnessViewModel.HealthState.READY) SPORT else MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            "Alles läuft über Health Connect – die offizielle Gesundheits-Zentrale von Android. Samsung Health, MyFitnessPal & Co. legen ihre Daten dort ab, " +
                "diese App liest sie nur. Kein Login, kein Server – alles bleibt auf dem Handy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            FitnessViewModel.HealthState.NOT_AVAILABLE -> Text(
                "Health Connect ist nicht verfügbar. In Samsung Health unter Einstellungen → Health Connect einschalten (oder Health Connect im Play Store aktualisieren).",
                modifier = Modifier.padding(top = 8.dp),
            )
            FitnessViewModel.HealthState.NEEDS_PERMISSION, FitnessViewModel.HealthState.PARTIAL ->
                Button(onClick = onConnect, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (state == FitnessViewModel.HealthState.PARTIAL) "Weitere Daten freigeben (Ernährung, Gewicht …)" else "Health Connect verbinden")
                }
            FitnessViewModel.HealthState.ERROR -> Text("Daten konnten nicht geladen werden.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            else -> Unit
        }
        snapshot?.sources?.takeIf { it.isNotEmpty() }?.let { sources ->
            FieldLabel("Daten kommen gerade von:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.map { HealthRepository.sourceName(it) }.distinct().sorted().forEach { Pill(it, SPORT) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state == FitnessViewModel.HealthState.READY || state == FitnessViewModel.HealthState.PARTIAL) {
                OutlinedButton(onClick = onSync) { Text("↻ Trainings holen") }
            }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Health Connect öffnen") }
        }
        TextButton(onClick = { showHow = !showHow }) { Text(if (showHow) "Anleitung ausblenden" else "So verbindest du Samsung Health, MyFitnessPal & Co.") }
        if (showHow) {
            CONNECTORS.forEach { c ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall)
                    Text(c.data, style = MaterialTheme.typography.bodySmall, color = SPORT)
                    Text(c.howTo, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Ehrlich gesagt: Eine direkte Anmeldung bei Samsung oder MyFitnessPal gibt es für private Apps nicht – Health Connect ist der offizielle und sichere Weg. " +
                    "Danach hier „Weitere Daten freigeben“ antippen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Log

@Composable
private fun WorkoutRow(w: WorkoutWithSets, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.workout.type.label(), style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(
                        w.date.friendly(),
                        "${w.workout.durationMinutes} Min",
                        w.workout.distanceMeters?.let { "%.1f km".format(it / 1000.0) },
                        w.workout.avgHeartRate?.let { "Ø $it bpm" },
                        w.sets.takeIf { it.isNotEmpty() }?.let { "${it.size} Sätze" },
                        if (w.workout.externalId != null) "aus Health Connect" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.FitnessCenter, null, tint = SPORT)
        }
    }
}

private fun Duration.hm() = "%d:%02d h".format(toHours(), toMinutesPart())
