package com.sonothamin.anythingwidgets.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sonothamin.anythingwidgets.MainActivity
import com.sonothamin.anythingwidgets.widget.spec.ActionSpec
import com.sonothamin.anythingwidgets.widget.spec.Node

/** Maps a validated [Node] tree onto Glance composables. Unknown input is never executed. */
@Composable
fun RenderNode(node: Node, data: Map<String, String>, extra: GlanceModifier = GlanceModifier) {
    val m = node.toModifier(extra)
    when (node.type) {
        "column" -> Column(
            modifier = m,
            verticalAlignment = vertical(node.justify),
            horizontalAlignment = horizontal(node.align),
        ) {
            node.children.forEachIndexed { i, child ->
                if (i > 0 && node.gap > 0) Spacer(GlanceModifier.height(node.gap.dp))
                RenderNode(child, data, if (child.grow) GlanceModifier.defaultWeight() else GlanceModifier)
            }
        }

        "row" -> Row(
            modifier = m,
            horizontalAlignment = horizontal(node.justify),
            verticalAlignment = vertical(node.align),
        ) {
            node.children.forEachIndexed { i, child ->
                if (i > 0 && node.gap > 0) Spacer(GlanceModifier.width(node.gap.dp))
                RenderNode(child, data, if (child.grow) GlanceModifier.defaultWeight() else GlanceModifier)
            }
        }

        "box" -> Box(modifier = m, contentAlignment = boxAlignment(node.align)) {
            node.children.forEach { RenderNode(it, data) }
        }

        "text" -> Text(
            text = DataSnapshot.apply(node.text.orEmpty(), data),
            modifier = m,
            style = TextStyle(
                color = colorOf(node.color, GlanceTheme.colors.onSurface),
                fontSize = node.size.sp,
                fontWeight = if (node.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = when (node.textAlign) {
                    "center" -> TextAlign.Center
                    "end" -> TextAlign.End
                    else -> TextAlign.Start
                },
            ),
            maxLines = node.maxLines ?: Int.MAX_VALUE,
        )

        "spacer" -> Spacer(m)

        "progress" -> LinearProgressIndicator(
            progress = DataSnapshot.apply(node.progress.orEmpty(), data).toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
            modifier = m,
            color = colorOf(node.color, GlanceTheme.colors.primary),
            backgroundColor = colorOf(node.track, GlanceTheme.colors.secondaryContainer),
        )
    }
}

@Composable
private fun Node.toModifier(extra: GlanceModifier): GlanceModifier {
    var m = extra
    when (fill) {
        "width" -> m = m.fillMaxWidth()
        "height" -> m = m.fillMaxHeight()
        "both" -> m = m.fillMaxSize()
    }
    width?.let { m = m.width(it.dp) }
    height?.let { m = m.height(it.dp) }
    // Order matters: background -> corner radius -> padding.
    background?.let { m = m.background(colorOf(it, GlanceTheme.colors.surface)) }
    if (radius > 0) m = m.cornerRadius(radius.dp)
    if (padding > 0) m = m.padding(padding.dp)
    actionOf(action)?.let { m = m.clickable(it) }
    return m
}

/** Material token ("primary", "onSurface", ...) or "#RRGGBB" / "#AARRGGBB". */
@Composable
private fun colorOf(ref: String?, fallback: ColorProvider): ColorProvider {
    if (ref == null) return fallback
    val c = GlanceTheme.colors
    return when (ref) {
        "primary" -> c.primary
        "onPrimary" -> c.onPrimary
        "primaryContainer" -> c.primaryContainer
        "onPrimaryContainer" -> c.onPrimaryContainer
        "secondary" -> c.secondary
        "onSecondary" -> c.onSecondary
        "secondaryContainer" -> c.secondaryContainer
        "onSecondaryContainer" -> c.onSecondaryContainer
        "tertiary" -> c.tertiary
        "onTertiary" -> c.onTertiary
        "tertiaryContainer" -> c.tertiaryContainer
        "onTertiaryContainer" -> c.onTertiaryContainer
        "surface" -> c.surface
        "onSurface" -> c.onSurface
        "surfaceVariant" -> c.surfaceVariant
        "onSurfaceVariant" -> c.onSurfaceVariant
        "background" -> c.background
        "onBackground" -> c.onBackground
        "error" -> c.error
        "outline" -> c.outline
        else -> runCatching { ColorProvider(Color(android.graphics.Color.parseColor(ref))) }.getOrDefault(fallback)
    }
}

/** Opens the app. (Intent-based: the reified actionStartActivity<T>() lives in a different package.) */
@Composable
internal fun openAppAction(): Action =
    actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))

@Composable
private fun actionOf(a: ActionSpec?): Action? = when (a?.type) {
    "open_app" -> openAppAction()
    "open_url" -> a.url?.takeIf { it.startsWith("https://") }?.let {
        actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addCategory(Intent.CATEGORY_BROWSABLE))
    }
    "refresh" -> actionRunCallback<RefreshAction>()
    else -> null
}

private fun horizontal(s: String?) = when (s) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun vertical(s: String?) = when (s) {
    "center" -> Alignment.CenterVertically
    "end" -> Alignment.Bottom
    else -> Alignment.Top
}

private fun boxAlignment(s: String?) = when (s) {
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
