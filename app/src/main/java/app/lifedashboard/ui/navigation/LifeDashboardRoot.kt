package app.lifedashboard.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChecklistRtl
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lifedashboard.ui.calendar.CalendarScreen
import app.lifedashboard.ui.connections.ConnectionsScreen
import app.lifedashboard.ui.more.MoreScreen
import app.lifedashboard.ui.learn.LearnScreen
import app.lifedashboard.ui.sport.SportScreen
import app.lifedashboard.ui.tasks.TasksScreen
import app.lifedashboard.ui.today.TodayScreen

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Heute", Icons.Rounded.Today),
    CALENDAR("calendar", "Kalender", Icons.Rounded.CalendarMonth),
    TASKS("tasks", "Aufgaben", Icons.Rounded.ChecklistRtl),
    LEARN("learn", "Lernen", Icons.Rounded.School),
    MORE("more", "Mehr", Icons.Rounded.MoreHoriz),
}

private const val SPORT_ROUTE = "sport"
private const val CONNECTIONS_ROUTE = "connections"
private const val GOALS_ROUTE = "goals"
private const val FINANCE_ROUTE = "finance"
private const val ETSY_ROUTE = "etsy"
private const val REVIEW_ROUTE = "review"

/** Unterseiten gehören optisch zu einem Tab (z. B. Sport → Mehr). */
private fun topLevelFor(route: String?): String? = when (route) {
    SPORT_ROUTE, CONNECTIONS_ROUTE, GOALS_ROUTE, FINANCE_ROUTE, ETSY_ROUTE, REVIEW_ROUTE -> TopLevelDestination.MORE.route
    else -> route
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Hauptnavigation. NavigationSuiteScaffold wählt automatisch:
 * Handy → Leiste unten (Einhandbedienung), Tablet → Leiste an der Seite.
 */
@Composable
fun LifeDashboardRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = topLevelFor(backStackEntry?.destination?.route)

    // Einmal nach Benachrichtigungen fragen (Android 13+)
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = currentTab == destination.route,
                    onClick = { navController.openTab(destination.route) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = TopLevelDestination.TODAY.route) {
            composable(TopLevelDestination.TODAY.route) {
                TodayScreen(
                    onOpenTasks = { navController.openTab(TopLevelDestination.TASKS.route) },
                    onOpenGoals = { navController.navigate(GOALS_ROUTE) },
                    onOpenReview = { navController.navigate(REVIEW_ROUTE) },
                )
            }
            composable(TopLevelDestination.CALENDAR.route) { CalendarScreen() }
            composable(TopLevelDestination.TASKS.route) { TasksScreen() }
            composable(TopLevelDestination.LEARN.route) { LearnScreen() }
            composable(TopLevelDestination.MORE.route) {
                MoreScreen(
                    onOpenSport = { navController.navigate(SPORT_ROUTE) },
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                    onOpenGoals = { navController.navigate(GOALS_ROUTE) },
                    onOpenFinance = { navController.navigate(FINANCE_ROUTE) },
                    onOpenEtsy = { navController.navigate(ETSY_ROUTE) },
                    onOpenReview = { navController.navigate(REVIEW_ROUTE) },
                )
            }
            composable(REVIEW_ROUTE) { app.lifedashboard.ui.review.ReviewScreen(onBack = { navController.popBackStack() }) }
            composable(ETSY_ROUTE) {
                app.lifedashboard.ui.etsy.EtsyScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTasks = { navController.openTab(TopLevelDestination.TASKS.route) },
                )
            }
            composable(FINANCE_ROUTE) { app.lifedashboard.ui.finance.FinanceScreen(onBack = { navController.popBackStack() }) }
            composable(SPORT_ROUTE) { SportScreen(onBack = { navController.popBackStack() }) }
            composable(GOALS_ROUTE) { app.lifedashboard.ui.goals.GoalsScreen(onBack = { navController.popBackStack() }) }
            composable(CONNECTIONS_ROUTE) { ConnectionsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
