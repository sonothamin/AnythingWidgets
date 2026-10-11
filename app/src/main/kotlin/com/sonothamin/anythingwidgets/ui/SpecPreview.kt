package com.sonothamin.anythingwidgets.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.widget.DataSnapshot
import com.sonothamin.anythingwidgets.widget.spec.Node
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec

/** Sizes the preview can mimic. They match the sizes SpecWidget registers (and its compact cutoff of 180dp). */
enum class PreviewSize(val label: String, val width: Int, val height: Int) {
    Small("Small", 110, 110),
    Wide("Wide", 250, 110),
    Large("Large", 250, 250),
}

/**
 * In-app preview of a widget. Mirrors SpecRenderer (which draws with Glance and can't run inside
 * the app) using regular Compose, with the same node semantics. Taps are intentionally inert.
 */
@Composable
fun SpecPreview(
    spec: WidgetSpec,
    size: PreviewSize,
    data: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val node = if (size.width < 180) spec.compact ?: spec.root else spec.root
    Surface(
        modifier = modifier.size(size.width.dp, size.height.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize()) { PreviewNode(node, data, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun PreviewNode(node: Node, data: Map<String, String>, extra: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val m = node.toPreviewModifier(extra, scheme)
    when (node.type) {
        "column" -> Column(
            modifier = m,
            verticalArrangement = Arrangement.spacedBy(node.gap.dp, vAlign(node.justify)),
            horizontalAlignment = hAlign(node.align),
        ) {
            node.children.forEach { child ->
                PreviewNode(child, data, if (child.grow) Modifier.weight(1f) else Modifier)
            }
        }

        "row" -> Row(
            modifier = m,
            horizontalArrangement = Arrangement.spacedBy(node.gap.dp, hAlign(node.justify)),
            verticalAlignment = vAlign(node.align),
        ) {
            node.children.forEach { child ->
                PreviewNode(child, data, if (child.grow) Modifier.weight(1f) else Modifier)
            }
        }

        "box" -> Box(modifier = m, contentAlignment = boxAlign(node.align)) {
            node.children.forEach { PreviewNode(it, data) }
        }

        "text" -> Text(
            text = DataSnapshot.apply(node.text.orEmpty(), data),
            modifier = m,
            color = colorOf(node.color, scheme.onSurface, scheme),
            fontSize = node.size.sp,
            lineHeight = (node.size * 1.2f).sp,
            fontWeight = if (node.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (node.textAlign) {
                "center" -> TextAlign.Center
                "end" -> TextAlign.End
                else -> TextAlign.Start
            },
            maxLines = node.maxLines ?: Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )

        "spacer" -> Spacer(m)

        "progress" -> LinearProgressIndicator(
            progress = {
                DataSnapshot.apply(node.progress.orEmpty(), data).toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
            },
            modifier = m,
            color = colorOf(node.color, scheme.primary, scheme),
            trackColor = colorOf(node.track, scheme.secondaryContainer, scheme),
        )
    }
}

private fun Node.toPreviewModifier(extra: Modifier, scheme: ColorScheme): Modifier {
    var m = extra
    when (fill) {
        "width" -> m = m.fillMaxWidth()
        "height" -> m = m.fillMaxHeight()
        "both" -> m = m.fillMaxSize()
    }
    width?.let { m = m.width(it.dp) }
    height?.let { m = m.height(it.dp) }
    // Same order as the Glance renderer: background -> corner radius -> padding.
    val shape = RoundedCornerShape(radius.dp)
    background?.let { m = m.background(colorOf(it, scheme.surface, scheme), shape) }
    if (radius > 0) m = m.clip(shape)
    if (padding > 0) m = m.padding(padding.dp)
    return m
}

private fun colorOf(ref: String?, fallback: Color, s: ColorScheme): Color {
    if (ref == null) return fallback
    return when (ref) {
        "primary" -> s.primary
        "onPrimary" -> s.onPrimary
        "primaryContainer" -> s.primaryContainer
        "onPrimaryContainer" -> s.onPrimaryContainer
        "secondary" -> s.secondary
        "onSecondary" -> s.onSecondary
        "secondaryContainer" -> s.secondaryContainer
        "onSecondaryContainer" -> s.onSecondaryContainer
        "tertiary" -> s.tertiary
        "onTertiary" -> s.onTertiary
        "tertiaryContainer" -> s.tertiaryContainer
        "onTertiaryContainer" -> s.onTertiaryContainer
        "surface" -> s.surface
        "onSurface" -> s.onSurface
        "surfaceVariant" -> s.surfaceVariant
        "onSurfaceVariant" -> s.onSurfaceVariant
        "background" -> s.background
        "onBackground" -> s.onBackground
        "error" -> s.error
        "outline" -> s.outline
        else -> runCatching { Color(android.graphics.Color.parseColor(ref)) }.getOrDefault(fallback)
    }
}

private fun hAlign(s: String?): Alignment.Horizontal = when (s) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun vAlign(s: String?): Alignment.Vertical = when (s) {
    "center" -> Alignment.CenterVertically
    "end" -> Alignment.Bottom
    else -> Alignment.Top
}

private fun boxAlign(s: String?): Alignment = when (s) {
    "top" -> Alignment.TopCenter
    "top-end" -> Alignment.TopEnd
    "start" -> Alignment.CenterStart
    "center" -> Alignment.Center
    "end" -> Alignment.CenterEnd
    "bottom-start" -> Alignment.BottomStart
    "bottom" -> Alignment.BottomCenter
    "bottom-end" -> Alignment.BottomEnd
    else -> Alignment.TopStart
}
