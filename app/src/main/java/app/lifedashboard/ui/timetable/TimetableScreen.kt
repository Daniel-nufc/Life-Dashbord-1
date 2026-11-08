package app.lifedashboard.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.longName
import app.lifedashboard.ui.components.minutesToTimeText
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: TimetableViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pair<DayOfWeek, PeriodSlot>?>(null) }

    OneUiScaffold(title = "Stundenplan") { padding ->
        if (state.loading) {
            LoadingState()
            return@OneUiScaffold
        }
        // Handy: eine Spalte. Tablet: mehrere Tage nebeneinander.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            contentPadding = padding.listPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeekType.entries.forEachIndexed { index, week ->
                            SegmentedButton(
                                selected = state.selectedWeek == week,
                                onClick = { viewModel.selectWeek(week) },
                                shape = SegmentedButtonDefaults.itemShape(index, WeekType.entries.size),
                            ) {
                                Text(if (week == state.currentWeek) "Woche ${week.name} (aktuell)" else "Woche ${week.name}")
                            }
                        }
                    }
                    Text(
                        "Tippe auf einen Block, um ihn zu ändern oder einzutragen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                }
            }
            items(state.days, key = { it.day.value }) { column ->
                SectionCard(title = column.day.longName()) {
                    column.slots.forEach { slot ->
                        SlotRow(slot) { editing = column.day to slot }
                    }
                }
            }
        }
    }

    editing?.let { (day, slot) ->
        LessonEditDialog(
            day = day,
            slot = slot,
            week = state.selectedWeek,
            subjects = state.subjects,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = {
                slot.lesson?.let { item -> viewModel.delete(item.lesson, state.selectedWeek) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SlotRow(slot: PeriodSlot, onClick: () -> Unit) {
    val lesson = slot.lesson
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            Text(minutesToTimeText(slot.period.startMinute), style = MaterialTheme.typography.labelLarge)
            Text(minutesToTimeText(slot.period.endMinute), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(4.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(lesson?.let { Color(it.subject.colorArgb) } ?: MaterialTheme.colorScheme.outlineVariant),
        )
        if (lesson != null) {
            Column(Modifier.weight(1f)) {
                Text(lesson.subject.code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${lesson.subject.name} · ${lesson.lesson.room}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        } else {
            Text("frei", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}
