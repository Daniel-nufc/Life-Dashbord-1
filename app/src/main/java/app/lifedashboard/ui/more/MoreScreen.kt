package app.lifedashboard.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.BuildConfig
import app.lifedashboard.data.settings.AppSettings
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.SectionTitle
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.hhmm
import app.lifedashboard.ui.components.listPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    fun setThisWeek(type: WeekType) {
        viewModelScope.launch { settingsRepository.setWeekType(LocalDate.now(), type) }
    }

    fun changeCommute(delta: Int) {
        val current = settings.value?.schoolCommuteMinutes ?: return
        viewModelScope.launch { settingsRepository.setSchoolCommuteMinutes(current + delta) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(viewModel: MoreViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    OneUiScaffold(title = "Mehr") { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = padding.listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SectionTitle("Bereiche") }
                item {
                    SectionCard {
                        AreaItem(Icons.Rounded.Flag, "Ziele & Gewohnheiten", "kommt in Phase 5")
                        AreaItem(Icons.Rounded.AttachMoney, "Finanzen", "kommt in Phase 6")
                        AreaItem(Icons.Rounded.Storefront, "Etsy-Shop", "kommt in Phase 7")
                    }
                }

                val s = settings ?: return@LazyColumn
                item { SectionTitle("Einstellungen") }
                item {
                    SectionCard {
                        ListItem(
                            headlineContent = { Text("App-Sperre") },
                            supportingContent = { Text("Fingerabdruck beim Öffnen") },
                            trailingContent = { Switch(checked = s.appLockEnabled, onCheckedChange = { viewModel.setAppLock(it) }) },
                            colors = transparentItem(),
                        )
                        ListItem(
                            headlineContent = { Text("Diese Woche ist") },
                            supportingContent = { Text("Falls A/B einmal nicht stimmt (z. B. nach den Ferien)") },
                            colors = transparentItem(),
                        )
                        val thisWeek = s.weekCalculator().weekTypeFor(LocalDate.now())
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            WeekType.entries.forEachIndexed { index, week ->
                                SegmentedButton(
                                    selected = thisWeek == week,
                                    onClick = { viewModel.setThisWeek(week) },
                                    shape = SegmentedButtonDefaults.itemShape(index, WeekType.entries.size),
                                ) { Text("Woche ${week.name}") }
                            }
                        }
                        ListItem(
                            headlineContent = { Text("Schulweg") },
                            supportingContent = { Text("${s.schoolCommuteMinutes} Minuten pro Richtung") },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.changeCommute(-5) }) { Icon(Icons.Rounded.Remove, "weniger") }
                                    IconButton(onClick = { viewModel.changeCommute(5) }) { Icon(Icons.Rounded.Add, "mehr") }
                                }
                            },
                            colors = transparentItem(),
                        )
                        ListItem(
                            headlineContent = { Text("Tagesfenster") },
                            supportingContent = { Text("${s.dayStart.hhmm()}–${s.dayEnd.hhmm()} (aufstehen bis schlafen)") },
                            colors = transparentItem(),
                        )
                    }
                }
                item { SectionTitle("Info") }
                item {
                    SectionCard {
                        Text("Life Dashboard ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Alle Daten bleiben verschlüsselt auf diesem Gerät.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun AreaItem(icon: ImageVector, title: String, subtitle: String) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = transparentItem(),
    )
}
