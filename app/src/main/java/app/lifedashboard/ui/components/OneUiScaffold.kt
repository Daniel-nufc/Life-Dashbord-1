package app.lifedashboard.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifedashboard.ui.theme.LocalAppColors

/** Höhe des schwebenden Docks am Handy – damit „Neu“-Knöpfe darüber sitzen (Tablet: 0). */
val LocalDockInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Bereiche, die direkt im Dock liegen, brauchen keinen Zurück-Pfeil. */
val LocalHideBack = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Bildschirm im One-UI-Stil: große Überschrift oben (viel Platz, damit der Inhalt mit dem
 * Daumen erreichbar weiter unten beginnt), die beim Scrollen zu einer kleinen Leiste schrumpft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val background = LocalAppColors.current.screenBackground
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { Box(Modifier.padding(bottom = LocalDockInset.current)) { floatingActionButton() } },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                    )
                },
                navigationIcon = { if (!LocalHideBack.current) navigationIcon() },
                actions = actions,
                expandedHeight = 148.dp,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(top = 0.dp)) { content(padding) }
    }
}

/** Standard-Innenabstand für Listen unterhalb der großen Überschrift. */
fun PaddingValues.listPadding(): PaddingValues = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = calculateTopPadding() + 4.dp,
    bottom = calculateBottomPadding() + 24.dp,
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}
