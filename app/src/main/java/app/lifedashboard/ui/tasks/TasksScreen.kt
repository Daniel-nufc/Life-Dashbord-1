package app.lifedashboard.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.repository.TaskRepository
import app.lifedashboard.data.repository.TimetableRepository
import app.lifedashboard.ui.components.EmptyState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.label
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import app.lifedashboard.ui.editors.QuickAddSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksData(val tasks: List<TaskEntity>, val subjects: Map<Long, SubjectEntity>)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    timetable: TimetableRepository,
) : ViewModel() {
    val data: StateFlow<TasksData?> = combine(repository.tasks, timetable.subjects) { t, s ->
        TasksData(t, s.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { repository.setDone(task, done) }
    }
}

/** Filter oben: alle, nur Hausaufgaben oder ein Bereich. */
private sealed interface TaskFilter {
    val label: String
    fun matches(task: TaskEntity): Boolean

    data object All : TaskFilter {
        override val label = "Alle"
        override fun matches(task: TaskEntity) = true
    }

    data object Homework : TaskFilter {
        override val label = "Hausaufgaben"
        override fun matches(task: TaskEntity) = task.kind == TaskKind.HOMEWORK
    }

    data class Area(val area: LifeArea) : TaskFilter {
        override val label = area.label
        override fun matches(task: TaskEntity) = task.area == area && task.kind != TaskKind.HOMEWORK
    }
}

private val filters: List<TaskFilter> = listOf(TaskFilter.All, TaskFilter.Homework) +
    listOf(LifeArea.ETSY, LifeArea.PRIVATE, LifeArea.SPORT, LifeArea.JOB, LifeArea.SCHOOL).map { TaskFilter.Area(it) }

@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }

    OneUiScaffold(title = "Aufgaben", floatingActionButton = { AddFab { showAdd = true } }) { padding ->
        val current = data ?: return@OneUiScaffold
        val filter = filters[filterIndex]
        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).toEpochDay()
        val visible = current.tasks.filter(filter::matches)
        val open = visible.filter { !it.done }
        val sections = listOf(
            "Überfällig" to open.filter { (it.dueEpochDay ?: Long.MAX_VALUE) < todayEpoch },
            "Heute" to open.filter { it.dueEpochDay == todayEpoch },
            "Morgen" to open.filter { it.dueEpochDay == todayEpoch + 1 },
            "Diese Woche" to open.filter { val d = it.dueEpochDay; d != null && d > todayEpoch + 1 && d <= endOfWeek },
            "Später" to open.filter { val d = it.dueEpochDay; d != null && d > endOfWeek && d > todayEpoch + 1 },
            "Ohne Datum" to open.filter { it.dueEpochDay == null },
        ).filter { it.second.isNotEmpty() }
        val done = visible.filter { it.done }.sortedByDescending { it.doneAtMillis ?: 0 }.take(20)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { app.lifedashboard.ui.editors.QuickInputBar(onRequest = { editing = it }) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filters.forEachIndexed { index, f ->
                            FilterChip(selected = index == filterIndex, onClick = { filterIndex = index }, label = { Text(f.label) })
                        }
                    }
                }
                if (sections.isEmpty()) {
                    item {
                        SectionCard {
                            EmptyState(Icons.Rounded.TaskAlt, "Nichts offen", "Neue Hausaufgabe oder Aufgabe? Tippe unten auf „Neu“.")
                        }
                    }
                }
                items(sections, key = { it.first }) { (title, list) ->
                    SectionCard(title = "$title · ${list.size}") {
                        list.forEach { task ->
                            TaskRow(
                                task = task,
                                subject = task.subjectId?.let { current.subjects[it] },
                                onToggle = { viewModel.toggle(task, it) },
                                onClick = { editing = task.toEditRequest() },
                            )
                        }
                    }
                }
                if (done.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showDone = !showDone }) {
                            Text(if (showDone) "Erledigte ausblenden" else "Erledigte anzeigen (${done.size})")
                        }
                    }
                    if (showDone) {
                        item {
                            SectionCard(title = "Erledigt") {
                                done.forEach { task ->
                                    TaskRow(
                                        task = task,
                                        subject = task.subjectId?.let { current.subjects[it] },
                                        onToggle = { viewModel.toggle(task, it) },
                                        onClick = { editing = task.toEditRequest() },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Tipp: Hausaufgaben werden automatisch zur nächsten Stunde des Fachs fällig – inkl. A/B-Woche.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAdd) {
        QuickAddSheet(
            date = LocalDate.now(),
            onPick = {
                showAdd = false
                editing = it
            },
            onDismiss = { showAdd = false },
        )
    }
    EditorHost(editing, onDismiss = { editing = null })
}

private fun TaskEntity.toEditRequest(): EditRequest =
    if (kind == TaskKind.HOMEWORK) EditRequest.Homework(this) else EditRequest.Todo(this, null)
