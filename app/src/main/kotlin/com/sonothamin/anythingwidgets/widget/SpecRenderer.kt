package com.sonothamin.anythingwidgets.widget

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import android.provider.AlarmClock
import android.provider.Settings
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.sonothamin.anythingwidgets.debug.DebugLog
import com.sonothamin.anythingwidgets.widget.spec.ActionSpec
import com.sonothamin.anythingwidgets.widget.spec.Node
import com.sonothamin.anythingwidgets.widget.spec.NothingPalette
import com.sonothamin.anythingwidgets.widget.spec.SpecRules

/** Maps a validated [Node] tree onto Glance composables. Unknown input is never executed. */
@Composable
fun RenderNode(raw: Node, data: Map<String, String>, extra: GlanceModifier = GlanceModifier) {
    // Conditions, bindings and templated colors are resolved first; a hidden node draws nothing.
    val node = SpecRules.resolve(raw, data) ?: return
    val m = node.toModifier(extra, data)
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
            text = DataSnapshot.textOf(node, data),
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

        "glyph" -> {
            val context = LocalContext.current
            val text = DataSnapshot.textOf(node, data)
            if (text.isNotEmpty()) {
                val maxWidth = node.width?.toFloat() ?: (LocalSize.current.width.value - 2f * (node.inset ?: 8))
                val bitmap = DotArt.glyph(
                    context = context,
                    text = text,
                    font = node.font,
                    bold = node.bold,
                    sizeDp = node.size.toFloat(),
                    argb = argbOf(node.color, GlanceTheme.colors.onSurface),
                    align = node.textAlign,
                    maxWidthDp = maxWidth,
                    maxLines = node.maxLines ?: Int.MAX_VALUE,
                    dropcap = node.dropcap,
                    minSizeDp = node.minSize?.toFloat(),
                )
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = text.replace('\n', ' '),
                    modifier = m,
                    contentScale = ContentScale.Fit,
                )
            }
        }

        "dotgrid" -> {
            val context = LocalContext.current
            val on = argbOf(node.color, GlanceTheme.colors.onSurface)
            val bitmap = DotArt.dotGrid(
                context = context,
                widthDp = (node.width ?: 120).toFloat(),
                heightDp = (node.height ?: 40).toFloat(),
                columns = node.columns,
                rows = node.rows,
                pattern = node.pattern?.let { DataSnapshot.apply(it, data) },
                fraction = DataSnapshot.apply(node.progress.orEmpty(), data).toFloatOrNull() ?: 0f,
                on = on,
                off = node.track?.let { argbOf(it, GlanceTheme.colors.outline) } ?: ((on and 0x00FFFFFF) or 0x30000000),
                accent = argbOf(node.accent, GlanceTheme.colors.error),
            )
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = m,
                contentScale = ContentScale.Fit,
            )
        }

        "clock" -> {
            val context = LocalContext.current
            AndroidRemoteViews(
                remoteViews = ClockViews.build(context, node, argbOf(node.color, GlanceTheme.colors.onSurface)),
                modifier = m,
            )
        }

        "progress" -> LinearProgressIndicator(
            progress = DataSnapshot.apply(node.progress.orEmpty(), data).toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
            modifier = m,
            color = colorOf(node.color, GlanceTheme.colors.primary),
            backgroundColor = colorOf(node.track, GlanceTheme.colors.secondaryContainer),
        )
    }
}

@Composable
private fun Node.toModifier(extra: GlanceModifier, data: Map<String, String>): GlanceModifier {
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
    actionOf(action, data)?.let { m = m.clickable(it) }
    return m
}

@Composable
private fun argbOf(ref: String?, fallback: ColorProvider): Int =
    colorOf(ref, fallback).getColor(LocalContext.current).toArgb()

/** Material token ("primary", "onSurface", ...), Nothing palette name ("n.red", ...) or "#RRGGBB" / "#AARRGGBB". */
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
        else -> NothingPalette.argb(ref)?.let { ColorProvider(Color(it)) } ?: fallback
    }
}

/** Opens the app. (Intent-based: the reified actionStartActivity<T>() lives in a different package.) */
@Composable
internal fun openAppAction(): Action =
    actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))

@Composable
private fun actionOf(a: ActionSpec?, data: Map<String, String>): Action? {
    if (a == null) return null
    val context = LocalContext.current
    fun t(s: String?) = DataSnapshot.apply(s.orEmpty(), data)
    val action: Action? = when (a.type) {
        "open_app" -> openAppAction()
        "open_url" -> a.url?.takeIf { it.startsWith("https://") }?.let {
            actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)).addCategory(Intent.CATEGORY_BROWSABLE))
        }
        "refresh" -> actionRunCallback<RefreshAction>()
        "launch_app" -> a.pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }?.let { actionStartActivity(it) }
        "dial" -> t(a.number).trim().takeIf { Regex("[0-9+*#() .-]{1,40}").matches(it) }
            ?.let { actionStartActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(it)))) }
        "email" -> t(a.to).trim().takeIf { Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(it) }?.let {
            actionStartActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(it)))
                    .putExtra(Intent.EXTRA_SUBJECT, t(a.subject)),
            )
        }
        "map" -> t(a.query).trim().takeIf { it.isNotEmpty() }
            ?.let { actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(it)))) }
        "settings" -> settingsIntent(a.screen)?.let { actionStartActivity(it) }
        "timer" -> a.seconds?.let {
            actionStartActivity(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, it)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
            )
        }
        "alarms" -> actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        in WidgetRuntime.STATE_OPS -> {
            val spec = data["spec.id"].orEmpty()
            if (spec.isEmpty()) null
            else {
                val pairs = buildList<ActionParameters.Pair<*>> {
                    add(StateAction.SPEC to spec)
                    add(StateAction.TYPE to a.type)
                    a.target?.let { add(StateAction.TARGET to it) }
                    a.by?.let { add(StateAction.BY to it.toString()) }
                    a.value?.let { add(StateAction.VALUE to it.content) }
                }
                actionRunCallback<StateAction>(actionParametersOf(*pairs.toTypedArray()))
            }
        }
        else -> null
    }
    if (action == null) DebugLog.w(context, "tap", "'${a.type}' on ${data["spec.id"] ?: "?"} has nothing to run (bad or missing fields); taps will do nothing")
    else DebugLog.d(context, "tap", "'${a.type}' attached on ${data["spec.id"] ?: "?"}")
    return action
}

private fun settingsIntent(screen: String?): Intent? = when (screen) {
    "wifi" -> Settings.ACTION_WIFI_SETTINGS
    "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
    "display" -> Settings.ACTION_DISPLAY_SETTINGS
    "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
    "sound" -> Settings.ACTION_SOUND_SETTINGS
    "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
    "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
    "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
    "apps" -> Settings.ACTION_APPLICATION_SETTINGS
    "date" -> Settings.ACTION_DATE_SETTINGS
    "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
    "all" -> Settings.ACTION_SETTINGS
    else -> null
}?.let { Intent(it) }

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
