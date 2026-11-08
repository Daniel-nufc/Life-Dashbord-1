package app.lifedashboard.ui.sport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.db.RoutineBlockEntity
import app.lifedashboard.data.repository.RoutineRepository
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.Pill
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.icon
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.minutesToTimeText
import app.lifedashboard.ui.components.shortName
import app.lifedashboard.ui.editors.AddFab
import app.lifedashboard.ui.editors.EditRequest
import app.lifedashboard.ui.editors.EditorHost
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SportViewModel @Inject constructor(routines: RoutineRepository) : ViewModel() {
    val routines: StateFlow<List<RoutineBlockEntity>> = routines.routines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SportScreen(onBack: () -> Unit, viewModel: SportViewModel = hiltViewModel()) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditRequest?>(null) }

    OneUiScaffold(
        title = "Training",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück") }
        },
        floatingActionButton = { AddFab { editing = EditRequest.Routine(null) } },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Wöchentlich wiederkehrend – blockiert die Zeit im Tagesplan. Antippen zum Ändern (z. B. neue Floorball-Zeiten).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                items(routines, key = { it.id }) { routine ->
                    SectionCard(onClick = { editing = EditRequest.Routine(routine) }) {
                        TileHeader(routine.area.icon, routine.title, routine.area.color) {
                            if (!routine.active) Pill("pausiert", MaterialTheme.colorScheme.outline)
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            routine.days().sorted().joinToString(" & ") { it.shortName() } +
                                " · ${minutesToTimeText(routine.startMinute)}–${minutesToTimeText(routine.endMinute)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        listOfNotNull(routine.location, routine.note).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.DirectionsRun, "Ironman-Weg · nächstes Ziel: 10 km", LifeArea.SPORT.color)
                        Column {
                            listOf(
                                "✅ 5 km laufen – geschafft",
                                "➡️ 10 km laufen",
                                "Sprint-Triathlon (0,75 / 20 / 5)",
                                "Olympisch (1,5 / 40 / 10)",
                                "Mitteldistanz (1,9 / 90 / 21,1)",
                                "Ironman (3,8 / 180 / 42,2)",
                            ).forEach {
                                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Text(
                                "Workout-Log, Diagramme, Health Connect und Habits kommen in Phase 5.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    EditorHost(editing, onDismiss = { editing = null })
}
