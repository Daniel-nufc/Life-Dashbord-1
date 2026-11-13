package app.lifedashboard.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import app.lifedashboard.ui.components.CheckMark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.label
import java.time.LocalDate

/** Eine Aufgabe mit Haken zum Abhaken. Farbe = Fach bzw. Bereich. */
@Composable
fun TaskRow(
    task: TaskEntity,
    subject: SubjectEntity?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val accent = subject?.let { Color(it.colorArgb) } ?: task.area.color
    val due = task.dueEpochDay?.let(LocalDate::ofEpochDay)
    val overdue = !task.done && due != null && due.isBefore(today)
    val context = buildList {
        add(if (task.kind == TaskKind.HOMEWORK) subject?.code ?: "Hausaufgabe" else task.area.label)
        due?.let { add(if (overdue) "überfällig (${it.friendly(today)})" else "fällig ${it.friendly(today)}") }
        add("${task.estimatedMinutes} Min")
    }.joinToString(" · ")

    val titleColor by animateColorAsState(
        if (task.done) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
        tween(260),
        label = "title",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CheckMark(checked = task.done, onCheckedChange = onToggle, color = accent)
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                context,
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
