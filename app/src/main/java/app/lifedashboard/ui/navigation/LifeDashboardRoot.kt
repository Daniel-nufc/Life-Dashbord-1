package app.lifedashboard.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lifedashboard.data.repository.ProgressRepository
import app.lifedashboard.data.settings.SettingsRepository
import app.lifedashboard.planner.ProgressState
import app.lifedashboard.ui.calendar.CalendarScreen
import app.lifedashboard.ui.components.LocalDockInset
import app.lifedashboard.ui.components.LocalHideBack
import app.lifedashboard.ui.components.ProgressRing
import app.lifedashboard.ui.connections.ConnectionsScreen
import app.lifedashboard.ui.learn.LearnScreen
import app.lifedashboard.ui.more.MoreScreen
import app.lifedashboard.ui.progress.LevelUpOverlay
import app.lifedashboard.ui.progress.ProgressScreen
import app.lifedashboard.ui.sport.FitnessScreen
import app.lifedashboard.ui.tasks.TasksScreen
import app.lifedashboard.ui.theme.LocalAppColors
import app.lifedashboard.ui.today.TodayScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Alle Bereiche der App. Die ersten sechs liegen im Dock, der Rest in der Seitenleiste. */
enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Heute", Icons.Rounded.Today),
    CALENDAR("calendar", "Kalender", Icons.Rounded.CalendarMonth),
    TASKS("tasks", "Aufgaben", Icons.Rounded.ChecklistRtl),
    LEARN("learn", "Lernen", Icons.Rounded.School),
    SPORT("sport", "Fitness", Icons.Rounded.DirectionsRun),
    FINANCE("finance", "Finanzen", Icons.Rounded.AccountBalanceWallet),
    PROGRESS("progress", "Fortschritt", Icons.Rounded.EmojiEvents),
    GOALS("goals", "Ziele", Icons.Rounded.Flag),
    ETSY("etsy", "Etsy", Icons.Rounded.Storefront),
    REVIEW("review", "Wochenrückblick", Icons.Rounded.Insights),
    MORE("more", "Mehr", Icons.Rounded.Tune),
}

private val DOCK = listOf(Destination.TODAY, Destination.CALENDAR, Destination.TASKS, Destination.LEARN, Destination.SPORT, Destination.FINANCE)
private val SECONDARY = listOf(Destination.PROGRESS, Destination.GOALS, Destination.ETSY, Destination.REVIEW)

private const val CONNECTIONS_ROUTE = "connections"
private const val BACKUP_ROUTE = "backup"
private const val HOLIDAYS_ROUTE = "holidays"
private const val SYNC_ROUTE = "sync"

/** Welcher Eintrag in Dock/Seitenleiste ist markiert? Einstellungs-Unterseiten gehören zu „Mehr“. */
private fun destinationFor(route: String?): Destination? =
    Destination.entries.firstOrNull { it.route == route } ?: when (route) {
        CONNECTIONS_ROUTE, BACKUP_ROUTE, HOLIDAYS_ROUTE, SYNC_ROUTE -> Destination.MORE
        else -> null
    }

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    private val settings: SettingsRepository,
    private val ical: app.lifedashboard.data.sync.IcalSyncRepository,
    private val titleChoices: app.lifedashboard.data.repository.TitleChoiceRepository,
    private val systemCalendar: app.lifedashboard.data.sync.SystemCalendarRepository,
    private val agenda: app.lifedashboard.data.repository.AgendaRepository,
    private val planDao: app.lifedashboard.data.db.PlanDao,
    private val engine: app.lifedashboard.data.planning.PlanningEngine,
    private val health: app.lifedashboard.data.sync.HealthRepository,
) : ViewModel() {
    val progress: StateFlow<ProgressState?> = progressRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastSeenLevel: StateFlow<Int?> = settings.settings.map { it.lastSeenLevel }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val userName: StateFlow<String> = settings.settings.map { it.userName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** Aktuelle Einstellungen (für den Ziele-Dialog) */
    val appSettings: StateFlow<app.lifedashboard.data.settings.AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Erster Start mit v28: Coach startet heute neu (alter Rückstand aus nie gesehenen Vorschlägen zählt nicht)
        viewModelScope.launch { settings.ensureCoachStart() }
        // Wenig geschlafen (unter 6 Std, Health Connect)? → heute etwas leichter planen
        viewModelScope.launch {
            val sleep = runCatching { health.lastNightSleep() }.getOrNull() ?: return@launch
            settings.markShortSleep(java.time.LocalDate.now(), sleep < java.time.Duration.ofHours(6))
        }
        viewModelScope.launch {
            titleChoices.ensureSeeded()
            connectFamilyWall()
        }
        // Plan immer passend halten: Kommt irgendwo in den nächsten 2 Wochen etwas dazu (Termin, FamilyWall,
        // Schicht, Aufgabe, Klausur – oder ein bestätigter Block vom Tablet), wird neu verteilt.
        viewModelScope.launch {
            val today = java.time.LocalDate.now()
            kotlinx.coroutines.flow.combine(agenda.observe(today, 14), planDao.observeKeptFrom(today.toEpochDay())) { a, k ->
                a.days.map { Triple(it.busyRanges(), it.reservedDay, it.tasksDue.map { t -> t.id to t.done }) } to
                    (a.allTasks.map { it.id to it.dueEpochDay } to k.map { listOf(it.id, it.status, it.startMinute, it.dateEpochDay) })
            }
                .distinctUntilChanged()
                .debounce(2_500)
                .conflate()
                .collect { runCatching { engine.planDays(java.time.LocalDate.now(), 14) } }
        }
        // Beim Öffnen Kalender-Abos (FamilyWall, Lernraum) auffrischen, wenn sie älter als 30 Minuten sind
        viewModelScope.launch { ical.syncStale(30 * 60_000L) }
    }

    /** FamilyWall-App auf dem Gerät? Dann einmalig deren Handy-Kalender übernehmen – nur meine Termine. */
    private suspend fun connectFamilyWall() {
        if (!systemCalendar.hasPermission()) return
        val fw = systemCalendar.calendars().firstOrNull { it.isFamilyWall } ?: return
        if (!settings.claimFamilyWallAutoConnect()) return
        val s = settings.settings.first()
        settings.setSystemCalendarIds(s.systemCalendarIds + fw.id)
        settings.setOnlyMineCalendarIds(s.onlyMineCalendarIds + fw.id)
        ical.all.first().filter { it.isFamilyWallSource() }.forEach { ical.delete(it) }
    }

    private fun app.lifedashboard.data.db.IcalSourceEntity.isFamilyWallSource() =
        name.contains("familywall", ignoreCase = true) || url.contains("familywall", ignoreCase = true)

    fun restartCoach() {
        viewModelScope.launch { settings.restartCoach() }
    }

    fun setGoals(stepGoal: Int, sportWeek: Int, studyWeek: Int) {
        viewModelScope.launch { settings.setGoals(stepGoal, sportWeek, studyWeek) }
    }

    fun redeemGaming(minutes: Int) {
        viewModelScope.launch { settings.redeemGaming(minutes) }
    }

    fun levelSeen(level: Int) {
        viewModelScope.launch { settings.setLastSeenLevel(level) }
    }
}

/**
 * Hauptnavigation.
 * Handy: schwebendes Dock unten, Seitenleiste per Wisch vom linken Rand.
 * Tablet: Dock links, das sich zur Leiste mit Namen aufklappen lässt.
 */
@Composable
fun LifeDashboardRoot(viewModel: RootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = destinationFor(backStackEntry?.destination?.route)
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val lastSeenLevel by viewModel.lastSeenLevel.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()

    // Einmal nach Benachrichtigungen fragen (Android 13+)
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionsAsked by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { permissionsAsked = true }
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else permissionsAsked = true
    }
    // Danach einmal nach der Akku-Ausnahme fragen (Samsung-Tiefschlaf)
    BatteryPrompt(enabled = permissionsAsked)

    // Level-Aufstieg: nur einmal feiern. Beim allerersten Start nach dem Update still merken.
    var celebrate by rememberSaveable { mutableStateOf<Int?>(null) }
    val level = progress?.level?.level
    LaunchedEffect(level, lastSeenLevel) {
        val seen = lastSeenLevel
        if (level != null && seen != null && level != seen) {
            if (seen in 1 until level) celebrate = level
            viewModel.levelSeen(level)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(LocalAppColors.current.screenBackground)) {
        val wide = maxWidth >= 720.dp
        val host: @Composable () -> Unit = { AppNavHost(navController) }

        if (wide) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            Row(Modifier.fillMaxSize()) {
                SideDock(
                    primary = DOCK,
                    secondary = SECONDARY,
                    footer = Destination.MORE,
                    selected = current,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    onSelect = { navController.openTab(it.route) },
                    modifier = Modifier
                        .background(LocalAppColors.current.card)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
                )
                Box(Modifier.weight(1f).fillMaxSize()) { host() }
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    DrawerContent(userName, progress, current) {
                        scope.launch { drawerState.close() }
                        navController.openTab(it.route)
                    }
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalDockInset provides DOCK_HEIGHT + 12.dp) { host() }
                    FloatingDock(
                        items = DOCK + Destination.MORE,
                        selected = if (current in DOCK) current else Destination.MORE.takeIf { current != null },
                        onSelect = { navController.openTab(it.route) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    )
                }
            }
        }

        celebrate?.let { reached ->
            LevelUpOverlay(level = reached, title = progress?.level?.title.orEmpty()) { celebrate = null }
        }
    }
}

/** Seitenleiste am Handy: alle Bereiche mit Namen, oben dein Level. */
@Composable
private fun DrawerContent(userName: String, progress: ProgressState?, current: Destination?, onSelect: (Destination) -> Unit) {
    val app = LocalAppColors.current
    ModalDrawerSheet(
        drawerContainerColor = app.card,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.width(296.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 18.dp)) {
            Row(Modifier.padding(start = 10.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing(progress?.level?.fraction ?: 0f, MaterialTheme.colorScheme.primary, size = 46.dp, stroke = 4.dp) {
                    Text("${progress?.level?.level ?: 1}", style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text(userName.ifBlank { "Life Dashboard" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        progress?.let { "Level ${it.level.level} · ${it.level.title}" } ?: "Life Dashboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DOCK.forEach { DrawerItem(it, it == current, onSelect) }
            Box(Modifier.padding(vertical = 10.dp, horizontal = 10.dp).fillMaxWidth().height(1.dp).background(app.hairline))
            SECONDARY.forEach { DrawerItem(it, it == current, onSelect) }
            Box(Modifier.padding(vertical = 10.dp, horizontal = 10.dp).fillMaxWidth().height(1.dp).background(app.hairline))
            DrawerItem(Destination.MORE, current == Destination.MORE, onSelect)
        }
    }
}

@Composable
private fun DrawerItem(item: Destination, selected: Boolean, onSelect: (Destination) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable { onSelect(item) }
            .semantics { contentDescription = "Leiste: ${item.label}" }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(item.icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(item.label, style = MaterialTheme.typography.titleSmall, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = Destination.TODAY.route,
        // Weicher Wechsel: neuer Bereich blendet ein und rückt leicht nach
        enterTransition = { fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 28 } },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(240), initialScale = 0.985f) },
        popExitTransition = { fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.985f) },
    ) {
        composable(Destination.TODAY.route) {
            TodayScreen(
                onOpenTasks = { navController.openTab(Destination.TASKS.route) },
                onOpenGoals = { navController.openTab(Destination.GOALS.route) },
                onOpenReview = { navController.openTab(Destination.REVIEW.route) },
                onOpenProgress = { navController.openTab(Destination.PROGRESS.route) },
            )
        }
        composable(Destination.CALENDAR.route) { CalendarScreen() }
        composable(Destination.TASKS.route) { TasksScreen() }
        composable(Destination.LEARN.route) { LearnScreen() }
        composable(Destination.MORE.route) {
            MoreScreen(
                onOpenSport = { navController.openTab(Destination.SPORT.route) },
                onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                onOpenGoals = { navController.navigate(Destination.GOALS.route) { launchSingleTop = true } },
                onOpenFinance = { navController.openTab(Destination.FINANCE.route) },
                onOpenEtsy = { navController.navigate(Destination.ETSY.route) { launchSingleTop = true } },
                onOpenReview = { navController.navigate(Destination.REVIEW.route) { launchSingleTop = true } },
                onOpenBackup = { navController.navigate(BACKUP_ROUTE) },
                onOpenHolidays = { navController.navigate(HOLIDAYS_ROUTE) },
                onOpenSync = { navController.navigate(SYNC_ROUTE) },
                onOpenProgress = { navController.navigate(Destination.PROGRESS.route) { launchSingleTop = true } },
            )
        }
        composable(Destination.SPORT.route) {
            CompositionLocalProvider(LocalHideBack provides true) { FitnessScreen(onBack = back) }
        }
        composable(Destination.FINANCE.route) {
            CompositionLocalProvider(LocalHideBack provides true) { app.lifedashboard.ui.finance.FinanceScreen(onBack = back) }
        }
        composable(Destination.PROGRESS.route) { ProgressScreen(onBack = back) }
        composable(Destination.GOALS.route) { app.lifedashboard.ui.goals.GoalsScreen(onBack = back) }
        composable(Destination.ETSY.route) {
            app.lifedashboard.ui.etsy.EtsyScreen(onBack = back, onOpenTasks = { navController.openTab(Destination.TASKS.route) })
        }
        composable(Destination.REVIEW.route) { app.lifedashboard.ui.review.ReviewScreen(onBack = back) }
        composable(SYNC_ROUTE) { app.lifedashboard.ui.sync.SyncScreen(onBack = back) }
        composable(HOLIDAYS_ROUTE) { app.lifedashboard.ui.holidays.HolidaysScreen(onBack = back) }
        composable(BACKUP_ROUTE) { app.lifedashboard.ui.backup.BackupScreen(onBack = back) }
        composable(CONNECTIONS_ROUTE) { ConnectionsScreen(onBack = back) }
    }
}
