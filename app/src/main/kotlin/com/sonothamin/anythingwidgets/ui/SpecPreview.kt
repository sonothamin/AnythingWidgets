package com.sonothamin.anythingwidgets.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.widget.ClockViews
import com.sonothamin.anythingwidgets.widget.DataSnapshot
import com.sonothamin.anythingwidgets.widget.DotArt
import com.sonothamin.anythingwidgets.widget.spec.NothingPalette
import com.sonothamin.anythingwidgets.widget.WidgetRuntime
import com.sonothamin.anythingwidgets.widget.spec.ActionSpec
import com.sonothamin.anythingwidgets.widget.spec.Node
import com.sonothamin.anythingwidgets.widget.spec.SpecRules
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec

/** Sizes the preview can mimic. They match the sizes SpecWidget registers (and its compact cutoff of 180dp). */
enum class PreviewSize(val label: String, val width: Int, val height: Int) {
    Small("Small", 110, 110),
    Wide("Wide", 250, 110),
    Large("Large", 250, 250),
}

/** Width in dp of the widget being previewed, so glyph text wraps where the real widget would. */
private val LocalPreviewWidth = compositionLocalOf { 240f }

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
    /** When set, taps on mini-app nodes (counters, toggles, pickers) are reported here so the preview is playable. */
    onAction: ((ActionSpec) -> Unit)? = null,
    /** true = dark, false = light; null follows the app's own light/dark setting. */
    pinnedDark: Boolean? = null,
) {
    val node = spec.layoutFor(size.width.toFloat(), size.height.toFloat())
    val bare = spec.chrome == "none"
    // The app uses a fixed Nothing palette, but real widgets follow the wallpaper (dynamic color),
    // so preview with the same scheme the home screen would use.
    val context = LocalContext.current
    val dark = pinnedDark ?: isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        pinnedDark == null -> MaterialTheme.colorScheme
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = modifier.size(size.width.dp, size.height.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (bare) Color.Transparent else scheme.surface,
            border = if (bare) null else BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            CompositionLocalProvider(LocalPreviewWidth provides size.width.toFloat()) {
                Box(Modifier.fillMaxSize()) { PreviewNode(node, data, onAction, Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun PreviewNode(raw: Node, data: Map<String, String>, onAction: ((ActionSpec) -> Unit)?, extra: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val node = SpecRules.resolve(raw, data) ?: return
    val m = node.toPreviewModifier(extra, scheme, onAction)
    when (node.type) {
        "column" -> Column(
            modifier = m,
            verticalArrangement = Arrangement.spacedBy(node.gap.dp, vAlign(node.justify)),
            horizontalAlignment = hAlign(node.align),
        ) {
            node.children.forEach { child ->
                PreviewNode(child, data, onAction, if (child.grow) Modifier.weight(1f) else Modifier)
            }
        }

        "row" -> Row(
            modifier = m,
            horizontalArrangement = Arrangement.spacedBy(node.gap.dp, hAlign(node.justify)),
            verticalAlignment = vAlign(node.align),
        ) {
            node.children.forEach { child ->
                PreviewNode(child, data, onAction, if (child.grow) Modifier.weight(1f) else Modifier)
            }
        }

        "box" -> Box(modifier = m, contentAlignment = boxAlign(node.align)) {
            node.children.forEach { PreviewNode(it, data, onAction) }
        }

        "text" -> Text(
            text = DataSnapshot.textOf(node, data),
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

        "glyph" -> {
            val context = LocalContext.current
            val text = DataSnapshot.textOf(node, data)
            val color = colorOf(node.color, scheme.onSurface, scheme).toArgb()
            val previewWidth = LocalPreviewWidth.current
            val bitmap = remember(text, node, color, previewWidth) {
                if (text.isEmpty()) null
                else DotArt.glyph(
                    context, text, node.font, node.bold, node.size.toFloat(), color, node.textAlign,
                    node.width?.toFloat() ?: (previewWidth - 2f * (node.inset ?: 8)), node.maxLines ?: Int.MAX_VALUE,
                    node.dropcap, node.minSize?.toFloat(),
                )
            }
            if (bitmap != null) {
                val density = LocalDensity.current.density
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = text,
                    modifier = m.then(
                        if (node.width == null) Modifier.size((bitmap.width / density).dp, (bitmap.height / density).dp)
                        else Modifier,
                    ),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        "dotgrid" -> {
            val context = LocalContext.current
            val on = colorOf(node.color, scheme.onSurface, scheme).toArgb()
            val off = node.track?.let { colorOf(it, scheme.outline, scheme).toArgb() } ?: ((on and 0x00FFFFFF) or 0x30000000)
            val accent = colorOf(node.accent, scheme.error, scheme).toArgb()
            val pattern = node.pattern?.let { DataSnapshot.apply(it, data) }
            val fraction = DataSnapshot.apply(node.progress.orEmpty(), data).toFloatOrNull() ?: 0f
            val bitmap = remember(node, pattern, fraction, on, off, accent) {
                DotArt.dotGrid(
                    context, (node.width ?: 120).toFloat(), (node.height ?: 40).toFloat(),
                    node.columns, node.rows, pattern, fraction, on, off, accent,
                )
            }
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = m, contentScale = ContentScale.Fit)
        }

        "clock" -> {
            val color = colorOf(node.color, scheme.onSurface, scheme).toArgb()
            AndroidView(
                factory = { ctx -> ClockViews.inflate(ctx, node, color) },
                modifier = m,
            )
        }

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

private fun Node.toPreviewModifier(extra: Modifier, scheme: ColorScheme, onAction: ((ActionSpec) -> Unit)?): Modifier {
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
    val a = action
    if (onAction != null && a != null && a.type in WidgetRuntime.STATE_OPS) m = m.clickable { onAction(a) }
    return m
}

internal fun colorOf(ref: String?, fallback: Color, s: ColorScheme): Color {
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
        // Adaptive names (n.card, n.ink, ...) pick their light or dark value from the app's own theme.
        else -> NothingPalette.argb(ref, dark = s.background.luminance() < 0.5f)?.let { Color(it) } ?: fallback
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
