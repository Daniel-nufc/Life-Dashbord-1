package app.lifedashboard.ui.editors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.ui.components.IconBadge
import app.lifedashboard.ui.components.color
import java.time.LocalDate

@Composable
fun AddFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text("Neu") },
    )
}

/** „Was möchtest du eintragen?“ – unten am Bildschirm, gut mit dem Daumen erreichbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(date: LocalDate, onPick: (EditRequest) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Was möchtest du eintragen?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            // Schnell-Eingabe in einem Satz, z. B. „Mi 17 Uhr Zahnarzt“
            QuickInputBar(onRequest = onPick, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            SheetItem(Icons.Rounded.MenuBook, LifeArea.SCHOOL.color, "Hausaufgabe", "Fällig automatisch zur nächsten Stunde") {
                onPick(EditRequest.Homework(null))
            }
            SheetItem(Icons.Rounded.Event, LifeArea.PRIVATE.color, "Termin", "Arzt, Treffen, Geburtstag …") {
                onPick(EditRequest.Event(null, date))
            }
            SheetItem(Icons.Rounded.TaskAlt, LifeArea.ETSY.color, "Aufgabe", "To-do für Etsy, Privat, Sport …") {
                onPick(EditRequest.Todo(null, null))
            }
            SheetItem(Icons.Rounded.Construction, LifeArea.JOB.color, "Job-Schicht", "Baustelle – wird im Plan freigehalten") {
                onPick(EditRequest.Shift(null, date))
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        leadingContent = { IconBadge(icon, color, size = 44.dp) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
