package app.lifedashboard.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.IconBadge
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.hhmm
import app.lifedashboard.ui.components.icon
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.pretty
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import app.lifedashboard.ui.editors.QuickAddSheet
import app.lifedashboard.ui.tasks.TaskRow
import app.lifedashboard.data.repository.euro
import java.time.LocalDate

private val HeroColors = listOf(Color(0xFF6FD3A6))

@Composable
fun TodayScreen(
    onOpenTasks: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenProgress: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }

    val title = (state as? TodayUiState.Ready)?.greeting ?: "Heute"
    OneUiScaffold(title = title, floatingActionButton = { AddFab { showAdd = true } }) { padding ->
        when (val s = state) {
            TodayUiState.Loading -> LoadingState()
            is TodayUiState.Ready -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(340.dp),
                contentPadding = padding.listPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
            ) {
                item(span = StaggeredGridItemSpan.FullLine) { Hero(s) }
                if (s.now.dayOfWeek == java.time.DayOfWeek.SUNDAY || s.now.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
                    item(span = StaggeredGridItemSpan.FullLine) {
                        SectionCard(onClick = onOpenReview) {
                            TileHeader(Icons.Rounded.AutoAwesome, "Dein Wochenrückblick", LifeArea.SCHOOL.color)
                            Text("Was lief gut, was nicht – und Vorschläge für die neue Woche. 2 Minuten.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    app.lifedashboard.ui.editors.QuickInputBar(onRequest = { editing = it })
                }
                if (s.allDayEvents.isNotEmpty() || s.externalAllDay.isNotEmpty()) {
                    item {
                        SectionCard {
                            TileHeader(Icons.Rounded.Event, "Heute ganztägig", LifeArea.PRIVATE.color)
                            s.allDayEvents.forEach { event ->
                                Text(
                                    "• ${event.title}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { editing = EditRequest.Event(event, LocalDate.now()) }
                                        .padding(vertical = 4.dp),
                                )
                            }
                            s.externalAllDay.forEach { title ->
                                Text("• $title", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
                s.cards.forEach { card ->
                    when (card) {
                        "PROGRESS" -> progress?.let { p ->
                            item(key = card) { app.lifedashboard.ui.progress.ProgressCard(p, onClick = onOpenProgress) }
                        }
                        "NOW" -> item(key = card) { NowTile(s) }
                        "TASKS" -> item(key = card) {
                            TasksTile(
                                s,
                                onToggle = viewModel::toggleTask,
                                onEdit = { task ->
                                    editing = if (task.kind == app.lifedashboard.data.db.TaskKind.HOMEWORK) {
                                        EditRequest.Homework(task)
                                    } else {
                                        EditRequest.Todo(task, null)
                                    }
                                },
                                onOpenTasks = onOpenTasks,
                            )
                        }
                        "EXAM" -> item(key = card) { ExamTile(s) }
                        "HABITS" -> if (s.habits.isNotEmpty()) {
                            item(key = card) { HabitsTile(s, onToggle = viewModel::toggleHabit, onOpen = onOpenGoals) }
                        }
                        "MONEY" -> item(key = card) { MoneyTile(s) }
                        "ETSY" -> item(key = card) { EtsyTile(s) }
                        "TIMELINE" -> item(key = card, span = StaggeredGridItemSpan.FullLine) {
                            TimelineCard(
                                s,
                                actions = PlanActions(
                                    accept = viewModel::accept,
                                    done = viewModel::done,
                                    decline = viewModel::decline,
                                    later = viewModel::later,
                                    replan = viewModel::replanNow,
                                ),
                            ) { entry ->
                                editing = when (entry.kind) {
                                    EntryKind.EVENT -> s.events.firstOrNull { it.id == entry.refId }?.let { EditRequest.Event(it, LocalDate.now()) }
                                    EntryKind.JOB -> s.shifts.firstOrNull { it.id == entry.refId }?.let { EditRequest.Shift(it, LocalDate.now()) }
                                    EntryKind.ROUTINE -> s.routines.firstOrNull { it.id == entry.refId }?.let { EditRequest.Routine(it) }
                                    else -> null
                                }
                            }
                        }
                    }
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

@Composable
private fun Hero(s: TodayUiState.Ready) {
    GradientCard(HeroColors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.dateLabel, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Woche ${s.weekType.name}", Color.White.copy(alpha = 0.25f))
                    Pill(s.holiday ?: if (s.isSchoolDay) "Schultag" else "Schulfrei", Color.White.copy(alpha = 0.25f))
                }
                Spacer(Modifier.height(12.dp))
                if (s.examsToday.isNotEmpty()) {
                    Text("Heute: Klausur ${s.examsToday.joinToString()} – du schaffst das!", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(s.coachLine, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            val progress = if (s.freeTimeTotal.isZero) 0f else s.freeTimeLeft.toMinutes().toFloat() / s.freeTimeTotal.toMinutes()
            ProgressRing(progress = progress, color = Color(0xFF6FD3A6), size = 84.dp, stroke = 5.dp, trackColor = Color.White.copy(alpha = 0.12f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%d:%02d".format(s.freeTimeLeft.toHours(), s.freeTimeLeft.toMinutesPart()),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("frei", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun NowTile(s: TodayUiState.Ready) {
    SectionCard {
        val current = s.current
        val accent = current?.let { entryColor(it) } ?: MaterialTheme.colorScheme.primary
        TileHeader(Icons.Rounded.Bolt, "Jetzt", accent)
        if (current != null && current.kind != EntryKind.FREE) {
            Text(current.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("bis ${current.range.end.hhmm()}" + (current.subtitle?.let { " · $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Freie Zeit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (s.freeTimeLeft.isZero) "Für heute ist nichts mehr frei" else "Noch ${s.freeTimeLeft.pretty()} frei heute",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        s.next?.let { next ->
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(entryColor(next).copy(alpha = 0.12f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = entryColor(next))
                Text("Als Nächstes: ${next.title} um ${next.range.start.hhmm()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TasksTile(
    s: TodayUiState.Ready,
    onToggle: (app.lifedashboard.data.db.TaskEntity, Boolean) -> Unit,
    onEdit: (app.lifedashboard.data.db.TaskEntity) -> Unit,
    onOpenTasks: () -> Unit,
) {
    SectionCard {
        TileHeader(Icons.Rounded.ChecklistRtl, "Hausaufgaben & Aufgaben", LifeArea.SCHOOL.color) {
            if (s.openTaskCount > 0) Pill("${s.openTaskCount} offen", LifeArea.SCHOOL.color)
        }
        if (s.focusTasks.isEmpty()) {
            Text("Alles erledigt. Stark!", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Neue Hausaufgabe? Unten auf „Neu“ tippen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        s.focusTasks.forEach { task ->
            TaskRow(
                task = task,
                subject = task.subjectId?.let { s.subjects[it] },
                onToggle = { onToggle(task, it) },
                onClick = { onEdit(task) },
            )
        }
        TextButton(onClick = onOpenTasks, modifier = Modifier.align(Alignment.End)) { Text("Alle Aufgaben") }
    }
}

@Composable
private fun entryColor(entry: TimelineEntry): Color = when (entry.kind) {
    EntryKind.LESSON -> entry.colorArgb?.let { Color(it) } ?: LifeArea.SCHOOL.color
    EntryKind.COMMUTE -> MaterialTheme.colorScheme.outline
    EntryKind.FREE -> MaterialTheme.colorScheme.outlineVariant
    EntryKind.EVENT -> entry.colorArgb?.let { Color(it) } ?: entry.area?.color ?: MaterialTheme.colorScheme.primary
    else -> entry.area?.color ?: MaterialTheme.colorScheme.primary
}

private class PlanActions(
    val accept: (app.lifedashboard.data.db.PlanBlockEntity) -> Unit,
    val done: (app.lifedashboard.data.db.PlanBlockEntity) -> Unit,
    val decline: (app.lifedashboard.data.db.PlanBlockEntity) -> Unit,
    val later: (app.lifedashboard.data.db.PlanBlockEntity) -> Unit,
    val replan: () -> Unit,
)

@Composable
private fun TimelineCard(s: TodayUiState.Ready, actions: PlanActions, onEntryClick: (TimelineEntry) -> Unit) {
    SectionCard {
        TileHeader(Icons.Rounded.Schedule, "Tagesplan", MaterialTheme.colorScheme.primary) {
            if (s.proposals > 0) Pill("${s.proposals} Vorschläge", LifeArea.ETSY.color)
            IconButton(onClick = actions.replan) { Icon(Icons.Rounded.Refresh, contentDescription = "Neu planen") }
        }
        if (s.plannedBlocks > 0) {
            Text(
                "Vorschläge annehmen ✓, verschieben oder ablehnen ✕ – die App lernt, was zu dir passt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (s.entries.isEmpty()) {
            Text("Keine Einträge", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val time = s.now.toLocalTime()
        s.entries.forEach { entry ->
            TimelineRow(
                entry = entry,
                isNow = time in entry.range,
                isPast = !entry.range.end.isAfter(time),
                onClick = if (entry.refId != null && entry.plan == null) ({ onEntryClick(entry) }) else null,
            )
            entry.plan?.let { block -> PlanButtons(block, actions) }
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, isNow: Boolean, isPast: Boolean, onClick: (() -> Unit)?) {
    val accent = entryColor(entry)
    val alpha = if (isPast) 0.45f else 1f
    val rowBackground = when {
        isNow -> accent.copy(alpha = 0.16f)
        entry.kind == EntryKind.FREE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowBackground)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(50.dp)) {
            Text(entry.range.start.hhmm(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(entry.range.end.hhmm(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .width(5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(accent.copy(alpha = alpha)),
        )
        when (entry.kind) {
            EntryKind.EVENT, EntryKind.JOB, EntryKind.ROUTINE, EntryKind.PLAN -> {
                IconBadge(entry.area?.icon ?: Icons.Rounded.Event, accent.copy(alpha = alpha), size = 30.dp)
                Spacer(Modifier.width(10.dp))
            }
            EntryKind.COMMUTE -> {
                IconBadge(Icons.Rounded.DirectionsBus, accent.copy(alpha = alpha), size = 30.dp)
                Spacer(Modifier.width(10.dp))
            }
            else -> Unit
        }
        Column(Modifier.weight(1f)) {
            if (entry.kind == EntryKind.FREE) {
                Text(
                    "Frei · ${entry.range.duration.pretty()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            } else {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                entry.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                }
            }
        }
        if (isNow) Pill("jetzt", accent)
    }
}

@Composable
private fun PlanButtons(block: app.lifedashboard.data.db.PlanBlockEntity, actions: PlanActions) {
    val status = block.status
    if (status == app.lifedashboard.data.db.PlanStatus.DONE) return
    Row(
        Modifier.fillMaxWidth().padding(start = 66.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (status == app.lifedashboard.data.db.PlanStatus.PROPOSED) {
            FilledTonalButton(onClick = { actions.accept(block) }) { Text("Passt") }
        } else {
            FilledTonalButton(onClick = { actions.done(block) }) { Text("Erledigt") }
        }
        OutlinedButton(onClick = { actions.later(block) }) { Text("Später") }
        IconButton(onClick = { actions.decline(block) }) { Icon(Icons.Rounded.Close, contentDescription = "Ablehnen", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ExamTile(s: TodayUiState.Ready) {
    SectionCard {
        val exam = s.nextExam
        TileHeader(Icons.Rounded.AutoAwesome, "Nächste Klausur", Color(0xFFE53935))
        if (exam == null) {
            Text("Keine Klausur eingetragen.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Unter „Lernen“ eintragen – dann plant die App deine Lernzeit automatisch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val days = exam.daysLeft()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exam.subject?.name ?: exam.exam.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (days == 0) "Heute!" else if (days == 1) "Morgen" else "in $days Tagen",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProgressRing(exam.progress / 100f, Color(0xFFE53935), size = 60.dp, stroke = 7.dp) {
                    Text("${exam.progress}%", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HabitsTile(s: TodayUiState.Ready, onToggle: (Long, Boolean) -> Unit, onOpen: () -> Unit) {
    SectionCard {
        val done = s.habits.count { it.doneToday }
        TileHeader(Icons.Rounded.AutoAwesome, "Gewohnheiten", LifeArea.ETSY.color) {
            Pill("$done/${s.habits.size}", LifeArea.ETSY.color)
        }
        s.habits.forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                app.lifedashboard.ui.components.CheckMark(checked = h.doneToday, onCheckedChange = { onToggle(h.habit.id, it) }, color = LifeArea.ETSY.color)
                Text(h.habit.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (h.streak.current > 1) Text("${h.streak.current} Tage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) { Text("Ziele ansehen") }
    }
}

@Composable
private fun MoneyTile(s: TodayUiState.Ready) {
    SectionCard {
        TileHeader(Icons.Rounded.Euro, "Geld diesen Monat", LifeArea.JOB.color)
        Text(s.availableCents.euroText(), style = MaterialTheme.typography.headlineMedium)
        Text("verfügbar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        if (s.budgetTotalCents > 0) {
            val ratio = (s.monthSpentCents.toFloat() / s.budgetTotalCents).coerceAtMost(1f)
            Text("Ausgegeben ${s.monthSpentCents.euroText()} von ${s.budgetTotalCents.euroText()} Budget", style = MaterialTheme.typography.bodyMedium)
            app.lifedashboard.ui.components.Meter(
                ratio,
                color = if (ratio >= 0.8f) app.lifedashboard.ui.theme.LocalAppColors.current.warning else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Text("Ausgegeben: ${s.monthSpentCents.euroText()} · Budgets unter Mehr → Finanzen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EtsyTile(s: TodayUiState.Ready) {
    SectionCard {
        TileHeader(Icons.Rounded.Storefront, "Etsy", LifeArea.ETSY.color) {
            Pill("${s.etsyOrders} Best.", LifeArea.ETSY.color)
        }
        Text("Umsatz diesen Monat: ${s.etsyRevenueCents.euroText()}", style = MaterialTheme.typography.titleMedium)
        s.etsyNextStep?.let { Text("Nächster Schritt: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun Long.euroText() = euro()
