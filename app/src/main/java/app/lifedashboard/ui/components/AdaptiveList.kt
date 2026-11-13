package app.lifedashboard.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liste für alle Bereiche: am Handy eine Spalte, am Tablet ein Raster mit 2–3 Spalten
 * (Karten fließen wie auf „Heute“ ineinander). Kopfkarten und Überschriften mit [wideItem] gehen über die volle Breite.
 */
@Composable
fun AdaptiveList(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = 380.dp,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minColumnWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = padding.listPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        content = content,
    )
}

/** Eintrag über die ganze Breite (Kopfkarte, Überschrift, Umschalter, Hinweis). */
fun LazyStaggeredGridScope.wideItem(key: Any? = null, content: @Composable LazyStaggeredGridItemScope.() -> Unit) =
    item(key = key, span = StaggeredGridItemSpan.FullLine, content = content)
