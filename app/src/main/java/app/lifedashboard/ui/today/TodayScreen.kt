package app.lifedashboard.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.hhmm
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.pretty

@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OneUiScaffold(title = "Heute") { padding ->
        when (val s = state) {
            TodayUiState.Loading -> LoadingState()
            is TodayUiState.Ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                    contentPadding = padding.listPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Header(s) }
                    item { NowCard(s) }
                    item { TimelineCard(s) }
                    item { ComingSoonCard() }
                }
            }
        }
    }
}

@Composable
private fun Header(s: TodayUiState.Ready) {
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text(s.dateLabel, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Woche ${s.weekType.name}" + if (s.isSchoolDay) " · Schultag" else " · kein Unterricht",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NowCard(s: TodayUiState.Ready) {
    SectionCard {
        val current = s.current
        if (current != null && current.kind != EntryKind.FREE) {
            Label("Jetzt")
            Text(current.title, style = MaterialTheme.typography.titleLarge)
            Text("bis ${current.range.end.hhmm()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Label("Gerade frei")
            Text(
                if (s.freeTimeLeft.isZero) "Für heute ist nichts mehr frei" else "Noch ${s.freeTimeLeft.pretty()} freie Zeit heute",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        val next = s.next
        if (next != null) {
            Text(
                "Als Nächstes: ${next.title} um ${next.range.start.hhmm()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TimelineCard(s: TodayUiState.Ready) {
    SectionCard(title = "Tagesablauf") {
        if (s.entries.isEmpty()) {
            Text("Keine Einträge", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val time = s.now.toLocalTime()
        s.entries.forEach { entry ->
            TimelineRow(entry, isNow = time in entry.range, isPast = !entry.range.end.isAfter(time))
        }
    }
}

@Composable
private fun TimelineRow(entry: TimelineEntry, isNow: Boolean, isPast: Boolean) {
    val accent = when (entry.kind) {
        EntryKind.LESSON -> entry.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
        EntryKind.ROUTINE -> MaterialTheme.colorScheme.tertiary
        EntryKind.COMMUTE -> MaterialTheme.colorScheme.outline
        EntryKind.FREE -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentAlpha = if (isPast) 0.5f else 1f
    val rowBackground = if (isNow) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackground)
            .height(IntrinsicSize.Min)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp)) {
            Text(entry.range.start.hhmm(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
            Text(entry.range.end.hhmm(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
        }
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = contentAlpha)),
        )
        Column(Modifier.weight(1f)) {
            if (entry.kind == EntryKind.FREE) {
                Text(
                    "Frei · ${entry.range.duration.pretty()}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            } else {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
                entry.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
                }
            }
        }
    }
}

@Composable
private fun ComingSoonCard() {
    SectionCard(title = "Bald hier") {
        Text(
            "Nächste Klausur mit Countdown, Lernfortschritt, Hausaufgaben und Vorschläge für deine freie Zeit kommen in den nächsten Phasen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssistChip(
            onClick = {},
            label = { Text("Phase 2–4") },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
