package app.lifedashboard.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.ExamKind
import app.lifedashboard.data.db.GradeEntity
import app.lifedashboard.data.db.GradeKind
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.pickDate
import app.lifedashboard.ui.editors.ChoiceChips
import app.lifedashboard.ui.editors.FieldLabel
import app.lifedashboard.ui.editors.ValueButton
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectChips(subjects: List<SubjectEntity>, selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        subjects.forEach { subject ->
            FilterChip(
                selected = selected == subject.id,
                onClick = { onSelect(subject.id) },
                label = { Text(subject.code) },
                leadingIcon = { Box(Modifier.size(10.dp).background(Color(subject.colorArgb), CircleShape)) },
            )
        }
    }
}

@Composable
private fun DialogFrame(
    title: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
fun ExamDialog(
    original: ExamEntity?,
    subjects: List<SubjectEntity>,
    onSave: (ExamEntity, List<String>) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    preset: ExamEntity? = null,
) {
    val context = LocalContext.current
    val start = original ?: preset
    var subjectId by remember { mutableStateOf(start?.subjectId) }
    var date by remember { mutableStateOf(start?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: LocalDate.now().plusDays(14)) }
    var kind by remember { mutableStateOf(start?.kind ?: ExamKind.KLAUSUR) }
    var title by remember { mutableStateOf(start?.title ?: "") }
    var topics by remember { mutableStateOf("") }

    DialogFrame(
        title = if (original == null) "Neue Klausur" else "Klausur bearbeiten",
        canSave = subjectId != null,
        onDismiss = onDismiss,
        onDelete = original?.let { { onDelete(it.id) } },
        onSave = {
            onSave(
                ExamEntity(
                    id = original?.id ?: 0,
                    subjectId = subjectId!!,
                    dateEpochDay = date.toEpochDay(),
                    title = title.trim(),
                    kind = kind,
                    note = original?.note,
                ),
                topics.split('\n', ',', ';').map { it.trim() }.filter { it.isNotEmpty() },
            )
        },
    ) {
        FieldLabel("Fach")
        SubjectChips(subjects, subjectId) { subjectId = it }
        ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
        FieldLabel("Art")
        ChoiceChips(
            listOf(ExamKind.KLAUSUR to "Klausur", ExamKind.TEST to "Test", ExamKind.REFERAT to "Referat", ExamKind.PRAESENTATION to "Präsentation"),
            kind,
        ) { kind = it }
        OutlinedTextField(title, { title = it }, label = { Text("Thema/Titel (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            topics,
            { topics = it },
            label = { Text(if (original == null) "Themen (eins pro Zeile oder mit Komma)" else "Weitere Themen hinzufügen") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Jedes Thema startet mit ca. 1 Std Aufwand. Schwere Fächer bekommen automatisch mehr Zeit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GradeDialog(
    original: GradeEntity?,
    presetSubjectId: Long?,
    subjects: List<SubjectEntity>,
    onSave: (GradeEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var subjectId by remember { mutableStateOf(original?.subjectId ?: presetSubjectId) }
    var points by remember { mutableFloatStateOf((original?.points ?: 10).toFloat()) }
    var kind by remember { mutableStateOf(original?.kind ?: GradeKind.KLAUSUR) }
    var weight by remember { mutableDoubleStateOf(original?.weight ?: 1.0) }
    var date by remember { mutableStateOf(original?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: LocalDate.now()) }

    DialogFrame(
        title = if (original == null) "Note eintragen" else "Note bearbeiten",
        canSave = subjectId != null,
        onDismiss = onDismiss,
        onDelete = original?.let { { onDelete(it.id) } },
        onSave = {
            onSave(
                GradeEntity(
                    id = original?.id ?: 0,
                    subjectId = subjectId!!,
                    points = points.toInt(),
                    kind = kind,
                    weight = weight,
                    dateEpochDay = date.toEpochDay(),
                    note = original?.note,
                ),
            )
        },
    ) {
        FieldLabel("Fach")
        SubjectChips(subjects, subjectId) { subjectId = it }
        FieldLabel("Punkte: ${points.toInt()}")
        Slider(value = points, onValueChange = { points = it }, valueRange = 0f..15f, steps = 14)
        FieldLabel("Art")
        ChoiceChips(
            listOf(GradeKind.KLAUSUR to "Klausur", GradeKind.MUENDLICH to "mündlich", GradeKind.TEST to "Test", GradeKind.SONSTIGES to "sonstige"),
            kind,
        ) { kind = it }
        FieldLabel("Gewicht")
        ChoiceChips(listOf(0.5 to "halb", 1.0 to "normal", 2.0 to "doppelt"), weight) { weight = it }
        ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
    }
}

@Composable
fun SubjectDialog(subject: SubjectEntity, onSave: (SubjectEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(subject.name) }
    var difficulty by remember { mutableFloatStateOf(subject.difficulty.toFloat()) }
    var advanced by remember { mutableStateOf(subject.isAdvancedCourse) }
    DialogFrame(
        title = subject.code,
        canSave = name.isNotBlank(),
        onDismiss = onDismiss,
        onDelete = null,
        onSave = { onSave(subject.copy(name = name.trim(), difficulty = difficulty.toInt(), isAdvancedCourse = advanced)) },
    ) {
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Wie schwer fällt dir das Fach? ${difficulty.toInt()}/5")
        Slider(value = difficulty, onValueChange = { difficulty = it }, valueRange = 1f..5f, steps = 3)
        Text(
            "Schwere Fächer bekommen im Lernplan automatisch mehr Zeit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Leistungskurs", modifier = Modifier.weight(1f))
            Switch(checked = advanced, onCheckedChange = { advanced = it })
        }
    }
}
