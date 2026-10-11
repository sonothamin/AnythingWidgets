package com.sonothamin.anythingwidgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.ui.components.CardShape
import com.sonothamin.anythingwidgets.ui.components.ChevronIcon
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingTile
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.SizeGlyph
import com.sonothamin.anythingwidgets.ui.components.groupShape
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.LargeWidgetReceiver
import com.sonothamin.anythingwidgets.widget.SmallWidgetReceiver
import com.sonothamin.anythingwidgets.widget.WideWidgetReceiver
import com.sonothamin.anythingwidgets.widget.data.SpecStore

/** Dashboard: how many widgets you have, one-tap "add to home screen" per size, and the installed list. */
@Composable
fun LibraryPage(refreshKey: Any? = null, onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current
    val installed = remember(refreshKey) { SpecStore(context).installed() }
    val manager = remember { AppWidgetManager.getInstance(context) }

    fun pin(receiver: Class<*>) {
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { PageHeader("Widgets") }

        item {
            NothingCard(
                shape = CardShape,
                modifier = Modifier.pageMargin(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            ) {
                Text(
                    installed.size.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 96.sp),
                )
                Text(
                    if (installed.isEmpty()) "Nothing installed yet. Head to Explore."
                    else if (installed.size == 1) "widget installed" else "widgets installed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionLabel("Add to home screen") }
        item {
            Row(Modifier.pageMargin(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NothingTile("Small", Modifier.weight(1f), onClick = { pin(SmallWidgetReceiver::class.java) }) {
                    SizeGlyph(14.dp, 14.dp)
                }
                NothingTile("Wide", Modifier.weight(1f), onClick = { pin(WideWidgetReceiver::class.java) }) {
                    SizeGlyph(26.dp, 14.dp)
                }
                NothingTile("Large", Modifier.weight(1f), onClick = { pin(LargeWidgetReceiver::class.java) }) {
                    SizeGlyph(26.dp, 26.dp)
                }
            }
        }
        item {
            Text(
                "You'll pick which widget to show after placing it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        if (installed.isNotEmpty()) {
            item { SectionLabel("Installed") }
            itemsIndexed(installed, key = { _, spec -> spec.id }) { i, spec ->
                NothingItem(
                    title = spec.name,
                    subtitle = spec.description ?: spec.categories.joinToString(" · ").ifEmpty { null },
                    shape = groupShape(i, installed.size),
                    modifier = Modifier.pageMargin().padding(top = if (i > 0) 2.dp else 0.dp),
                    onClick = { onOpen(spec.id) },
                    trailing = { ChevronIcon() },
                )
            }
        }
    }
}
