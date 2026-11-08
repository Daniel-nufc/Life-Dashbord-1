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
import app.lifedashboard.ui.placeholder.ComingSoonScreen
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

/** Unterseiten gehören optisch zu einem Tab (z. B. Sport → Mehr). */
private fun topLevelFor(route: String?): String? = when (route) {
    SPORT_ROUTE, CONNECTIONS_ROUTE -> TopLevelDestination.MORE.route
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
                TodayScreen(onOpenTasks = { navController.openTab(TopLevelDestination.TASKS.route) })
            }
            composable(TopLevelDestination.CALENDAR.route) { CalendarScreen() }
            composable(TopLevelDestination.TASKS.route) { TasksScreen() }
            composable(TopLevelDestination.LEARN.route) {
                ComingSoonScreen(
                    title = "Lernen",
                    icon = Icons.Rounded.School,
                    phase = "Phase 3",
                    features = listOf(
                        "Klausuren mit Countdown und Themenlisten",
                        "Automatischer Lernplan rückwärts von der Klausur",
                        "Wiederholungen nach Spaced Repetition (auch für den Schulweg)",
                        "Fokus-Timer 25/5 mit Statistik",
                        "Notenpunkte (0–15), Schnitt und Ziel 1,7",
                    ),
                )
            }
            composable(TopLevelDestination.MORE.route) {
                MoreScreen(
                    onOpenSport = { navController.navigate(SPORT_ROUTE) },
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                )
            }
            composable(SPORT_ROUTE) { SportScreen(onBack = { navController.popBackStack() }) }
            composable(CONNECTIONS_ROUTE) { ConnectionsScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
