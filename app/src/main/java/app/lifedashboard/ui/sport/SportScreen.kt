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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.minutesToTimeText
import app.lifedashboard.ui.components.shortName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SportViewModel @Inject constructor(routines: RoutineRepository) : ViewModel() {
    val trainings: StateFlow<List<RoutineBlockEntity>> = routines.routines
        .map { list -> list.filter { it.area == LifeArea.SPORT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SportScreen(viewModel: SportViewModel = hiltViewModel()) {
    val trainings by viewModel.trainings.collectAsStateWithLifecycle()
    OneUiScaffold(title = "Sport") { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Wöchentliches Training",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                items(trainings, key = { it.id }) { training ->
                    SectionCard {
                        Text(training.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            training.days().sorted().joinToString(" & ") { it.shortName() } +
                                " · ${minutesToTimeText(training.startMinute)}–${minutesToTimeText(training.endMinute)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        listOfNotNull(training.location, training.note).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    SectionCard(title = "Kommt in Phase 5") {
                        Column {
                            listOf(
                                "Training bearbeiten (neue Floorball-Zeiten)",
                                "Workout-Log mit Übungen, Sätzen und Gewicht",
                                "Fortschritts-Diagramme",
                                "Schritte & Schlaf über Health Connect (Samsung Health)",
                                "Ironman-Weg: 5 km → Sprint → Olympisch → Mitteldistanz → Ironman",
                            ).forEach {
                                Text("•  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
