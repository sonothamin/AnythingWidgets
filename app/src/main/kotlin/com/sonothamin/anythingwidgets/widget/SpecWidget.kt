package com.sonothamin.anythingwidgets.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.sonothamin.anythingwidgets.MainActivity
import com.sonothamin.anythingwidgets.widget.data.SpecStore
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
        val specId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_SPEC]
        val spec = withContext(Dispatchers.IO) { specId?.let { SpecStore(context).load(it) } }
        val data = DataSnapshot.build(context)
        provideContent { GlanceTheme { WidgetContent(spec, data) } }
    }

    companion object {
        val KEY_SPEC = stringPreferencesKey("spec_id")
    }
}

@Composable
private fun WidgetContent(spec: WidgetSpec?, data: Map<String, String>) {
    // Follow the system's widget corner radius on Android 12+ (Google design guidance).
    val shape = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(GlanceTheme.colors.widgetBackground)
        .let {
            if (Build.VERSION.SDK_INT >= 31) it.cornerRadius(android.R.dimen.system_app_widget_background_radius)
            else it.cornerRadius(16.dp)
        }

    if (spec == null) {
        Box(
            shape.clickable(actionStartActivity<MainActivity>()).padding(12.dp),
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

    val compact = LocalSize.current.width < 180.dp
    val node = if (compact) spec.compact ?: spec.root else spec.root
    Box(shape) { RenderNode(node, data, GlanceModifier.fillMaxSize()) }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SpecWidget().update(context, glanceId)
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
