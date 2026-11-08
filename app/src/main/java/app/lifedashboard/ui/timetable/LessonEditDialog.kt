package app.lifedashboard.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.WeekPattern
import app.lifedashboard.data.repository.LessonEdit
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.components.longName
import app.lifedashboard.ui.components.minutesToTimeText
import java.time.DayOfWeek

@Composable
fun LessonEditDialog(
    day: DayOfWeek,
    slot: PeriodSlot,
    week: WeekType,
    subjects: List<SubjectEntity>,
    onSave: (LessonEdit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val original = slot.lesson?.lesson
    var subjectId by remember { mutableLongStateOf(original?.subjectId ?: subjects.firstOrNull()?.id ?: 0L) }
    var room by remember { mutableStateOf(original?.room ?: "") }
    val defaultPattern = original?.weekPattern ?: if (week == WeekType.A) WeekPattern.A else WeekPattern.B
    var pattern by remember { mutableStateOf(defaultPattern) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("${day.longName()} · ${slot.period.number}. Block")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${minutesToTimeText(slot.period.startMinute)}–${minutesToTimeText(slot.period.endMinute)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Fach", style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(subjects, key = { it.id }) { subject ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { subjectId = subject.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = subjectId == subject.id, onClick = { subjectId = subject.id })
                            Box(Modifier.size(10.dp).background(Color(subject.colorArgb), CircleShape))
                            Text("  ${subject.code}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "  ${subject.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Raum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Gilt für", style = MaterialTheme.typography.titleSmall)
                listOf(
                    WeekPattern.EVERY to "Jede Woche",
                    WeekPattern.A to "Nur Woche A",
                    WeekPattern.B to "Nur Woche B",
                ).forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { pattern = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pattern == value, onClick = { pattern = value })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = subjectId != 0L,
                onClick = {
                    onSave(
                        LessonEdit(
                            dayOfWeek = day.value,
                            period = slot.period.number,
                            original = original,
                            subjectId = subjectId,
                            room = room,
                            pattern = pattern,
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (original != null) {
                    TextButton(onClick = onDelete) {
                        Text("Löschen (Woche ${week.name})", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
