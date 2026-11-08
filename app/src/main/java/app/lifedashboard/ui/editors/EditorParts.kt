package app.lifedashboard.ui.editors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.icon
import app.lifedashboard.ui.components.label

@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

/** Auswahl des Lebensbereichs als bunte Chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AreaChips(selected: LifeArea, onSelect: (LifeArea) -> Unit, areas: List<LifeArea> = LifeArea.entries) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        areas.forEach { area ->
            FilterChip(
                selected = area == selected,
                onClick = { onSelect(area) },
                label = { Text(area.label) },
                leadingIcon = { Icon(area.icon, contentDescription = null, tint = area.color, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = area.color.copy(alpha = 0.22f)),
            )
        }
    }
}

/** Eine Reihe einfacher Auswahl-Chips (z. B. 15/30/45 Minuten). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

/** Knopf, der den aktuellen Wert zeigt und beim Antippen eine Auswahl öffnet. */
@Composable
fun ValueButton(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value)
        }
    }
}

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
