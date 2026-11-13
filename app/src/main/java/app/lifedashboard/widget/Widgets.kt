package app.lifedashboard.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.lifedashboard.MainActivity
import app.lifedashboard.data.db.PlanStatus
import app.lifedashboard.data.planning.PlanningEngine
import app.lifedashboard.data.repository.AgendaRepository
import app.lifedashboard.data.repository.StudyRepository
import app.lifedashboard.ui.today.EntryKind
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun agenda(): AgendaRepository
    fun engine(): PlanningEngine
    fun study(): StudyRepository
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Aktualisiert alle Widgets (wird regelmäßig aus dem Hintergrund aufgerufen). */
suspend fun updateWidgets(context: Context) {
    runCatching { TodayWidget().updateAll(context) }
    runCatching { ExamWidget().updateAll(context) }
}

private data class WidgetLine(val time: String, val title: String)

private data class TodayData(val header: String, val now: String?, val lines: List<WidgetLine>, val homework: Int)

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = runCatching { load(context) }.getOrElse { TodayData("Heute", null, emptyList(), 0) }
        provideContent { GlanceTheme { TodayContent(data) } }
    }

    private suspend fun load(context: Context): TodayData {
        val e = entry(context)
        val today = LocalDate.now()
        val range = e.agenda().observe(today, 2).first()
        val day = range.days.first()
        val plan = e.engine().observeDay(today).first()
        val now = LocalTime.now()
        val entries = day.timeline(plan).filter { it.kind != EntryKind.FREE && it.kind != EntryKind.COMMUTE }
        val current = entries.firstOrNull { now in it.range }
        val upcoming = entries.filter { it.range.start.isAfter(now) }.take(3)
        val hw = range.allTasks.count { !it.done && it.dueEpochDay != null && it.dueEpochDay <= today.toEpochDay() + 1 }
        return TodayData(
            header = "Heute · Woche ${day.weekType.name}",
            now = current?.let { "Jetzt: ${it.title} bis ${it.range.end}" },
            lines = upcoming.map { WidgetLine(it.range.start.toString(), it.title + if (it.plan?.status == PlanStatus.PROPOSED) " (Vorschlag)" else "") },
            homework = hw,
        )
    }
}

@Composable
private fun TodayContent(data: TodayData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(data.header, style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp))
        Spacer(GlanceModifier.height(4.dp))
        data.now?.let { Text(it, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp), maxLines = 1) }
        if (data.lines.isEmpty() && data.now == null) {
            Text("Nichts mehr geplant – genieß den Tag", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
        }
        data.lines.forEach { line ->
            Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(line.time, style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.width(8.dp))
                Text(line.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), maxLines = 1)
            }
        }
        if (data.homework > 0) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                "${data.homework} Aufgabe(n) bis morgen fällig",
                style = TextStyle(color = ColorProvider(Color(0xFFE53935)), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

private data class ExamData(val subject: String?, val days: Int, val progress: Int)

class ExamWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = runCatching {
            val next = entry(context).study().overview().first().upcoming.firstOrNull()
            ExamData(next?.subject?.name ?: next?.exam?.title, next?.daysLeft() ?: 0, next?.progress ?: 0)
        }.getOrElse { ExamData(null, 0, 0) }
        provideContent { GlanceTheme { ExamContent(data) } }
    }
}

@Composable
private fun ExamContent(data: ExamData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(Color(0xFFE53935)))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val white = ColorProvider(Color.White)
        if (data.subject == null) {
            Text("Keine Klausur", style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text("eingetragen", style = TextStyle(color = white, fontSize = 12.sp))
        } else {
            Text(if (data.days == 0) "HEUTE" else "${data.days}", style = TextStyle(color = white, fontSize = 34.sp, fontWeight = FontWeight.Bold))
            Text(if (data.days == 1) "Tag bis" else if (data.days == 0) "" else "Tage bis", style = TextStyle(color = white, fontSize = 12.sp))
            Text(data.subject, style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text("Gelernt: ${data.progress} %", style = TextStyle(color = white, fontSize = 12.sp))
        }
    }
}

class ExamWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExamWidget()
}
