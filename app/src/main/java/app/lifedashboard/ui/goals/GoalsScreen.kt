package app.lifedashboard.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.GoalEntity
import app.lifedashboard.data.db.HabitEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.MilestoneEntity
import app.lifedashboard.data.repository.GoalRepository
import app.lifedashboard.data.repository.GoalWithMilestones
import app.lifedashboard.data.repository.HabitStatus
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.emoji
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.editors.AreaChips
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repo: GoalRepository,
    private val usage: app.lifedashboard.data.sync.UsageRepository,
    private val automation: app.lifedashboard.data.repository.AutomationRepository,
) : ViewModel() {
    private val _usageAllowed = kotlinx.coroutines.flow.MutableStateFlow(usage.hasPermission())
    val usageAllowed: StateFlow<Boolean> = _usageAllowed

    fun refreshAuto() {
        _usageAllowed.value = usage.hasPermission()
        viewModelScope.launch { runCatching { automation.autoCheckHabits() } }
    }

    fun usageSettingsIntent() = usage.settingsIntent()
    val habits: StateFlow<List<HabitStatus>> = repo.habits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals: StateFlow<List<GoalWithMilestones>> = repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleHabit(id: Long, done: Boolean) = launch { repo.toggleHabit(id, LocalDate.now(), done) }
    fun addHabit(title: String, emoji: String, area: LifeArea) = launch {
        repo.saveHabit(HabitEntity(title = title, emoji = emoji.trim(), area = area, active = true, sortOrder = 99))
    }
    fun archiveHabit(habit: HabitEntity) = launch { repo.saveHabit(habit.copy(active = false)) }
    fun toggleMilestone(m: MilestoneEntity) = launch { repo.saveMilestone(m.copy(done = !m.done)) }
    fun addMilestone(goalId: Long, title: String, order: Int) = launch {
        repo.saveMilestone(MilestoneEntity(goalId = goalId, title = title, sortOrder = order, done = false))
    }
    fun addGoal(title: String, area: LifeArea, steps: List<String>) = launch {
        val id = repo.saveGoal(GoalEntity(area = area, title = title, description = null, deadlineEpochDay = null, done = false, sortOrder = 99))
        steps.forEachIndexed { i, s -> repo.saveMilestone(MilestoneEntity(goalId = id, title = s, sortOrder = i, done = false)) }
    }
    fun deleteGoal(id: Long) = launch { repo.deleteGoal(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun GoalsScreen(onBack: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val usageAllowed by viewModel.usageAllowed.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshAuto()
        onPauseOrDispose { }
    }
    var addHabit by remember { mutableStateOf(false) }
    var addGoal by remember { mutableStateOf(false) }

    OneUiScaffold(
        title = "Ziele & Gewohnheiten",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.LocalFireDepartment, "Gewohnheiten heute", LifeArea.ETSY.color) {
                            IconButton(onClick = { addHabit = true }) { Icon(Icons.Rounded.Add, "Gewohnheit hinzufügen") }
                        }
                        Text(
                            "1 Joker pro Woche: Ein verpasster Tag lässt die Serie nicht reißen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        habits.forEach { status -> HabitRow(status, viewModel) }
                        if (!usageAllowed && habits.any { it.habit.autoRule == "SCREEN_SOCIAL" || it.habit.autoRule == "SLEEP_PHONE" }) {
                            Text(
                                "Handyzeit und Schlafenszeit automatisch abhaken: Erlaube einmal den „Nutzungsdatenzugriff“ für Life Dashboard. Die Daten bleiben auf dem Handy.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            TextButton(onClick = { context.startActivity(viewModel.usageSettingsIntent()) }) { Text("Nutzungsdatenzugriff erlauben") }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Ziele", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { addGoal = true }) { Text("+ Ziel") }
                    }
                }
                items(goals, key = { it.goal.id }) { goal -> GoalCard(goal, viewModel) }
            }
        }
    }

    if (addHabit) {
        var title by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("") }
        var area by remember { mutableStateOf(LifeArea.PRIVATE) }
        AlertDialog(
            onDismissRequest = { addHabit = false },
            title = { Text("Neue Gewohnheit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Was, z. B. „Wasser trinken“") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(emoji, { emoji = it.take(2) }, label = { Text("Emoji (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AreaChips(area, { area = it })
                }
            },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { viewModel.addHabit(title.trim(), emoji.trim(), area); addHabit = false }) { Text("Hinzufügen") } },
            dismissButton = { TextButton(onClick = { addHabit = false }) { Text("Abbrechen") } },
        )
    }
    if (addGoal) {
        var title by remember { mutableStateOf("") }
        var steps by remember { mutableStateOf("") }
        var area by remember { mutableStateOf(LifeArea.PRIVATE) }
        AlertDialog(
            onDismissRequest = { addGoal = false },
            title = { Text("Neues Ziel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Ziel") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    AreaChips(area, { area = it })
                    OutlinedTextField(steps, { steps = it }, label = { Text("Zwischenschritte (eins pro Zeile)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Text("Tipp: Kleine Schritte – du siehst immer nur den nächsten.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    viewModel.addGoal(title.trim(), area, steps.lines().map { it.trim() }.filter { it.isNotEmpty() })
                    addGoal = false
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { addGoal = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun HabitRow(status: HabitStatus, viewModel: GoalsViewModel) {
    val color = status.habit.area.color
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        app.lifedashboard.ui.components.CheckMark(checked = status.doneToday, onCheckedChange = { viewModel.toggleHabit(status.habit.id, it) }, color = color)
        Column(Modifier.weight(1f)) {
            Text("${status.habit.emoji} ${status.habit.title}", style = MaterialTheme.typography.bodyLarge)
            status.habit.autoRule?.let { rule ->
                Text(
                    "automatisch: " + when (rule) {
                        "STEPS" -> "ab 6.000 Schritten oder Training"
                        "SLEEP_PHONE" -> "Handy nach 22:30 nicht benutzt"
                        "SCREEN_SOCIAL" -> "Social Media & Videos unter 2 Std"
                        else -> rule
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                status.lastWeek.forEach { done ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (done) color else color.copy(alpha = 0.18f)),
                    )
                }
            }
        }
        if (status.streak.current > 0) Pill("${status.streak.current} Tage", color)
        IconButton(onClick = { viewModel.archiveHabit(status.habit) }) {
            Icon(Icons.Rounded.Delete, "Gewohnheit entfernen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GoalCard(item: GoalWithMilestones, viewModel: GoalsViewModel) {
    val color = item.goal.area.color
    var newStep by remember(item.goal.id) { mutableStateOf("") }
    var expanded by remember(item.goal.id) { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
            Text(item.goal.area.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.goal.title, style = MaterialTheme.typography.titleMedium)
                val next = item.nextStep
                Text(
                    if (next != null) "Nächster Schritt: ${next.title}" else "Geschafft!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    fontWeight = FontWeight.Medium,
                )
            }
            Pill("${(item.progress * 100).toInt()} %", color)
        }
        app.lifedashboard.ui.components.Meter(item.progress, color = color, modifier = Modifier.padding(vertical = 10.dp))
        item.goal.description?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            item.milestones.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app.lifedashboard.ui.components.CheckMark(checked = m.done, onCheckedChange = { viewModel.toggleMilestone(m) }, color = color)
                    Text(
                        m.title,
                        textDecoration = if (m.done) TextDecoration.LineThrough else null,
                        color = if (m.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newStep, { newStep = it }, placeholder = { Text("Neuer Schritt …") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(enabled = newStep.isNotBlank(), onClick = {
                    viewModel.addMilestone(item.goal.id, newStep.trim(), item.milestones.size)
                    newStep = ""
                }) { Icon(Icons.Rounded.Add, "Schritt hinzufügen") }
            }
            TextButton(onClick = { viewModel.deleteGoal(item.goal.id) }) { Text("Ziel löschen", color = MaterialTheme.colorScheme.error) }
        } else {
            Text("Antippen für alle Schritte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
