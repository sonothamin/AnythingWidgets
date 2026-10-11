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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.sonothamin.anythingwidgets.MainActivity
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
private fun ConfigScreen(installed: List<WidgetSpec>, onPick: (WidgetSpec) -> Unit, onBrowse: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose a widget", style = MaterialTheme.typography.headlineMedium)
            if (installed.isEmpty()) {
                Text("You haven't installed any widgets yet.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onBrowse) { Text("Open Explore") }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(installed, key = { it.id }) { spec ->
                        Card(onClick = { onPick(spec) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(spec.name, style = MaterialTheme.typography.titleMedium)
                                spec.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}
