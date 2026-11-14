package app.lifedashboard.ui.calendar

import androidx.compose.foundation.layout.Box
import app.lifedashboard.ui.components.compactSchool
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.lifedashboard.ui.components.readable
import app.lifedashboard.ui.components.shortName
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

private fun hoursText(minutes: Int): String = if (minutes >= 60) "%d:%02d Std".format(minutes / 60, minutes % 60) else "$minutes Min"

private fun List<app.lifedashboard.data.db.PlanBlockEntity>.visible() = filter {
    it.status == app.lifedashboard.data.db.PlanStatus.PROPOSED || it.status == app.lifedashboard.data.db.PlanStatus.ACCEPTED || it.status == app.lifedashboard.data.db.PlanStatus.DONE
}

/**
 * Wochenansicht: oben die sieben Tage mit ihrer Auslastung (man sieht sofort, ob die Woche gleichmäßig ist),
 * darunter der gewählte Tag als Zeitstrahl. Am Tablet stehen Woche und Tag nebeneinander.
 */
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
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val trainingWeek by viewModel.trainingWeek.collectAsStateWithLifecycle()
    val range = week
    if (range == null) {
        LoadingState()
        return
    }
    val today = LocalDate.now()
    var selectedEpoch by rememberSaveable(weekStart) {
        mutableStateOf((if (!today.isBefore(weekStart) && !today.isAfter(weekStart.plusDays(6))) today else weekStart).toEpochDay())
    }
    val selected = range.days.firstOrNull { it.date.toEpochDay() == selectedEpoch } ?: range.days.first()
    val loads = range.days.associate { d -> d.date to plan.visible().filter { it.dateEpochDay == d.date.toEpochDay() }.sumOf { it.endMinute - it.startMinute } }
    val proposals = plan.count { it.status == app.lifedashboard.data.db.PlanStatus.PROPOSED }

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            topContent()
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousWeek) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Vorherige Woche")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("KW ${CalendarViewModel.weekNumber(weekStart)} · Woche ${range.days.first().weekType.name}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${weekStart.format(dayFormatter)} – ${weekStart.plusDays(6).format(dayFormatter)} · ${hoursText(loads.values.sum())} geplant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::nextWeek) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Nächste Woche")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (proposals > 0) {
                    app.lifedashboard.ui.components.MiniAction("Woche übernehmen ($proposals)", filled = true, onClick = viewModel::acceptWeek)
                }
                app.lifedashboard.ui.components.MiniAction("Neu planen", filled = false, onClick = viewModel::replanWeek)
                if (weekStart != planningMonday()) {
                    app.lifedashboard.ui.components.MiniAction("Aktuelle Woche", filled = false, onClick = viewModel::thisWeek)
                }
            }
        }
    }
    val dayPane: @Composable () -> Unit = {
        DayPane(
            selected, range, plan.filter { it.dateEpochDay == selected.date.toEpochDay() },
            trainingWeek.filter { it.session.date == selected.date }, viewModel, onAdd, onEdit, onOpenTimetable,
        )
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(
                    Modifier.width(340.dp),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { header() }
                    item {
                        SectionCard(focus = false) {
                            range.days.forEach { d ->
                                WeekRow(d, loads[d.date] ?: 0, d.date == selected.date) { selectedEpoch = d.date.toEpochDay() }
                            }
                        }
                    }
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 112.dp),
                ) { item { dayPane() } }
            }
        } else {
            LazyColumn(contentPadding = padding.listPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { header() }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        range.days.forEach { d ->
                            WeekCell(d, loads[d.date] ?: 0, d.date == selected.date, Modifier.weight(1f)) { selectedEpoch = d.date.toEpochDay() }
                        }
                    }
                }
                item { dayPane() }
            }
        }
    }
}

/** Ein Tag in der Wochenleiste (Handy): Wochentag, Datum, darunter ein kleiner Balken für die geplante Zeit. */
@Composable
private fun WeekCell(day: DayAgenda, minutes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val isToday = day.date == LocalDate.now()
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${day.date.dayOfWeek.longName()}, ${hoursText(minutes)} geplant" }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            day.date.dayOfWeek.shortName(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier.size(36.dp).background(if (selected) accent else androidx.compose.ui.graphics.Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    day.isSchoolFree -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        // Auslastung: volle Höhe = 3 Stunden
        Box(Modifier.width(5.dp).height(26.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight((minutes / 180f).coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(accent))
        }
        Box(Modifier.size(5.dp).background(if (day.exams.isNotEmpty()) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
    }
}

/** Ein Tag in der Wochenliste (Tablet): Name, Datum, Auslastung als Balken. */
@Composable
private fun WeekRow(day: DayAgenda, minutes: Int, selected: Boolean, onClick: () -> Unit) {
    val isToday = day.date == LocalDate.now()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(92.dp)) {
            Text(
                day.date.dayOfWeek.longName(),
                style = MaterialTheme.typography.titleSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                day.date.format(dayFormatter) + if (day.exams.isNotEmpty()) " · Klausur" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (day.exams.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        app.lifedashboard.ui.components.Meter((minutes / 180f).coerceIn(0f, 1f), modifier = Modifier.weight(1f), height = 5.dp)
        Text(
            if (minutes == 0) "–" else hoursText(minutes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DayPane(
    day: DayAgenda,
    range: AgendaRange,
    plan: List<app.lifedashboard.data.db.PlanBlockEntity>,
    trainings: List<app.lifedashboard.data.repository.PlannedTraining>,
    viewModel: CalendarViewModel,
    onAdd: (LocalDate) -> Unit,
    onEdit: (EditRequest) -> Unit,
    onOpenTimetable: () -> Unit,
) {
    val isToday = day.date == LocalDate.now()
    val planned = plan.visible()
    var miniFor by remember { mutableStateOf<app.lifedashboard.data.db.PlanBlockEntity?>(null) }
    miniFor?.let { b ->
        app.lifedashboard.ui.focus.MiniSportDialog(
            b,
            onMini = { viewModel.miniSport(b); miniFor = null },
            onDecline = { viewModel.decline(b); miniFor = null },
            onDismiss = { miniFor = null },
        )
    }
    SectionCard(focus = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("${day.date.dayOfWeek.longName()}, ${day.date.format(dayFormatter)}", style = MaterialTheme.typography.titleLarge)
                Text(
                    listOfNotNull(
                        if (planned.isEmpty()) "nichts geplant" else "${hoursText(planned.sumOf { it.endMinute - it.startMinute })} geplant",
                        planned.count { it.status == app.lifedashboard.data.db.PlanStatus.PROPOSED }.takeIf { it > 0 }?.let { "$it Vorschläge" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            day.holiday?.let { Pill(it.take(22), LifeArea.SPORT.color) }
            day.reservedBy?.let { Pill("Reserviert: ${it.take(18)}", LifeArea.PRIVATE.color) }
            if (isToday) Pill("Heute", MaterialTheme.colorScheme.primary)
            IconButton(onClick = { onAdd(day.date) }) { Icon(Icons.Rounded.Add, contentDescription = "Eintragen") }
        }

        // Feste Termine überschneiden sich (z. B. Spieltag und Schicht) – rechtzeitig klären
        day.conflicts().forEach { (a, b) ->
            Text(
                "Überschneidung: $a und $b",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Stunde fällt aus? Antippen → die Zeit wird frei und neu verplant (nochmal antippen = findet doch statt)
        val allLessons = (day.lessons + day.cancelledLessons).filter { it.period != null }.sortedBy { it.period!!.number }
        if (allLessons.isNotEmpty() && !day.date.isBefore(LocalDate.now())) {
            Text(
                if (day.cancelledLessons.isEmpty()) "Fällt etwas aus? Stunde antippen:" else "${day.cancelledLessons.size} ${if (day.cancelledLessons.size == 1) "Stunde fällt" else "Stunden fallen"} aus",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allLessons.forEach { l ->
                    val gone = l in day.cancelledLessons
                    androidx.compose.material3.FilterChip(
                        selected = gone,
                        onClick = { viewModel.setLessonCancelled(day.date, l.period!!.number, !gone) },
                        label = { Text("${l.period!!.number}. ${l.subject.code}" + if (gone) " · fällt aus" else "") },
                    )
                }
            }
        }

        // Ganztägiges: Klausuren und Termine ohne Uhrzeit
        day.exams.forEach { exam ->
            val subject = range.subjects[exam.subjectId]
            AllDayLine(
                Icons.Rounded.School, MaterialTheme.colorScheme.error,
                "${exam.kind.name.lowercase().replaceFirstChar { it.uppercase() }}: ${subject?.name ?: exam.title}",
                exam.title.ifBlank { null },
            ) { onEdit(EditRequest.Exam(exam)) }
        }
        day.events.filter { it.startMinute == null }.forEach { event ->
            AllDayLine(Icons.Rounded.Event, event.area.color, event.title, event.location ?: "ganztägig") { onEdit(EditRequest.Event(event, day.date)) }
        }
        day.externalEvents.filter { it.startMinute == null }.forEach { event ->
            AllDayLine(Icons.Rounded.Event, androidx.compose.ui.graphics.Color(event.color), event.title, event.calendarName.ifBlank { "ganztägig" }) {}
        }

        val entries = day.timeline(plan).compactSchool()
        if (entries.any { it.kind != EntryKind.FREE }) {
            app.lifedashboard.ui.components.DayTimeline(
                entries = entries,
                now = if (isToday) java.time.LocalTime.now() else null,
                actions = app.lifedashboard.ui.components.PlanActions(
                    accept = viewModel::accept,
                    done = viewModel::done,
                    decline = { b -> if (b.type == app.lifedashboard.planner.DemandType.SPORT && b.endMinute - b.startMinute > 15) miniFor = b else viewModel.decline(b) },
                    later = viewModel::later,
                    move = viewModel::move,
                ),
                modifier = Modifier.padding(top = 12.dp),
            ) { entry ->
                when (entry.kind) {
                    EntryKind.LESSON -> onOpenTimetable()
                    EntryKind.EVENT -> day.events.firstOrNull { it.id == entry.refId }?.let { onEdit(EditRequest.Event(it, day.date)) }
                    EntryKind.JOB -> day.shifts.firstOrNull { it.id == entry.refId }?.let { onEdit(EditRequest.Shift(it, day.date)) }
                    EntryKind.ROUTINE -> day.routines.firstOrNull { it.id == entry.refId }?.let { onEdit(EditRequest.Routine(it)) }
                    else -> Unit
                }
            }
        } else {
            Text(
                if (day.isSchoolFree) "Frei – genieß es." else "Nichts eingetragen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }

        // Sport-Zeitfenster – auch für Wochen ohne Tagesplan-Vorschläge
        trainings.filter { t -> plan.none { it.demandKey == t.session.key && it.status != app.lifedashboard.data.db.PlanStatus.DECLINED } }
            .forEach { t ->
                AllDayLine(
                    LifeArea.SPORT.icon, LifeArea.SPORT.color, t.session.title,
                    when {
                        t.done -> "Erledigt"
                        t.skipped -> "Ausgefallen"
                        else -> "${t.session.minutes} Min · Uhrzeit kommt mit dem Tagesplan"
                    },
                ) {}
            }
        if (day.tasksDue.isNotEmpty()) {
            app.lifedashboard.ui.components.SectionTitle("Fällig an diesem Tag", Modifier.padding(start = 0.dp))
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
    }
}

/** Zeile ohne Uhrzeit (Klausur, ganztägiger Termin, Sport-Zeitfenster). */
@Composable
private fun AllDayLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = color.readable(), modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
