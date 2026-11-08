package app.lifedashboard.ui.editors

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.data.db.EventEntity
import app.lifedashboard.data.db.JobShiftEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.db.SubjectEntity
import app.lifedashboard.data.db.TaskEntity
import app.lifedashboard.data.db.TaskKind
import app.lifedashboard.planner.minuteToTime
import app.lifedashboard.data.repository.euro
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.hhmm
import app.lifedashboard.ui.components.pickDate
import app.lifedashboard.ui.components.pickTime
import app.lifedashboard.ui.components.shortName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Zeigt den passenden Dialog für [request]. Eine Stelle für alle Bearbeiten-Dialoge. */
@Composable
fun EditorHost(request: EditRequest?, onDismiss: () -> Unit, viewModel: EditorViewModel = hiltViewModel()) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    when (request) {
        null -> Unit
        is EditRequest.Event -> EventDialog(request, onDismiss, viewModel)
        is EditRequest.Homework -> HomeworkDialog(request, subjects, onDismiss, viewModel)
        is EditRequest.Todo -> TodoDialog(request, onDismiss, viewModel)
        is EditRequest.Shift -> ShiftDialog(request, onDismiss, viewModel)
        is EditRequest.Routine -> RoutineDialog(request, onDismiss, viewModel)
        is EditRequest.Exam -> app.lifedashboard.ui.learn.ExamDialog(
            original = request.exam.takeIf { it.id != 0L },
            subjects = subjects,
            onSave = { exam, topics -> viewModel.saveExam(exam, topics); onDismiss() },
            onDelete = { id -> viewModel.deleteExam(id); onDismiss() },
            onDismiss = onDismiss,
            preset = request.exam,
        )
        is EditRequest.Expense -> ExpenseDialog(request, onDismiss, viewModel)
    }
}

@Composable
private fun EditorDialog(
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text("Speichern") } },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

// ---------------------------------------------------------------- Termin

@Composable
private fun EventDialog(request: EditRequest.Event, onDismiss: () -> Unit, vm: EditorViewModel) {
    val context = LocalContext.current
    val original = request.event
    var title by remember { mutableStateOf(original?.title ?: "") }
    var area by remember { mutableStateOf(original?.area ?: LifeArea.PRIVATE) }
    var date by remember { mutableStateOf(original?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: request.date) }
    var allDay by remember { mutableStateOf(original != null && original.startMinute == null) }
    var start by remember { mutableStateOf(original?.startMinute?.let(::minuteToTime) ?: LocalTime.of(16, 0)) }
    var end by remember { mutableStateOf(original?.endMinute?.let(::minuteToTime) ?: LocalTime.of(17, 0)) }
    var location by remember { mutableStateOf(original?.location ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }

    EditorDialog(
        title = if (original == null || original.id == 0L) "Neuer Termin" else "Termin bearbeiten",
        canSave = title.isNotBlank(),
        onDismiss = onDismiss,
        onDelete = original?.takeIf { it.id != 0L }?.let { { vm.deleteEvent(it.id); onDismiss() } },
        onSave = {
            vm.saveEvent(
                EventEntity(
                    id = original?.id ?: 0,
                    title = title.trim(),
                    dateEpochDay = date.toEpochDay(),
                    startMinute = if (allDay) null else start.hour * 60 + start.minute,
                    endMinute = if (allDay) null else maxOf(end.hour * 60 + end.minute, start.hour * 60 + start.minute),
                    area = area,
                    location = location.trim().ifBlank { null },
                    note = note.trim().ifBlank { null },
                ),
            )
            onDismiss()
        },
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("Was?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Bereich")
        AreaChips(area, { area = it })
        ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ganztägig", modifier = Modifier.weight(1f))
            Switch(checked = allDay, onCheckedChange = { allDay = it })
        }
        if (!allDay) {
            ButtonRow {
                ValueButton("Von", start.hhmm(), {
                    context.pickTime(start) {
                        val length = java.time.Duration.between(start, end)
                        start = it
                        end = it.plus(if (length.isNegative) java.time.Duration.ofHours(1) else length)
                    }
                }, Modifier.weight(1f))
                ValueButton("Bis", end.hhmm(), { context.pickTime(end) { end = it } }, Modifier.weight(1f))
            }
        }
        OutlinedTextField(location, { location = it }, label = { Text("Ort (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- Hausaufgabe

private val durationOptions = listOf(15 to "15 Min", 30 to "30 Min", 45 to "45 Min", 60 to "1 Std", 90 to "1,5 Std")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeworkDialog(
    request: EditRequest.Homework,
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    vm: EditorViewModel,
) {
    val context = LocalContext.current
    val original = request.task
    var subjectId by remember { mutableStateOf(original?.subjectId) }
    var title by remember { mutableStateOf(original?.title ?: "") }
    var minutes by remember { mutableIntStateOf(original?.estimatedMinutes ?: 30) }
    // 0 = nächste Stunde, 1 = übernächste, -1 = eigenes Datum
    var dueMode by remember { mutableIntStateOf(if (original == null || (original.id == 0L && original.dueEpochDay == null)) 0 else -1) }
    var customDate by remember { mutableStateOf(original?.dueEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().plusDays(1)) }
    var nextLesson by remember { mutableStateOf<LocalDate?>(null) }
    var secondLesson by remember { mutableStateOf<LocalDate?>(null) }

    var estimated by remember { mutableStateOf(false) }
    LaunchedEffect(subjectId) {
        val id = subjectId
        nextLesson = id?.let { vm.nextLessonDate(it, 0) }
        secondLesson = id?.let { vm.nextLessonDate(it, 1) }
        // Dauer automatisch aus deinen bisherigen Hausaufgaben im Fach schätzen
        if (id != null && (original == null || original.id == 0L)) {
            minutes = vm.estimateMinutes(id)
            estimated = true
        }
    }
    val due: LocalDate? = when (dueMode) {
        0 -> nextLesson
        1 -> secondLesson
        else -> customDate
    }

    EditorDialog(
        title = if (original == null || original.id == 0L) "Neue Hausaufgabe" else "Hausaufgabe bearbeiten",
        canSave = subjectId != null && title.isNotBlank(),
        onDismiss = onDismiss,
        onDelete = original?.takeIf { it.id != 0L }?.let { { vm.deleteTask(it.id); onDismiss() } },
        onSave = {
            vm.saveTask(
                TaskEntity(
                    id = original?.id ?: 0,
                    title = title.trim(),
                    area = LifeArea.SCHOOL,
                    kind = TaskKind.HOMEWORK,
                    subjectId = subjectId,
                    dueEpochDay = (due ?: customDate).toEpochDay(),
                    estimatedMinutes = minutes,
                    done = original?.done ?: false,
                    doneAtMillis = original?.doneAtMillis,
                    note = original?.note,
                    createdAtMillis = original?.createdAtMillis ?: System.currentTimeMillis(),
                ),
            )
            onDismiss()
        },
    ) {
        FieldLabel("Fach")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { subject ->
                FilterChip(
                    selected = subjectId == subject.id,
                    onClick = { subjectId = subject.id },
                    label = { Text(subject.code) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(Color(subject.colorArgb), CircleShape)) },
                )
            }
        }
        OutlinedTextField(title, { title = it }, label = { Text("Was ist auf?") }, modifier = Modifier.fillMaxWidth())
        FieldLabel("Fällig")
        ChoiceChips(
            options = listOf(
                0 to ("Nächste Stunde" + (nextLesson?.let { " (${it.friendly()})" } ?: "")),
                1 to ("Übernächste" + (secondLesson?.let { " (${it.friendly()})" } ?: "")),
                -1 to "Datum wählen",
            ),
            selected = dueMode,
            onSelect = {
                dueMode = it
                if (it == -1) context.pickDate(customDate) { picked -> customDate = picked }
            },
        )
        if (dueMode == -1) {
            ValueButton("Datum", customDate.friendly(), { context.pickDate(customDate) { customDate = it } }, Modifier.fillMaxWidth())
        } else if (subjectId != null && due == null) {
            Text("Keine Stunde in den nächsten Wochen gefunden – bitte Datum wählen.", color = MaterialTheme.colorScheme.error)
        }
        FieldLabel("Wie lange ungefähr?" + if (estimated) " (geschätzt aus deinen bisherigen Hausaufgaben)" else "")
        ChoiceChips(durationOptions, minutes) { minutes = it; estimated = false }
    }
}

// ---------------------------------------------------------------- To-do

@Composable
private fun TodoDialog(request: EditRequest.Todo, onDismiss: () -> Unit, vm: EditorViewModel) {
    val context = LocalContext.current
    val original = request.task
    var title by remember { mutableStateOf(original?.title ?: "") }
    var area by remember { mutableStateOf(original?.area ?: LifeArea.PRIVATE) }
    var hasDue by remember { mutableStateOf(original?.dueEpochDay != null || request.date != null) }
    var due by remember { mutableStateOf(original?.dueEpochDay?.let(LocalDate::ofEpochDay) ?: request.date ?: LocalDate.now()) }
    var minutes by remember { mutableIntStateOf(original?.estimatedMinutes ?: 30) }
    var note by remember { mutableStateOf(original?.note ?: "") }

    EditorDialog(
        title = if (original == null || original.id == 0L) "Neue Aufgabe" else "Aufgabe bearbeiten",
        canSave = title.isNotBlank(),
        onDismiss = onDismiss,
        onDelete = original?.takeIf { it.id != 0L }?.let { { vm.deleteTask(it.id); onDismiss() } },
        onSave = {
            vm.saveTask(
                TaskEntity(
                    id = original?.id ?: 0,
                    title = title.trim(),
                    area = area,
                    kind = original?.kind ?: TaskKind.TODO,
                    subjectId = original?.subjectId,
                    dueEpochDay = if (hasDue) due.toEpochDay() else null,
                    estimatedMinutes = minutes,
                    done = original?.done ?: false,
                    doneAtMillis = original?.doneAtMillis,
                    note = note.trim().ifBlank { null },
                    createdAtMillis = original?.createdAtMillis ?: System.currentTimeMillis(),
                ),
            )
            onDismiss()
        },
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("Was ist zu tun?") }, modifier = Modifier.fillMaxWidth())
        FieldLabel("Bereich")
        AreaChips(area, { area = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fälligkeitsdatum", modifier = Modifier.weight(1f))
            Switch(checked = hasDue, onCheckedChange = { hasDue = it })
        }
        if (hasDue) {
            ValueButton("Fällig", due.friendly(), { context.pickDate(due) { due = it } }, Modifier.fillMaxWidth())
        }
        FieldLabel("Wie lange ungefähr?")
        ChoiceChips(durationOptions, minutes) { minutes = it }
        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- Job-Schicht

@Composable
private fun ShiftDialog(request: EditRequest.Shift, onDismiss: () -> Unit, vm: EditorViewModel) {
    val context = LocalContext.current
    val original = request.shift
    var date by remember { mutableStateOf(original?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: request.date) }
    var start by remember { mutableStateOf(original?.startMinute?.let(::minuteToTime) ?: LocalTime.of(15, 30)) }
    var end by remember { mutableStateOf(original?.endMinute?.let(::minuteToTime) ?: LocalTime.of(18, 30)) }
    var note by remember { mutableStateOf(original?.note ?: "") }

    EditorDialog(
        title = if (original == null || original.id == 0L) "Neue Job-Schicht" else "Schicht bearbeiten",
        canSave = end.isAfter(start),
        onDismiss = onDismiss,
        onDelete = original?.takeIf { it.id != 0L }?.let { { vm.deleteShift(it.id); onDismiss() } },
        onSave = {
            vm.saveShift(
                JobShiftEntity(
                    id = original?.id ?: 0,
                    dateEpochDay = date.toEpochDay(),
                    startMinute = start.hour * 60 + start.minute,
                    endMinute = end.hour * 60 + end.minute,
                    note = note.trim().ifBlank { null },
                    hourlyWageCents = original?.hourlyWageCents,
                ),
            )
            onDismiss()
        },
    ) {
        ValueButton("Datum", date.friendly(), { context.pickDate(date) { date = it } }, Modifier.fillMaxWidth())
        ButtonRow {
            ValueButton("Von", start.hhmm(), { context.pickTime(start) { start = it } }, Modifier.weight(1f))
            ValueButton("Bis", end.hhmm(), { context.pickTime(end) { end = it } }, Modifier.weight(1f))
        }
        if (!end.isAfter(start)) Text("Ende muss nach dem Start liegen.", color = MaterialTheme.colorScheme.error)
        OutlinedTextField(note, { note = it }, label = { Text("Notiz, z. B. Baustelle (optional)") }, modifier = Modifier.fillMaxWidth())
        Text(
            "Tipp: An Job-Tagen plant die App später kein hartes Zusatztraining ein.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- Training / feste Blöcke

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineDialog(request: EditRequest.Routine, onDismiss: () -> Unit, vm: EditorViewModel) {
    val context = LocalContext.current
    val original = request.routine
    var title by remember { mutableStateOf(original?.title ?: "") }
    var area by remember { mutableStateOf(original?.area ?: LifeArea.SPORT) }
    var days by remember { mutableStateOf(original?.days() ?: emptySet()) }
    var start by remember { mutableStateOf(original?.startMinute?.let(::minuteToTime) ?: LocalTime.of(18, 0)) }
    var end by remember { mutableStateOf(original?.endMinute?.let(::minuteToTime) ?: LocalTime.of(19, 30)) }
    var location by remember { mutableStateOf(original?.location ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var active by remember { mutableStateOf(original?.active ?: true) }

    EditorDialog(
        title = if (original == null) "Neuer fester Termin" else "${original.title} bearbeiten",
        canSave = title.isNotBlank() && days.isNotEmpty() && end.isAfter(start),
        onDismiss = onDismiss,
        onDelete = null,
        onSave = {
            vm.saveRoutine(
                RoutineBlockEntity(
                    id = original?.id ?: 0,
                    title = title.trim(),
                    area = area,
                    daysOfWeek = days.sorted().joinToString(",") { it.value.toString() },
                    startMinute = start.hour * 60 + start.minute,
                    endMinute = end.hour * 60 + end.minute,
                    location = location.trim().ifBlank { null },
                    note = note.trim().ifBlank { null },
                    active = active,
                ),
            )
            onDismiss()
        },
    ) {
        OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Bereich")
        AreaChips(area, { area = it })
        FieldLabel("Wochentage")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in days,
                    onClick = { days = if (day in days) days - day else days + day },
                    label = { Text(day.shortName()) },
                )
            }
        }
        ButtonRow {
            ValueButton("Von", start.hhmm(), { context.pickTime(start) { start = it } }, Modifier.weight(1f))
            ValueButton("Bis", end.hhmm(), { context.pickTime(end) { end = it } }, Modifier.weight(1f))
        }
        Text(
            "Die Zeit inkl. Hin- und Rückweg eintragen – so plant die App nichts in die Fahrt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(location, { location = it }, label = { Text("Ort (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aktiv", modifier = Modifier.weight(1f))
            Switch(checked = active, onCheckedChange = { active = it })
        }
    }
}

// ---------------------------------------------------------------- Ausgabe (aus Schnelleingabe)

@Composable
private fun ExpenseDialog(request: EditRequest.Expense, onDismiss: () -> Unit, vm: EditorViewModel) {
    val tx = request.tx
    var what by remember { mutableStateOf(tx.counterparty) }
    var category by remember { mutableStateOf(tx.category) }
    EditorDialog(
        title = "Ausgabe: " + (-tx.amountCents).euro(),
        canSave = what.isNotBlank(),
        onDismiss = onDismiss,
        onDelete = null,
        onSave = {
            vm.saveExpense(tx.copy(counterparty = what.trim(), category = category))
            onDismiss()
        },
    ) {
        OutlinedTextField(what, { what = it }, label = { Text("Wofür?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FieldLabel("Kategorie" + if (category != null) "" else " (bitte wählen)")
        ChoiceChips(app.lifedashboard.data.repository.DEFAULT_CATEGORIES.take(12).map { it to it }, category) { category = it }
        Text("Wird vom Bargeld abgezogen – in Finanzen änderbar.", style = MaterialTheme.typography.bodySmall)
    }
}
