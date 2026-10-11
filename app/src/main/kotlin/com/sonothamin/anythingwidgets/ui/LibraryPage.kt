package com.sonothamin.anythingwidgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ui.theme.GlassCard
import com.sonothamin.anythingwidgets.widget.LargeWidgetReceiver
import com.sonothamin.anythingwidgets.widget.SmallWidgetReceiver
import com.sonothamin.anythingwidgets.widget.WideWidgetReceiver
import com.sonothamin.anythingwidgets.widget.data.SpecStore

/** Installed widgets + one-tap "add to home screen" (pin) for each provider size. */
@Composable
fun LibraryPage() {
    val context = LocalContext.current
    val installed = remember { SpecStore(context).installed() }
    val manager = remember { AppWidgetManager.getInstance(context) }

    fun pin(receiver: Class<*>) {
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Add to home screen", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pin(SmallWidgetReceiver::class.java) }) { Text("Small") }
                    FilledTonalButton(onClick = { pin(WideWidgetReceiver::class.java) }) { Text("Wide") }
                    FilledTonalButton(onClick = { pin(LargeWidgetReceiver::class.java) }) { Text("Large") }
                }
                Text(
                    "You'll pick which widget to show after placing it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (installed.isEmpty()) {
            item { Text("Nothing installed yet. Head to Explore.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(installed, key = { it.id }) { spec ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(spec.name, style = MaterialTheme.typography.titleLarge)
                        spec.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}
