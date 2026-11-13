package app.lifedashboard.ui.learn

import app.lifedashboard.ui.components.wideItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.data.db.ExamEntity
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.TopicEntity
import app.lifedashboard.data.repository.ExamWithTopics
import app.lifedashboard.data.repository.StudyOverview
import app.lifedashboard.planner.Grades
import app.lifedashboard.ui.components.readable
import app.lifedashboard.planner.ReviewRating
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.editors.AddFab
import java.time.LocalDate

private val ExamGradient = listOf(Color(0xFFE53935), Color(0xFFF26B1D), Color(0xFFF2A21D))
private const val TARGET_GRADE = 1.7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(viewModel: LearnViewModel = hiltViewModel()) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var examDialog by remember { mutableStateOf<ExamDialogRequest?>(null) }
    var gradeDialog by remember { mutableStateOf<GradeDialogRequest?>(null) }
    var subjectToEdit by remember { mutableStateOf<app.lifedashboard.data.db.SubjectEntity?>(null) }

    OneUiScaffold(
        title = "Lernen",
        floatingActionButton = {
            when (tab) {
                0 -> AddFab { examDialog = ExamDialogRequest(null) }
                1 -> AddFab { gradeDialog = GradeDialogRequest(null, null) }
            }
        },
    ) { padding ->
        val data = overview
        if (data == null) {
            LoadingState()
            return@OneUiScaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                wideItem {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Klausuren", "Noten", "Fokus").forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = tab == index,
                                onClick = { tab = index },
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                            ) { Text(label) }
                        }
                    }
                }
                when (tab) {
                    0 -> examsTab(data, viewModel, onEdit = { examDialog = ExamDialogRequest(it) })
                    1 -> gradesTab(data, onAddGrade = { gradeDialog = GradeDialogRequest(null, it) }, onEditGrade = { gradeDialog = GradeDialogRequest(it, it.subjectId) }, onEditSubject = { s -> gradeDialog = null; subjectToEdit = s })
                    else -> item { FocusTab(data, viewModel) }
                }
            }
        }
    }

    examDialog?.let { request ->
        ExamDialog(
            original = request.exam,
            subjects = overview?.subjects.orEmpty(),
            onSave = { exam, topics -> viewModel.saveExam(exam, topics); examDialog = null },
            onDelete = { id -> viewModel.deleteExam(id); examDialog = null },
            onDismiss = { examDialog = null },
        )
    }
    gradeDialog?.let { request ->
        GradeDialog(
            original = request.grade,
            presetSubjectId = request.subjectId,
            subjects = overview?.subjects.orEmpty(),
            onSave = { viewModel.saveGrade(it); gradeDialog = null },
            onDelete = { viewModel.deleteGrade(it); gradeDialog = null },
            onDismiss = { gradeDialog = null },
        )
    }
    subjectToEdit?.let { subject ->
        SubjectDialog(
            subject = subject,
            onSave = { viewModel.saveSubject(it); subjectToEdit = null },
            onDismiss = { subjectToEdit = null },
        )
    }
}


data class ExamDialogRequest(val exam: ExamEntity?)
data class GradeDialogRequest(val grade: app.lifedashboard.data.db.GradeEntity?, val subjectId: Long?)

// ======================================================================== Klausuren

private fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.examsTab(
    data: StudyOverview,
    viewModel: LearnViewModel,
    onEdit: (ExamEntity) -> Unit,
) {
    val next = data.upcoming.firstOrNull()
    wideItem {
        if (next == null) {
            SectionCard {
                TileHeader(Icons.Rounded.Event, "Keine Klausur eingetragen", LifeArea.SCHOOL.color)
                Text(
                    "Trag deine Klausuren mit „Neu“ ein – die App verteilt die Lernzeit dann automatisch rückwärts bis zum Termin und plant Wiederholungen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            NextExamHero(next)
        }
    }
    if (data.dueReviews.isNotEmpty()) {
        item {
            SectionCard {
                TileHeader(Icons.Rounded.Replay, "Heute wiederholen", LifeArea.SCHOOL.color) {
                    Pill("${data.dueReviews.size}", LifeArea.SCHOOL.color)
                }
                Text(
                    "Kurz durchgehen (5–10 Min, z. B. in der Bahn) und dann bewerten:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                data.dueReviews.forEach { (exam, topic) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("${exam.subject?.code ?: ""} · ${topic.title}", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(ReviewRating.HARD to "Schwer", ReviewRating.OK to "Ging", ReviewRating.EASY to "Leicht").forEach { (rating, label) ->
                                OutlinedButton(onClick = { viewModel.review(topic, rating) }) { Text(label) }
                            }
                        }
                    }
                }
            }
        }
    }
    val upcomingPlan = data.plan.filter { !it.date.isBefore(LocalDate.now()) && it.date.isBefore(LocalDate.now().plusDays(7)) }
    if (upcomingPlan.isNotEmpty()) {
        item {
            SectionCard {
                TileHeader(Icons.Rounded.School, "Lernplan nächste 7 Tage", LifeArea.SCHOOL.color)
                upcomingPlan.groupBy { it.date }.forEach { (date, list) ->
                    Text(date.friendly(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    list.forEach {
                        Text("•  ${it.title} · ${it.minutes} Min" + if (it.isReview) " (Wiederholung)" else "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Diese Einheiten landen automatisch als Vorschläge in deinen freien Zeiten (Kalender & Heute) – nachmittags bevorzugt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    items(data.upcoming, key = { it.exam.id }) { exam ->
        ExamCard(exam, data.forecasts[exam.exam.id], viewModel, onEdit)
    }
    val past = data.exams.filter { it.daysLeft() < 0 }
    if (past.isNotEmpty()) {
        wideItem {
            Text(
                "Vergangen: " + past.takeLast(5).joinToString(" · ") { "${it.subject?.code ?: it.exam.title} (${it.date.friendly()})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextExamHero(next: ExamWithTopics) {
    val days = next.daysLeft()
    GradientCard(ExamGradient) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NÄCHSTE KLAUSUR", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.1.sp)
                Text(next.subject?.name ?: next.exam.title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(next.date.friendly() + (next.exam.title.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        days == 0 -> "Heute! Du packst das."
                        days == 1 -> "Morgen – heute nur noch wiederholen und früh schlafen."
                        days <= 7 -> "Noch $days Tage. Jeden Tag ein Stück – keine Ausreden."
                        else -> "Noch $days Tage. Jetzt anfangen = später entspannt."
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ProgressRing(next.progress / 100f, Color(0xFF6FD3A6), size = 84.dp, stroke = 5.dp, trackColor = Color.White.copy(alpha = 0.12f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    Text(if (days == 1) "Tag" else "Tage", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Lernplan einer Klausur auf einen Blick: passt die Zeit, wird es eng oder fehlt etwas?
 * Darunter ein Balken pro Tag bis zur Klausur (Höhe = geplante Minuten).
 */
@Composable
private fun PlanStrip(forecast: app.lifedashboard.planner.StudyForecast, examDate: LocalDate, accent: Color) {
    val tones = app.lifedashboard.ui.theme.LocalAppColors.current
    val today = LocalDate.now()
    fun hours(min: Int) = if (min >= 60) "%d:%02d Std".format(min / 60, min % 60) else "$min Min"
    val (dot, headline, detail) = when (forecast.health) {
        app.lifedashboard.planner.PlanHealth.DONE -> Triple(tones.positive, "Alles gelernt", "Bis zur Klausur nur noch kurz wiederholen.")
        app.lifedashboard.planner.PlanHealth.ON_TRACK -> Triple(
            tones.positive, "Plan passt",
            "${hours(forecast.plannedMinutes)} verteilt" + (forecast.firstDay?.let { " · Start ${it.friendly(today)}" } ?: ""),
        )
        app.lifedashboard.planner.PlanHealth.TIGHT -> Triple(
            tones.warning, "Wird eng",
            "${hours(forecast.plannedMinutes)} in wenigen Tagen. Lieber heute schon anfangen.",
        )
        app.lifedashboard.planner.PlanHealth.SHORT -> Triple(
            MaterialTheme.colorScheme.error, "Zeit fehlt",
            "${hours(forecast.missingMinutes)} passen nicht mehr hinein. Heute anfangen oder Themen kürzen.",
        )
    }
    Column(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(headline, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate).toInt().coerceIn(0, 21)
        if (days >= 2 && forecast.perDay.isNotEmpty()) {
            val byDay = forecast.perDay.toMap()
            val max = (forecast.perDay.maxOf { it.second }).coerceAtLeast(60)
            val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            val bar = accent.readable()
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(34.dp)) {
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (days - 1)) / days
                val radius = androidx.compose.ui.geometry.CornerRadius(minOf(w / 2, 3.dp.toPx()))
                for (i in 0 until days) {
                    val minutes = byDay[today.plusDays(i.toLong())] ?: 0
                    val x = i * (w + gap)
                    drawRoundRect(track, androidx.compose.ui.geometry.Offset(x, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(w, 3.dp.toPx()), radius)
                    if (minutes > 0) {
                        val h = (size.height * minutes / max).coerceAtLeast(5.dp.toPx())
                        drawRoundRect(bar, androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(w, h), radius)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("heute", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Klausur", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExamCard(exam: ExamWithTopics, forecast: app.lifedashboard.planner.StudyForecast?, viewModel: LearnViewModel, onEdit: (ExamEntity) -> Unit) {
    var newTopic by remember(exam.exam.id) { mutableStateOf("") }
    var confirmDelete by remember(exam.exam.id) { mutableStateOf(false) }
    val accent = exam.subject?.let { Color(it.colorArgb) } ?: LifeArea.SCHOOL.color
    SectionCard(onClick = { onEdit(exam.exam) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(accent, CircleShape))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(exam.subject?.name ?: exam.exam.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${exam.date.friendly()} · noch ${exam.daysLeft()} Tage" + (exam.exam.title.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill("${exam.progress} %", accent)
            IconButton(onClick = { confirmDelete = true }) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Delete, "Klausur löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (confirmDelete) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Klausur löschen?") },
                text = { Text("${exam.subject?.name ?: exam.exam.title} am ${exam.date.friendly()} – mit allen Themen und dem Lernplan.") },
                confirmButton = { TextButton(onClick = { viewModel.deleteExam(exam.exam.id); confirmDelete = false }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
            )
        }
        app.lifedashboard.ui.components.Meter(exam.progress / 100f, color = accent, modifier = Modifier.padding(vertical = 10.dp))
        forecast?.let { PlanStrip(it, exam.date, accent) }
        exam.topics.forEach { topic -> TopicRow(topic, accent, viewModel) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = newTopic,
                onValueChange = { newTopic = it },
                placeholder = { Text("Neues Thema …") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                enabled = newTopic.isNotBlank(),
                onClick = {
                    viewModel.addTopic(exam.exam.id, newTopic.trim())
                    newTopic = ""
                },
            ) { Icon(Icons.Rounded.Add, "Thema hinzufügen") }
        }
    }
}

@Composable
private fun TopicRow(topic: TopicEntity, accent: Color, viewModel: LearnViewModel) {
    var value by remember(topic.id, topic.progress) { mutableStateOf(topic.progress.toFloat()) }
    var editing by remember(topic.id) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                topic.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { editing = true },
            )
            Text("${value.toInt()} %", style = MaterialTheme.typography.labelLarge, color = accent)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { viewModel.setProgress(topic, value.toInt()) },
            valueRange = 0f..100f,
            steps = 9,
        )
    }
    if (editing) {
        var title by remember { mutableStateOf(topic.title) }
        var minutes by remember { mutableStateOf((topic.effortMinutes / 15f).coerceIn(1f, 16f)) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Thema bearbeiten") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Thema") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("Geschätzter Aufwand: ${(minutes.toInt() * 15).let { if (it >= 60) "%d Std %02d Min".format(it / 60, it % 60) else "$it Min" }}")
                    Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 1f..16f, steps = 14)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    viewModel.renameTopic(topic, title.trim(), minutes.toInt() * 15)
                    editing = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.deleteTopic(topic.id); editing = false }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = false }) { Text("Abbrechen") }
                }
            },
        )
    }
}

// ======================================================================== Noten

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.gradesTab(
    data: StudyOverview,
    onAddGrade: (Long) -> Unit,
    onEditGrade: (app.lifedashboard.data.db.GradeEntity) -> Unit,
    onEditSubject: (app.lifedashboard.data.db.SubjectEntity) -> Unit,
) {
    wideItem {
        val avg = data.overallAverage
        val target = Grades.pointsForGrade(TARGET_GRADE)
        GradientCard(listOf(Color(0xFF1DB370), Color(0xFF3D7BF7))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ziel: Abi 1,7", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("≈ %.1f Punkte im Schnitt".format(target), color = Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            avg == null -> "Trag deine ersten Noten ein, dann siehst du, wo du stehst."
                            avg >= target -> "Du liegst auf Kurs. Weiter so!"
                            else -> "Noch %.1f Punkte bis zum Ziel. Die schwächsten Fächer bringen am meisten.".format(target - avg)
                        },
                        color = Color.White,
                    )
                }
                ProgressRing(((avg ?: 0.0) / 15.0).toFloat(), Color.White, size = 84.dp, trackColor = Color.White.copy(alpha = 0.25f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(avg?.let { "%.1f".format(it) } ?: "–", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(avg?.let { "≈ Note %.1f".format(Grades.pointsToGrade(it)) } ?: "Punkte", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    wideItem {
        Text(
            "Grobe Orientierung (Formel 17 − 3 × Note), nicht die offizielle Abi-Berechnung. Die Noten der 11. zählen meist noch nicht fürs Abi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(data.grades.sortedBy { it.average ?: 99.0 }, key = { it.subject.id }) { sg ->
        val accent = Color(sg.subject.colorArgb)
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(accent, CircleShape))
                Column(Modifier.weight(1f).padding(start = 10.dp).clickable { onEditSubject(sg.subject) }) {
                    Text(sg.subject.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${sg.subject.code} · Schwierigkeit ${sg.subject.difficulty}/5" + if (sg.subject.isAdvancedCourse) " · LK" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(sg.average?.let { "%.1f P".format(it) } ?: "–", style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onAddGrade(sg.subject.id) }) { Icon(Icons.Rounded.Add, "Note eintragen") }
            }
            if (sg.grades.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    sg.grades.forEach { g ->
                        FilterChip(selected = false, onClick = { onEditGrade(g) }, label = { Text("${g.points} P · ${g.kind.label()}") })
                    }
                }
            }
        }
    }
}

fun app.lifedashboard.data.db.GradeKind.label(): String = when (this) {
    app.lifedashboard.data.db.GradeKind.KLAUSUR -> "Klausur"
    app.lifedashboard.data.db.GradeKind.MUENDLICH -> "mündlich"
    app.lifedashboard.data.db.GradeKind.TEST -> "Test"
    app.lifedashboard.data.db.GradeKind.SONSTIGES -> "sonstige"
}

// ======================================================================== Fokus

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusTab(data: StudyOverview, viewModel: LearnViewModel) {
    val state by viewModel.focus.collectAsStateWithLifecycle()
    val isBreak = state.phase == FocusState.Phase.BREAK
    val color = if (isBreak) LifeArea.SPORT.color else LifeArea.SCHOOL.color
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            TileHeader(Icons.Rounded.Timer, if (isBreak) "Pause – kurz bewegen, Wasser trinken" else "Fokus 25 Minuten", color)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = state.subjectId == null, onClick = { viewModel.selectFocusSubject(null) }, label = { Text("Allgemein") })
                data.subjects.forEach { s ->
                    FilterChip(selected = state.subjectId == s.id, onClick = { viewModel.selectFocusSubject(s.id) }, label = { Text(s.code) })
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = state.remainingSeconds.toFloat() / state.totalSeconds,
                    color = color,
                    size = 220.dp,
                    stroke = 14.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%02d:%02d".format(state.remainingSeconds / 60, state.remainingSeconds % 60),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Runde ${state.completedRounds + if (isBreak) 0 else 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = viewModel::startOrPause) {
                    Icon(if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.running) "Pause" else if (state.phase == FocusState.Phase.IDLE) "Los – nur 25 Minuten" else "Weiter")
                }
                Spacer(Modifier.width(8.dp))
                if (isBreak) {
                    FilledTonalButton(onClick = viewModel::skipBreak) { Text("Pause überspringen") }
                } else {
                    IconButton(onClick = viewModel::reset) { Icon(Icons.Rounded.Refresh, "Zurücksetzen") }
                }
            }
            Text(
                "Tipp: Handy weg, nur dieses eine Thema. Nach 4 Runden gibt es 15 Minuten Pause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        val sessions = data.sessionsThisWeek
        SectionCard {
            TileHeader(Icons.Rounded.EmojiEvents, "Diese Woche gelernt", LifeArea.ETSY.color) {
                Pill("${sessions.sumOf { it.minutes }} Min", LifeArea.ETSY.color)
            }
            if (sessions.isEmpty()) {
                Text("Noch keine Fokus-Runde diese Woche. Starte mit einer – nur 25 Minuten.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val bySubject = sessions.groupBy { it.subjectId }.mapValues { (_, list) -> list.sumOf { it.minutes } }
            val max = bySubject.values.maxOrNull() ?: 1
            bySubject.entries.sortedByDescending { it.value }.forEach { (subjectId, minutes) ->
                val subject = data.subjects.firstOrNull { it.id == subjectId }
                val barColor = subject?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(subject?.code ?: "Allg.", modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
                    Box(
                        Modifier
                            .weight((minutes.toFloat() / max).coerceAtLeast(0.02f))
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(barColor),
                    )
                    if (minutes < max) Spacer(Modifier.weight((max - minutes).toFloat() / max))
                    Text("  $minutes Min", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
