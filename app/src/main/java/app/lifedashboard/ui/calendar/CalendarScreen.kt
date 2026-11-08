package app.lifedashboard.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.data.repository.AgendaRange
import app.lifedashboard.data.repository.DayAgenda
import app.lifedashboard.ui.components.GERMAN
import app.lifedashboard.ui.components.IconBadge
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.hhmm
import app.lifedashboard.ui.components.icon
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.longName
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import app.lifedashboard.ui.editors.QuickAddSheet
import app.lifedashboard.ui.tasks.TaskRow
import app.lifedashboard.ui.timetable.TimetableContent
import app.lifedashboard.ui.today.EntryKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addForDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }

    val tabSwitcher: @Composable () -> Unit = {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Woche", "Stundenplan").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
    }

    OneUiScaffold(
        title = "Kalender",
        floatingActionButton = { if (tab == 0) AddFab { addForDate = LocalDate.now() } },
    ) { padding ->
        if (tab == 0) {
            WeekContent(
                padding = padding,
                topContent = tabSwitcher,
                viewModel = viewModel,
                onAdd = { addForDate = it },
                onEdit = { editing = it },
                onOpenTimetable = { tab = 1 },
            )
        } else {
            TimetableContent(padding = padding, topContent = tabSwitcher)
        }
    }

    addForDate?.let { date ->
        QuickAddSheet(
            date = date,
            onPick = {
                addForDate = null
                editing = it
            },
            onDismiss = { addForDate = null },
        )
    }
    EditorHost(editing, onDismiss = { editing = null })
}

private val dayFormatter = DateTimeFormatter.ofPattern("d. MMM", GERMAN)

@Composable
private fun WeekContent(
    padding: PaddingValues,
    topContent: @Composable () -> Unit,
    viewModel: CalendarViewModel,
    onAdd: (LocalDate) -> Unit,
    onEdit: (EditRequest) -> Unit,
    onOpenTimetable: () -> Unit,
) {
    val week by viewModel.week.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val range = week
    if (range == null) {
        LoadingState()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = padding.listPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { topContent() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousWeek) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Vorherige Woche")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "KW ${CalendarViewModel.weekNumber(weekStart)} · Woche ${range.days.first().weekType.name}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${weekStart.format(dayFormatter)} – ${weekStart.plusDays(6).format(dayFormatter)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::nextWeek) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Nächste Woche")
                }
            }
            if (weekStart != planningMonday()) {
                TextButton(onClick = viewModel::thisWeek) { Text("Zur aktuellen Woche") }
            }
        }
        items(range.days, key = { it.date.toEpochDay() }) { day ->
            DayCard(day, range, viewModel, onAdd, onEdit, onOpenTimetable)
        }
    }
}

@Composable
private fun DayCard(
    day: DayAgenda,
    range: AgendaRange,
    viewModel: CalendarViewModel,
    onAdd: (LocalDate) -> Unit,
    onEdit: (EditRequest) -> Unit,
    onOpenTimetable: () -> Unit,
) {
    val isToday = day.date == LocalDate.now()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    day.date.dayOfWeek.longName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(day.date.format(dayFormatter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isToday) Pill("Heute", MaterialTheme.colorScheme.primary)
            IconButton(onClick = { onAdd(day.date) }) { Icon(Icons.Rounded.Add, contentDescription = "Eintragen") }
        }

        var empty = true
        if (day.lessons.isNotEmpty()) {
            empty = false
            val first = day.lessons.mapNotNull { it.timeRange }.minOfOrNull { it.start }
            val last = day.lessons.mapNotNull { it.timeRange }.maxOfOrNull { it.end }
            AgendaLine(
                time = first?.hhmm() ?: "",
                icon = Icons.Rounded.School,
                color = LifeArea.SCHOOL.color,
                title = "Schule bis ${last?.hhmm() ?: ""}",
                subtitle = day.lessons.joinToString(" · ") { it.subject.code },
                onClick = onOpenTimetable,
            )
        }
        day.events.filter { it.startMinute == null }.forEach { event ->
            empty = false
            AgendaLine("ganzt.", Icons.Rounded.Event, event.area.color, event.title, event.location) {
                onEdit(EditRequest.Event(event, day.date))
            }
        }
        day.externalEvents.filter { it.startMinute == null }.forEach { event ->
            empty = false
            AgendaLine("ganzt.", Icons.Rounded.Event, androidx.compose.ui.graphics.Color(event.color), event.title, event.calendarName) {}
        }
        day.timeline()
            .filter { it.kind == EntryKind.EVENT || it.kind == EntryKind.JOB || it.kind == EntryKind.ROUTINE }
            .forEach { entry ->
                empty = false
                val color = entry.colorArgb?.let { androidx.compose.ui.graphics.Color(it) } ?: entry.area?.color ?: MaterialTheme.colorScheme.primary
                AgendaLine(
                    time = entry.range.start.hhmm(),
                    icon = entry.area?.icon ?: Icons.Rounded.Event,
                    color = color,
                    title = entry.title,
                    subtitle = "bis ${entry.range.end.hhmm()}" + (entry.subtitle?.let { " · $it" } ?: ""),
                ) {
                    val request = when (entry.kind) {
                        EntryKind.EVENT -> day.events.firstOrNull { it.id == entry.refId }?.let { EditRequest.Event(it, day.date) }
                        EntryKind.JOB -> day.shifts.firstOrNull { it.id == entry.refId }?.let { EditRequest.Shift(it, day.date) }
                        else -> day.routines.firstOrNull { it.id == entry.refId }?.let { EditRequest.Routine(it) }
                    }
                    request?.let(onEdit)
                }
            }
        if (day.tasksDue.isNotEmpty()) {
            empty = false
            Text(
                "Fällig",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            day.tasksDue.forEach { task ->
                TaskRow(
                    task = task,
                    subject = task.subjectId?.let { range.subjects[it] },
                    onToggle = { viewModel.toggleTask(task, it) },
                    onClick = {
                        onEdit(if (task.kind == TaskKind.HOMEWORK) EditRequest.Homework(task) else EditRequest.Todo(task, null))
                    },
                )
            }
        }
        if (empty) {
            Text(
                if (day.isWeekend) "Frei – genieß es 😎" else "Nichts eingetragen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun AgendaLine(
    time: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        IconBadge(icon, color, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
