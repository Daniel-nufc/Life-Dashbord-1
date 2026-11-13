package app.lifedashboard.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SportsHockey
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.lifedashboard.BuildConfig
import app.lifedashboard.data.db.LifeArea
import app.lifedashboard.data.settings.AppSettings
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.WeekType
import app.lifedashboard.ui.components.IconBadge
import app.lifedashboard.ui.components.OneUiScaffold
import app.lifedashboard.ui.components.SectionCard
import app.lifedashboard.ui.components.SectionTitle
import app.lifedashboard.ui.components.color
import app.lifedashboard.ui.components.hhmm
import app.lifedashboard.ui.components.listPadding
import app.lifedashboard.ui.components.pickTime
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
    schoolCalendar: app.lifedashboard.data.repository.SchoolCalendarRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val freeRanges: StateFlow<List<app.lifedashboard.planner.DateRange>> = schoolCalendar.ranges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    fun setStrict(strict: Boolean) {
        viewModelScope.launch { settingsRepository.setStrictCoach(strict) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setThisWeek(type: WeekType) {
        viewModelScope.launch { settingsRepository.setWeekType(LocalDate.now(), type) }
    }

    fun changeCommute(delta: Int) {
        val current = settings.value?.schoolCommuteMinutes ?: return
        viewModelScope.launch { settingsRepository.setSchoolCommuteMinutes(current + delta) }
    }

    fun setName(name: String) {
        viewModelScope.launch { settingsRepository.setUserName(name) }
    }

    fun movePriorityUp(area: String) {
        val order = settings.value?.priorityOrder?.toMutableList() ?: return
        val i = order.indexOf(area)
        if (i > 0) {
            order[i] = order[i - 1]
            order[i - 1] = area
            viewModelScope.launch { settingsRepository.setPriorityOrder(order) }
        }
    }

    fun toggleCard(key: String) {
        val st = settings.value ?: return
        val hidden = if (key in st.hiddenTodayCards) st.hiddenTodayCards - key else st.hiddenTodayCards + key
        viewModelScope.launch { settingsRepository.setTodayCards(st.todayCards, hidden) }
    }

    fun moveCardUp(key: String) {
        val st = settings.value ?: return
        val order = st.todayCards.toMutableList()
        val i = order.indexOf(key)
        if (i > 0) {
            order[i] = order[i - 1]
            order[i - 1] = key
            viewModelScope.launch { settingsRepository.setTodayCards(order, st.hiddenTodayCards) }
        }
    }

    fun setWeekendStart(minuteOfDay: Int) {
        viewModelScope.launch { settingsRepository.setWeekendDayStart(minuteOfDay) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onOpenSport: () -> Unit, onOpenConnections: () -> Unit, onOpenGoals: () -> Unit, onOpenFinance: () -> Unit, onOpenEtsy: () -> Unit, onOpenReview: () -> Unit, onOpenBackup: () -> Unit, onOpenHolidays: () -> Unit, onOpenSync: () -> Unit = {}, onOpenProgress: () -> Unit = {}, viewModel: MoreViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val freeRanges by viewModel.freeRanges.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }

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
                        AreaItem(Icons.Rounded.EmojiEvents, LifeArea.SPORT.color, "Fortschritt & Abzeichen", "Level, Serie, Wochenziele, Ironman-Weg", onOpenProgress)
                        AreaItem(Icons.Rounded.Sync, LifeArea.SCHOOL.color, "Handy ↔ Tablet", "Automatischer Abgleich über Cloud-Ordner oder WLAN", onOpenSync)
                        AreaItem(Icons.Rounded.Insights, LifeArea.SCHOOL.color, "Wochenrückblick", "Was lief gut, was nicht, Vorschläge", onOpenReview)
                        AreaItem(Icons.Rounded.BeachAccess, LifeArea.SPORT.color, "Ferien & freie Tage", "Berliner Ferien 2026/27, eigene Studientage", onOpenHolidays)
                        AreaItem(Icons.Rounded.CalendarMonth, LifeArea.SCHOOL.color, "Kalender & Abos", "Handy-Kalender, Lernraum, IServ, WebUntis", onOpenConnections)
                        AreaItem(Icons.Rounded.SportsHockey, LifeArea.SPORT.color, "Fitness", "Schritte, Schlaf, Puls, Ernährung, Sport-Zeitfenster · Samsung Health & MyFitnessPal", onOpenSport)
                        AreaItem(Icons.Rounded.Flag, LifeArea.PRIVATE.color, "Ziele & Gewohnheiten", "Ironman-Weg, Abi 1,7, Streaks", onOpenGoals)
                        AreaItem(Icons.Rounded.AttachMoney, LifeArea.JOB.color, "Finanzen", "Konten, Budgets, Puffer, Sparziele, DKB-Import", onOpenFinance)
                        AreaItem(Icons.Rounded.Storefront, LifeArea.ETSY.color, "Etsy-Shop", "Umsatz, Motiv-Pipeline, Checkliste", onOpenEtsy)
                    }
                }

                val s = settings ?: return@LazyColumn
                item { SectionTitle("Einstellungen") }
                item {
                    SectionCard {
                        ListItem(
                            headlineContent = { Text("Dein Name") },
                            supportingContent = { Text(s.userName) },
                            modifier = Modifier.clickable { editName = true },
                            colors = transparentItem(),
                        )
                        ListItem(
                            headlineContent = { Text("Erscheinungsbild") },
                            supportingContent = { Text("Hell, dunkel oder wie das Gerät") },
                            colors = transparentItem(),
                        )
                        val modes = listOf("SYSTEM" to "Automatisch", "LIGHT" to "Hell", "DARK" to "Dunkel")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            modes.forEachIndexed { index, (key, label) ->
                                SegmentedButton(
                                    selected = s.themeMode == key,
                                    onClick = { viewModel.setThemeMode(key) },
                                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                ) { Text(label) }
                            }
                        }
                        ListItem(
                            headlineContent = { Text("Karten auf „Heute“") },
                            supportingContent = {
                                Column {
                                    Text("Ein-/ausblenden und mit ↑ nach oben holen.")
                                    s.todayCards.forEachIndexed { index, key ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            app.lifedashboard.ui.components.CheckMark(checked = key !in s.hiddenTodayCards, onCheckedChange = { viewModel.toggleCard(key) }, size = 20.dp)
                                            Text(app.lifedashboard.data.settings.todayCardLabel(key), modifier = Modifier.weight(1f))
                                            if (index > 0) {
                                                IconButton(onClick = { viewModel.moveCardUp(key) }) { Icon(Icons.Rounded.KeyboardArrowUp, "nach oben") }
                                            }
                                        }
                                    }
                                }
                            },
                            colors = transparentItem(),
                        )
                        ListItem(
                            headlineContent = { Text("Prioritäten bei wenig Zeit") },
                            supportingContent = {
                                Column {
                                    Text("Was die Planung zuerst bekommt. Tippe ↑, um etwas nach vorne zu holen.")
                                    s.priorityOrder.forEachIndexed { index, area ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}. ${areaName(area)}", modifier = Modifier.weight(1f))
                                            if (index > 0) {
                                                IconButton(onClick = { viewModel.movePriorityUp(area) }) {
                                                    Icon(Icons.Rounded.KeyboardArrowUp, "nach oben")
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            colors = transparentItem(),
                        )
                        ListItem(
                            headlineContent = { Text("Strenger Coach") },
                            supportingContent = { Text("Verpasste Schul-Blöcke kosten Punkte und brechen die Serie, ab 1 Std Rückstand ist die Zockzeit gesperrt") },
                            trailingContent = { Switch(checked = s.strictCoach, onCheckedChange = { viewModel.setStrict(it) }) },
                            colors = transparentItem(),
                        )
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
                        val thisWeek = s.weekCalculator(freeRanges).weekTypeFor(LocalDate.now())
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
                            headlineContent = { Text("Tag unter der Woche") },
                            supportingContent = { Text("${s.dayStart.hhmm()}–${s.dayEnd.hhmm()} (aufstehen bis schlafen)") },
                            colors = transparentItem(),
                        )
                        ListItem(
                            headlineContent = { Text("Wochenende ab") },
                            supportingContent = { Text("${s.weekendDayStart.hhmm()} Uhr – vorher plant die App nichts") },
                            modifier = Modifier.clickable {
                                context.pickTime(s.weekendDayStart) { viewModel.setWeekendStart(it.hour * 60 + it.minute) }
                            },
                            colors = transparentItem(),
                        )
                    }
                }
                item { SectionTitle("Daten") }
                item {
                    SectionCard {
                        AreaItem(Icons.Rounded.Backup, LifeArea.SCHOOL.color, "Backup & Übertragen", "Verschlüsselt sichern (für den Alltag: „Handy ↔ Tablet“)", onOpenBackup)
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

    val s = settings
    if (editName && s != null) {
        var name by remember { mutableStateOf(s.userName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Wie soll ich dich nennen?") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setName(name)
                    editName = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Abbrechen") } },
        )
    }
}

private fun areaName(key: String) = when (key) {
    "SCHOOL" -> "Schule & Klausuren"
    "SPORT" -> "Sport"
    "ETSY" -> "Etsy"
    else -> key
}

@Composable
private fun transparentItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun AreaItem(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: (() -> Unit)?) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { IconBadge(icon, color) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
        colors = transparentItem(),
    )
}
