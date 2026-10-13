package com.sonothamin.anythingwidgets.ui

import com.sonothamin.anythingwidgets.ui.components.NothingArt
import com.sonothamin.anythingwidgets.ui.components.NothingEmptyState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.ui.components.CardShape
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.WidgetRuntime
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec

/** Dashboard: your installed widgets as a gallery of live previews. */
@Composable
fun LibraryPage(
    refreshKey: Any? = null,
    onOpen: (String) -> Unit = {},
    onExplore: () -> Unit = {},
) {
    val context = LocalContext.current
    val installed = remember(refreshKey) { SpecStore(context).installed() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { PageHeader("My Widgets") }

        if (installed.isEmpty()) {
            item {
                NothingEmptyState(
                    art = NothingArt.Empty,
                    title = "Nothing here. Yet.",
                    message = "Install a widget from Explore.",
                    modifier = Modifier.fillParentMaxHeight(0.6f),
                    actionLabel = "Browse Explore",
                    onAction = onExplore,
                )
            }
        } else {
            item { SectionLabel("Installed") }
            items(installed.chunked(2), key = { row -> row.first().id }) { row ->
                Row(
                    Modifier.pageMargin().fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { spec ->
                        WidgetTile(spec, refreshKey, Modifier.weight(1f)) { onOpen(spec.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A widget shown as it really looks, with its name underneath. */
@Composable
private fun WidgetTile(spec: WidgetSpec, refreshKey: Any?, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    NothingCard(
        CardShape,
        modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpecPreview(spec, PreviewSize.Small, remember(spec, refreshKey) { WidgetRuntime.data(context, spec) })
        }
        Spacer(Modifier.height(12.dp))
        Text(
            spec.name,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        spec.categories.firstOrNull()?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
