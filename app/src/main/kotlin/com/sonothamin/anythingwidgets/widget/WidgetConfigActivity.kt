package com.sonothamin.anythingwidgets.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.sonothamin.anythingwidgets.MainActivity
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.groupShape
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.ui.theme.AnythingWidgetsTheme
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.launch

/** Launched by the home screen when a widget is added or reconfigured. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out must cancel placement.
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        val installed = SpecStore(this).installed()
        setContent {
            AnythingWidgetsTheme {
                ConfigScreen(
                    installed = installed,
                    onPick = { choose(appWidgetId, it) },
                    onBrowse = { startActivity(Intent(this, MainActivity::class.java)) },
                    onBack = { finish() },
                )
            }
        }
    }

    private fun choose(appWidgetId: Int, spec: WidgetSpec) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { it[SpecWidget.KEY_SPEC] = spec.id }
            SpecWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(
    installed: List<WidgetSpec>,
    onPick: (WidgetSpec) -> Unit,
    onBrowse: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { PageHeader("Choose a widget", onBack = onBack) }
            if (installed.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("You haven't installed any widgets yet.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onBrowse) { Text("Open Explore") }
                    }
                }
            } else {
                itemsIndexed(installed, key = { _, spec -> spec.id }) { i, spec ->
                    NothingItem(
                        title = spec.name,
                        subtitle = spec.description,
                        shape = groupShape(i, installed.size),
                        modifier = Modifier.pageMargin().padding(top = if (i > 0) 2.dp else 0.dp),
                        onClick = { onPick(spec) },
                    )
                }
            }
        }
    }
}
