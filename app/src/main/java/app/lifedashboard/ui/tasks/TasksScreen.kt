package app.lifedashboard.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.PlanBlockEntity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.planning.PlanningEngine
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.data.repository.TaskRepository
import app.lifedashboard.data.repository.TimetableRepository
import app.lifedashboard.planner.DemandType
import app.lifedashboard.ui.components.CheckMark
import app.lifedashboard.ui.components.EmptyState
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.LocalDockInset
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.label
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.wideItem
import app.lifedashboard.ui.components.longName
import app.lifedashboard.ui.components.readable
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import app.lifedashboard.ui.editors.QuickAddSheet
import app.lifedashboard.ui.focus.FocusMode
import app.lifedashboard.ui.theme.LocalAppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksData(
    val tasks: List<TaskEntity>,
    val subjects: Map<Long, SubjectEntity>,
    /** Nächster geplanter Block je Aufgabe (aus dem Tagesplan, 14 Tage) */
    val plannedFor: Map<Long, PlanBlockEntity>,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    timetable: TimetableRepository,
    private val engine: PlanningEngine,
    private val study: StudyRepository,
) : ViewModel() {
    private val today = LocalDate.now()

    val data: StateFlow<TasksData?> = combine(repository.tasks, timetable.subjects, engine.observeRange(today, today.plusDays(14))) { t, s, plan ->
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val upcoming = plan
            .filter { it.status == PlanStatus.PROPOSED || it.status == PlanStatus.ACCEPTED }
            .filter { it.dateEpochDay > today.toEpochDay() || it.endMinute > now }
            .filter { it.demandKey.startsWith("task:") }
            .sortedWith(compareBy({ it.dateEpochDay }, { it.startMinute }))
        val planned = upcoming.groupBy { it.demandKey.removePrefix("task:").toLongOrNull() ?: -1 }.mapValues { it.value.first() }
        TasksData(t, s.associateBy { it.id }, planned)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { repository.setDone(task, done) }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { repository.delete(task.id) }
    }

    fun restore(task: TaskEntity) {
        viewModelScope.launch { repository.save(task) }
    }

    /** Im Fokus-Modus erledigt: Aufgabe abhaken, Lernzeit zählen, geplanten Block heute als erledigt markieren. */
    fun focusDone(task: TaskEntity, block: PlanBlockEntity?, minutes: Int) {
        viewModelScope.launch {
            if (block != null && block.id > 0 && block.dateEpochDay == LocalDate.now().toEpochDay()) runCatching { engine.done(block) }
            repository.setDone(task, true)
            if (task.area == LifeArea.SCHOOL || task.kind == TaskKind.HOMEWORK) {
                study.recordSession(task.subjectId, minutes, System.currentTimeMillis() - minutes * 60_000L)
            }
        }
    }
}

/** Filter oben: alle, Hausaufgaben oder ein Bereich. */
private enum class Filter(val label: String) {
    ALL("Alle"), HOMEWORK("Hausaufgaben"), SCHOOL("Schule"), ETSY("Etsy"), PRIVATE("Privat"), SPORT("Sport"), JOB("Job");

    fun matches(task: TaskEntity) = when (this) {
        ALL -> true
        HOMEWORK -> task.kind == TaskKind.HOMEWORK
        SCHOOL -> task.area == LifeArea.SCHOOL && task.kind != TaskKind.HOMEWORK
        ETSY -> task.area == LifeArea.ETSY
        PRIVATE -> task.area == LifeArea.PRIVATE
        SPORT -> task.area == LifeArea.SPORT
        JOB -> task.area == LifeArea.JOB
    }
}

private data class Section(val key: String, val title: String, val tasks: List<TaskEntity>, val urgent: Boolean = false)

private fun hm(minutes: Int) = if (minutes >= 60) "%d:%02d Std".format(minutes / 60, minutes % 60) else "$minutes Min"
private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun TaskEntity.doneDate(): LocalDate? = doneAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

/** Offene Aufgaben nach Tagen sortiert: überfällig, heute, morgen, die Wochentage, nächste Woche, später, ohne Datum. */
private fun openSections(open: List<TaskEntity>, today: LocalDate): List<Section> {
    val t = today.toEpochDay()
    val sunday = today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).toEpochDay()
    val result = mutableListOf<Section>()
    result += Section("overdue", "Überfällig", open.filter { (it.dueEpochDay ?: Long.MAX_VALUE) < t }, urgent = true)
    result += Section("today", "Heute", open.filter { it.dueEpochDay == t })
    result += Section("tomorrow", "Morgen", open.filter { it.dueEpochDay == t + 1 })
    (t + 2..sunday).forEach { day ->
        result += Section("d$day", LocalDate.ofEpochDay(day).dayOfWeek.longName(), open.filter { it.dueEpochDay == day })
    }
    result += Section("next", "Nächste Woche", open.filter { val d = it.dueEpochDay; d != null && d > sunday && d <= sunday + 7 })
    result += Section("later", "Später", open.filter { val d = it.dueEpochDay; d != null && d > sunday + 7 })
    result += Section("someday", "Irgendwann", open.filter { it.dueEpochDay == null })
    return result.filter { it.tasks.isNotEmpty() }
}

private fun doneSections(done: List<TaskEntity>, today: LocalDate): List<Section> {
    val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val sorted = done.sortedByDescending { it.doneAtMillis ?: 0 }
    return listOf(
        Section("d-today", "Heute erledigt", sorted.filter { it.doneDate() == today }),
        Section("d-week", "Diese Woche", sorted.filter { val d = it.doneDate(); d != null && d != today && !d.isBefore(monday) }),
        Section("d-older", "Früher", sorted.filter { val d = it.doneDate(); d == null || d.isBefore(monday) }.take(30)),
    ).filter { it.tasks.isNotEmpty() }
}

/**
 * Aufgaben: oben eine Kopfkarte mit dem, was heute ansteht, und der nächsten Aufgabe zum Starten.
 * Darunter die Aufgaben nach Tagen. Wischen nach rechts = erledigt, nach links = löschen (mit Rückgängig).
 */
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }
    var focus by remember { mutableStateOf<Pair<TaskEntity, PlanBlockEntity?>?>(null) }
    var deleted by remember { mutableStateOf<TaskEntity?>(null) }
    var undoTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(undoTick) {
        if (deleted != null) {
            delay(4500)
            deleted = null
        }
    }

    OneUiScaffold(title = "Aufgaben", floatingActionButton = { AddFab { showAdd = true } }) { padding ->
        val current = data
        if (current == null) {
            LoadingState()
            return@OneUiScaffold
        }
        val today = LocalDate.now()
        val visible = current.tasks.filter(filter::matches)
        val open = visible.filter { !it.done }
        val done = visible.filter { it.done }
        val sections = if (showDone) doneSections(done, today) else openSections(open, today)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                wideItem(key = "hero") {
                    TasksHero(current, today, onStart = { task, block -> focus = task to block }, onOpen = { editing = it.toEditRequest() })
                }
                wideItem(key = "switch") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Segmented(
                            left = "Offen · ${open.size}", right = "Erledigt · ${done.count { it.doneDate()?.let { d -> !d.isBefore(today.minusDays(6)) } == true }}",
                            rightSelected = showDone, onSelect = { showDone = it },
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Filter.entries.forEach { f ->
                                val count = current.tasks.count { !it.done && f.matches(it) }
                                FilterPill(if (f == Filter.ALL || count == 0) f.label else "${f.label} $count", f == filter) { filter = f }
                            }
                        }
                    }
                }
                if (sections.isEmpty()) {
                    wideItem(key = "empty") {
                        SectionCard {
                            if (showDone) {
                                EmptyState(Icons.Rounded.TaskAlt, "Noch nichts erledigt", "Hak die erste Aufgabe ab – das zählt 15 Punkte.")
                            } else {
                                EmptyState(Icons.Rounded.TaskAlt, "Alles erledigt", "Nichts offen. Neue Hausaufgabe? Tippe unten auf „Neu“.")
                            }
                        }
                    }
                }
                sections.forEach { section ->
                    wideItem(key = "h-${section.key}") { SectionHeader(section) }
                    items(section.tasks, key = { "t${it.id}" }) { task ->
                        TaskCard(
                            task = task,
                            subject = task.subjectId?.let { current.subjects[it] },
                            planned = current.plannedFor[task.id],
                            today = today,
                            onToggle = { viewModel.toggle(task, it) },
                            onOpen = { editing = task.toEditRequest() },
                            onStart = { focus = task to current.plannedFor[task.id] },
                            onDelete = {
                                viewModel.delete(task)
                                deleted = task
                                undoTick++
                            },
                        )
                    }
                }
                wideItem(key = "hint") {
                    Text(
                        "Wischen nach rechts = erledigt, nach links = löschen. Hausaufgaben werden automatisch zur nächsten Stunde fällig und spätestens am Vortag eingeplant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }

            // Rückgängig nach dem Löschen
            AnimatedVisibility(
                visible = deleted != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalDockInset.current + 84.dp, start = 16.dp, end = 16.dp),
            ) {
                val d = deleted
                Row(
                    Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "„${d?.title.orEmpty()}“ gelöscht",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Rückgängig",
                        color = MaterialTheme.colorScheme.inversePrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                            d?.let(viewModel::restore)
                            deleted = null
                        }.padding(horizontal = 12.dp, vertical = 10.dp),
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
    focus?.let { (task, block) ->
        val minutes = block?.let { it.endMinute - it.startMinute } ?: task.estimatedMinutes.coerceAtLeast(10)
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val focusBlock = PlanBlockEntity(
            id = block?.id ?: -task.id,
            dateEpochDay = LocalDate.now().toEpochDay(),
            startMinute = now,
            endMinute = now + minutes,
            type = if (task.kind == TaskKind.HOMEWORK) DemandType.HOMEWORK else if (task.area == LifeArea.SPORT) DemandType.SPORT else if (task.area == LifeArea.ETSY) DemandType.ETSY else DemandType.TASK,
            demandKey = "task:${task.id}",
            title = task.title,
            refId = task.id,
            status = PlanStatus.ACCEPTED,
            createdAtMillis = System.currentTimeMillis(),
        )
        FocusMode(focusBlock, onDone = { viewModel.focusDone(task, block, minutes) }, onClose = { focus = null })
    }
}

/** Kopfkarte: Ring = Wochenquote, große Zahl = heute zu tun, darunter die nächste Aufgabe mit „Starten“. */
@Composable
private fun TasksHero(data: TasksData, today: LocalDate, onStart: (TaskEntity, PlanBlockEntity?) -> Unit, onOpen: (TaskEntity) -> Unit) {
    val tones = LocalAppColors.current
    val t = today.toEpochDay()
    val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6).toEpochDay()
    val open = data.tasks.filter { !it.done }
    val overdue = open.filter { (it.dueEpochDay ?: Long.MAX_VALUE) < t }
    val dueSoon = open.filter { it.dueEpochDay != null && it.dueEpochDay <= t + 1 }
    val doneWeek = data.tasks.count { it.done && it.doneDate()?.let { d -> !d.isBefore(monday) } == true }
    val openWeek = open.count { (it.dueEpochDay ?: Long.MAX_VALUE) <= sunday }
    val quota = if (doneWeek + openWeek == 0) 0f else doneWeek.toFloat() / (doneWeek + openWeek)
    // Nächste Aufgabe: überfällig zuerst, dann nach Abgabe, dann nach geplantem Block
    val next = open.sortedWith(compareBy({ it.dueEpochDay ?: Long.MAX_VALUE }, { data.plannedFor[it.id]?.dateEpochDay ?: Long.MAX_VALUE })).firstOrNull()
    val accent = next?.let { task -> task.subjectId?.let { data.subjects[it] }?.let { Color(it.colorArgb) } ?: task.area.color } ?: MaterialTheme.colorScheme.primary

    GradientCard(listOf(accent, tones.glow)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BIS MORGEN FÄLLIG", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${dueSoon.size}", color = Color.White, fontSize = 52.sp, style = MaterialTheme.typography.displaySmall)
                    Text(
                        "  ${hm(dueSoon.sumOf { it.estimatedMinutes })}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                if (overdue.isNotEmpty()) {
                    Text("${overdue.size} überfällig – die zuerst.", color = tones.warning, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(if (dueSoon.isEmpty()) "Nichts Dringendes. Stark." else "Heute erledigen, morgen entspannt.", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            ProgressRing(quota, MaterialTheme.colorScheme.primary, size = 84.dp, stroke = 7.dp, trackColor = Color.White.copy(alpha = 0.10f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$doneWeek/${doneWeek + openWeek}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("Woche", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (next != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable { onOpen(next) }
                    .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(50)).background(accent))
                Column(Modifier.weight(1f)) {
                    Text("ALS NÄCHSTES", color = accent, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                    Text(next.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onStart(next, data.plannedFor[next.id]) }
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    Text("Starten", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Segmented(left: String, right: String, rightSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val tones = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(tones.card)
            .border(1.dp, tones.hairline, RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        listOf(false to left, true to right).forEach { (value, label) ->
            val selected = rightSelected == value
            val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, tween(220), label = "seg")
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val tones = LocalAppColors.current
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f) else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else tones.hairline, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionHeader(section: Section) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            section.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (section.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${section.tasks.size} · ${hm(section.tasks.sumOf { it.estimatedMinutes })}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Eine Aufgabe als Karte: Farbbalken (Fach/Bereich), Haken, Titel, wann eingeplant, Starten. Wischbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: TaskEntity,
    subject: SubjectEntity?,
    planned: PlanBlockEntity?,
    today: LocalDate,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDelete: () -> Unit,
) {
    val tones = LocalAppColors.current
    val accent = subject?.let { Color(it.colorArgb) } ?: task.area.color
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onToggle(!task.done); true }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (toRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (toRight) Icons.Rounded.Check else Icons.Rounded.DeleteOutline,
                    null,
                    tint = if (toRight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (toRight) (if (task.done) "Wieder offen" else "Erledigt") else "Löschen",
                    color = if (toRight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(20.dp))
                .background(tones.card)
                .border(1.dp, tones.hairline, RoundedCornerShape(20.dp))
                .clickable(onClick = onOpen)
                .semantics { contentDescription = task.title },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(accent.copy(alpha = if (task.done) 0.3f else 1f)))
            Spacer(Modifier.width(4.dp))
            CheckMark(checked = task.done, onCheckedChange = onToggle, color = accent)
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(subject?.code ?: if (task.kind == TaskKind.HOMEWORK) "Hausaufgabe" else task.area.label, accent)
                    Text(hm(task.estimatedMinutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!task.done) {
                        val due = task.dueEpochDay?.let(LocalDate::ofEpochDay)
                        val planText = planned?.let { b ->
                            val d = LocalDate.ofEpochDay(b.dateEpochDay)
                            val day = when (d) {
                                today -> "heute"
                                today.plusDays(1) -> "morgen"
                                else -> d.dayOfWeek.longName().take(2)
                            }
                            "· eingeplant $day ${clock(b.startMinute)}"
                        } ?: if (due != null && due.isBefore(today)) "· überfällig" else null
                        planText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (planned == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (!task.done) {
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable(onClick = onStart)
                        .semantics { contentDescription = "Starten: ${task.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color.readable(),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun TaskEntity.toEditRequest(): EditRequest =
    if (kind == TaskKind.HOMEWORK) EditRequest.Homework(this) else EditRequest.Todo(this, null)
