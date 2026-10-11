package com.sonothamin.anythingwidgets.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class NavTab(val label: String, val icon: ImageVector)

/**
 * Floating capsule nav: real backdrop blur (API 31+; a denser tint is used below that),
 * a hairline highlight border, and a selected pill that morphs and reveals only its own title.
 */
@Composable
fun GlassNavBar(
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    var origin by remember { mutableStateOf(Offset.Zero) }

    val tint = if (dark) Color(0xFF1A1A1A).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.82f)
    val indicator = if (dark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.12f)
    val stroke = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.28f else 0.70f), Color.White.copy(alpha = 0.06f)),
    )

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(CircleShape)
            .border(1.dp, stroke, CircleShape),
    ) {
        // 1) Blurred copy of whatever is behind the capsule.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val r = 22.dp.toPx()
                    renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    clip = true
                    shape = CircleShape
                }
                .drawBehind { translate(-origin.x, -origin.y) { drawLayer(backdrop) } },
        )
        // 2) Frost tint.
        Box(Modifier.matchParentSize().background(tint))

        // 3) Tabs.
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                val bg by animateColorAsState(
                    if (selected) indicator else Color.Transparent,
                    spring(stiffness = Spring.StiffnessMediumLow), label = "bg",
                )
                val fg by animateColorAsState(
                    if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                    spring(stiffness = Spring.StiffnessMediumLow), label = "fg",
                )
                Row(
                    Modifier
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(i) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = fg, modifier = Modifier.size(24.dp))
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally(spring(0.8f, Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkHorizontally(spring(0.8f, Spring.StiffnessMediumLow)) + fadeOut(),
                    ) {
                        Text(
                            tab.label,
                            color = fg,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
