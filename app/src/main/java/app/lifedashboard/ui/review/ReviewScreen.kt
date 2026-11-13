package app.lifedashboard.ui.review

import app.lifedashboard.ui.components.wideItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.repository.ReviewRepository
import app.lifedashboard.data.repository.WeekReview
import app.lifedashboard.data.repository.euro
import app.lifedashboard.ui.components.GradientCard
import app.lifedashboard.ui.components.LoadingState
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.TileHeader
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.friendly
import app.lifedashboard.ui.components.listPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ReviewViewModel @Inject constructor(private val repo: ReviewRepository) : ViewModel() {
    private val _review = MutableStateFlow<WeekReview?>(null)
    val review: StateFlow<WeekReview?> = _review.asStateFlow()

    init {
        viewModelScope.launch { _review.value = runCatching { repo.week() }.getOrNull() }
    }

    fun adopt(text: String) {
        viewModelScope.launch { repo.adopt(text) }
    }
}

@Composable
fun ReviewScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val review by viewModel.review.collectAsStateWithLifecycle()
    val adopted = remember { mutableStateListOf<String>() }
    OneUiScaffold(
        title = "Wochenrückblick",
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
    ) { padding ->
        val r = review
        if (r == null) {
            LoadingState()
            return@OneUiScaffold
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            app.lifedashboard.ui.components.AdaptiveList(padding) {
                wideItem {
                    GradientCard(listOf(Color(0xFF7B5CF0), Color(0xFF3D7BF7))) {
                        Text("Woche ab ${r.weekStart.friendly()}", color = Color.White.copy(alpha = 0.85f))
                        Text("${r.done} von ${r.planned} Blöcken erledigt", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        listOf(
                            "${r.studyMinutes} Min Fokus · ${r.homeworkDone} Hausaufgaben",
                            "${r.workouts} Trainings · ${r.workoutMinutes} Min",
                            "Gewohnheiten ${r.habitRate} %",
                            "Ausgaben ${r.spentCents.euro()} · Etsy ${r.etsyRevenueCents.euro()}",
                        ).forEach { Text(it, color = Color.White) }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.ThumbUp, "Was lief gut", LifeArea.SPORT.color)
                        r.good.forEach { Text("✓  $it", modifier = Modifier.padding(vertical = 2.dp)) }
                    }
                }
                if (r.bad.isNotEmpty()) {
                    item {
                        SectionCard {
                            TileHeader(Icons.Rounded.ThumbDown, "Was nicht so lief", LifeArea.ETSY.color)
                            r.bad.forEach { Text("•  $it", modifier = Modifier.padding(vertical = 2.dp)) }
                            Text("Kein Problem – verpasste Blöcke wurden schon neu eingeplant.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    SectionCard {
                        TileHeader(Icons.Rounded.Lightbulb, "Vorschläge für die neue Woche", LifeArea.SCHOOL.color)
                        r.suggestions.forEach { s ->
                            Text(s, modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(enabled = s !in adopted, onClick = { viewModel.adopt(s); adopted += s }) {
                                    Text(if (s in adopted) "✓ Übernommen" else "Als Aufgabe übernehmen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
