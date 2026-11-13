package app.lifedashboard.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lifedashboard.ui.theme.LocalAppColors

val DOCK_HEIGHT = 64.dp

/**
 * Schwebendes Dock am Handy. Die Markierung gleitet federnd zum gewählten Bereich,
 * Symbole geben beim Antippen kurz nach.
 */
@Composable
fun FloatingDock(
    items: List<Destination>,
    selected: Destination?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalAppColors.current
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DOCK_HEIGHT)
            .shadow(if (app.isDark) 0.dp else 18.dp, shape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.14f))
            .clip(shape)
            .background(if (app.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else app.card)
            .border(1.dp, app.hairline, shape)
            .padding(horizontal = 6.dp),
    ) {
        val slot = maxWidth / items.size
        val index = items.indexOf(selected)
        val x by animateDpAsState(slot * index.coerceAtLeast(0), spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "dock")
        val visible by animateFloatAsState(if (index >= 0) 1f else 0f, label = "dockAlpha")
        Box(
            Modifier
                .offset(x = x + (slot - 44.dp) / 2)
                .align(Alignment.CenterStart)
                .size(44.dp)
                .graphicsLayer { alpha = visible }
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, app.glow, 0.45f)))),
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                DockIcon(item, item == selected, Modifier.weight(1f).fillMaxHeight()) { onSelect(item) }
            }
        }
    }
}

@Composable
private fun DockIcon(item: Destination, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else if (selected) 1.06f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press")
    Box(
        modifier
            .selectable(selected = selected, interactionSource = source, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

val SIDE_DOCK_COLLAPSED = 76.dp
val SIDE_DOCK_EXPANDED = 236.dp

/**
 * Dock links am Tablet: schmal nur mit Symbolen, per Tipp auf das Menü oder Wisch nach rechts
 * klappt es mit Namen auf.
 */
@Composable
fun SideDock(
    primary: List<Destination>,
    secondary: List<Destination>,
    footer: Destination,
    selected: Destination?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalAppColors.current
    val width by animateDpAsState(if (expanded) SIDE_DOCK_EXPANDED else SIDE_DOCK_COLLAPSED, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "sideDock")
    val labelAlpha by animateFloatAsState(if (expanded) 1f else 0f, label = "labels")
    Row(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(width)
                .fillMaxHeight()
                .background(app.card)
                .pointerInput(expanded) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > 40f) onExpandedChange(true) else if (total < -40f) onExpandedChange(false)
                        },
                    ) { _, drag -> total += drag }
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SideItem(
                icon = if (expanded) Icons.Rounded.MenuOpen else Icons.Rounded.Menu,
                label = "Life Dashboard",
                description = if (expanded) "Leiste einklappen" else "Leiste ausklappen",
                selected = false,
                labelAlpha = labelAlpha,
                strong = true,
            ) { onExpandedChange(!expanded) }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                primary.forEach { d -> SideItem(d.icon, d.label, d.label, d == selected, labelAlpha) { onSelect(d) } }
                Box(Modifier.padding(vertical = 10.dp, horizontal = 8.dp).fillMaxWidth().height(1.dp).background(app.hairline))
                secondary.forEach { d -> SideItem(d.icon, d.label, d.label, d == selected, labelAlpha) { onSelect(d) } }
            }
            SideItem(footer.icon, footer.label, footer.label, footer == selected, labelAlpha) { onSelect(footer) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(app.hairline))
    }
}

@Composable
private fun SideItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    selected: Boolean,
    labelAlpha: Float,
    strong: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "press")
    val tint = if (selected) MaterialTheme.colorScheme.primary else if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, interactionSource = source, indication = ripple(), role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        }
        Text(
            label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.titleSmall,
            letterSpacing = 0.sp,
            color = if (selected || strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
    }
}
