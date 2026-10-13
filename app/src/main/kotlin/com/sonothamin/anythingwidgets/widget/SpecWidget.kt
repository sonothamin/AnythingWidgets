package com.sonothamin.anythingwidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.sonothamin.anythingwidgets.debug.DebugLog
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.ActionSpec
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One Glance widget that renders whichever downloaded spec the instance was configured with. */
class SpecWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Do I/O before composing; each update re-runs this, so data is always fresh.
        val initialId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_SPEC]
        val initialSpec = withContext(Dispatchers.IO) { initialId?.let { SpecStore(context).load(it) } }
        // Feeds only hit the network when the user allowed it and the cached copy is older than its ttl.
        initialSpec?.let { spec -> withTimeoutOrNull(8_000) { WidgetRuntime.refreshFeeds(context, spec) } }
        val data = initialSpec?.let { WidgetRuntime.data(context, it) } ?: DataSnapshot.build(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        DebugLog.d(context, "widget", "Draw #$appWidgetId spec=${initialId ?: "none"}${if (initialId != null && initialSpec == null) " (spec file missing)" else ""}")
        provideContent {
            // Read the choice from live state: a session started before the user picked a widget
            // must still re-render with the new choice instead of the captured (null) spec.
            val liveId = currentState<Preferences>()[KEY_SPEC]
            val spec = if (liveId == initialId) initialSpec
            else remember(liveId) { liveId?.let { SpecStore(context).load(it) } }
            // A spec picked after this session started has no precomputed values yet.
            val liveData = if (spec === initialSpec || spec == null) data else remember(spec) { WidgetRuntime.data(context, spec) }
            GlanceTheme { WidgetContent(spec, liveData, appWidgetId) }
        }
    }

    companion object {
        val KEY_SPEC = stringPreferencesKey("spec_id")
    }
}

@Composable
private fun WidgetContent(spec: WidgetSpec?, data: Map<String, String>, appWidgetId: Int) {
    // Follow the system's widget corner radius on Android 12+ (Google design guidance).
    // Specs with chrome "none" draw their own shape, so no card goes behind them.
    val bare = spec?.chrome == "none"
    val shape = if (bare) {
        GlanceModifier.fillMaxSize()
    } else {
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .let {
                if (Build.VERSION.SDK_INT >= 31) it.cornerRadius(android.R.dimen.system_app_widget_background_radius)
                else it.cornerRadius(16.dp)
            }
    }

    if (spec == null) {
        val context = LocalContext.current
        // Reopen the picker for this exact instance (not the app) so a choice can be made here.
        val pick = actionStartActivity(
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("anythingwidgets://configure/$appWidgetId")),
        )
        Box(
            shape.clickable(pick).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Tap to pick a widget",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }

    val size = LocalSize.current
    val node = spec.layoutFor(size.width.value, size.height.value)
    Box(shape) { RenderNode(node, data, GlanceModifier.fillMaxSize()) }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // A tap on "refresh" also re-fetches this widget's feeds, if the user allowed them.
        val id = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[SpecWidget.KEY_SPEC]
        DebugLog.i(context, "tap", "refresh tapped on ${id ?: "unknown widget"}")
        try {
            id?.let { SpecStore(context).load(it) }?.let { spec ->
                withTimeoutOrNull(8_000) { WidgetRuntime.refreshFeeds(context, spec, force = true) }
            }
            SpecWidget().update(context, glanceId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.e(context, "tap", "refresh failed", e)
            throw e
        }
    }
}

/** Mini-app taps: counters, toggles, pickers. Validated against the spec again here; bad input is ignored. */
class StateAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val specId = parameters[SPEC] ?: return DebugLog.w(context, "tap", "State tap without a spec id; ignored")
        val type = parameters[TYPE] ?: return DebugLog.w(context, "tap", "State tap without a type; ignored")
        DebugLog.i(context, "tap", "'$type' tapped on $specId (target=${parameters[TARGET] ?: "-"})")
        val spec = withContext(Dispatchers.IO) { SpecStore(context).load(specId) }
            ?: return DebugLog.w(context, "tap", "Spec $specId not found; ignored")
        val op = ActionSpec(
            type = type,
            target = parameters[TARGET],
            by = parameters[BY]?.toDoubleOrNull(),
            value = parameters[VALUE]?.let { JsonPrimitive(it) },
        )
        val changed = WidgetRuntime.applyOp(context, spec, op)
        DebugLog.i(context, "tap", if (changed) "'$type' changed state; redrawing" else "'$type' changed nothing (invalid target, type or value)")
        if (changed) SpecWidget().updateAll(context)
    }

    companion object {
        val SPEC = ActionParameters.Key<String>("spec")
        val TYPE = ActionParameters.Key<String>("type")
        val TARGET = ActionParameters.Key<String>("target")
        val BY = ActionParameters.Key<String>("by")
        val VALUE = ActionParameters.Key<String>("value")
    }
}

abstract class BaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpecWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        SyncWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        SyncWorker.cancel(context)
    }
}

class SmallWidgetReceiver : BaseWidgetReceiver()
class WideWidgetReceiver : BaseWidgetReceiver()
class LargeWidgetReceiver : BaseWidgetReceiver()
