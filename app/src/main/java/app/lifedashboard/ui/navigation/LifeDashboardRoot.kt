package app.lifedashboard.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lifedashboard.ui.more.MoreScreen
import app.lifedashboard.ui.placeholder.ComingSoonScreen
import app.lifedashboard.ui.sport.SportScreen
import app.lifedashboard.ui.timetable.TimetableScreen
import app.lifedashboard.ui.today.TodayScreen

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Heute", Icons.Rounded.Today),
    CALENDAR("calendar", "Kalender", Icons.Rounded.CalendarMonth),
    LEARN("learn", "Lernen", Icons.Rounded.School),
    SPORT("sport", "Sport", Icons.Rounded.FitnessCenter),
    MORE("more", "Mehr", Icons.Rounded.MoreHoriz),
}

/**
 * Hauptnavigation. NavigationSuiteScaffold wählt automatisch:
 * Handy → Leiste unten (Einhandbedienung), Tablet → Leiste an der Seite.
 */
@Composable
fun LifeDashboardRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = TopLevelDestination.TODAY.route) {
            composable(TopLevelDestination.TODAY.route) { TodayScreen() }
            composable(TopLevelDestination.CALENDAR.route) { TimetableScreen() }
            composable(TopLevelDestination.LEARN.route) {
                ComingSoonScreen(
                    title = "Lernen",
                    icon = Icons.Rounded.School,
                    phase = "Phase 3",
                    features = listOf(
                        "Klausuren mit Countdown und Themenlisten",
                        "Automatischer Lernplan rückwärts von der Klausur",
                        "Wiederholungen nach Spaced Repetition",
                        "Fokus-Timer (Pomodoro) mit Statistik",
                        "Noten in Punkten (0–15) und Durchschnitt",
                        "Hausaufgabenplaner (Phase 2) – fällig zur nächsten Stunde des Fachs",
                    ),
                )
            }
            composable(TopLevelDestination.SPORT.route) { SportScreen() }
            composable(TopLevelDestination.MORE.route) { MoreScreen() }
        }
    }
}
