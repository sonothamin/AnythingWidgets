package com.sonothamin.anythingwidgets.ui

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
import com.sonothamin.anythingwidgets.widget.DataSnapshot
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
    val data = remember(refreshKey) { DataSnapshot.build(context) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { PageHeader("My Widgets") }

        if (installed.isEmpty()) {
            item {
                NothingCard(CardShape, Modifier.pageMargin(), contentPadding = PaddingValues(24.dp)) {
                    Text(
                        "No widgets yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Install some from Explore, then add them from your home screen's widget picker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    Button(onClick = onExplore) { Text("Browse Explore") }
                }
            }
        } else {
            item { SectionLabel("Installed") }
            items(installed.chunked(2), key = { row -> row.first().id }) { row ->
                Row(
                    Modifier.pageMargin().fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { spec ->
                        WidgetTile(spec, data, Modifier.weight(1f)) { onOpen(spec.id) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A widget shown as it really looks, with its name underneath. */
@Composable
private fun WidgetTile(spec: WidgetSpec, data: Map<String, String>, modifier: Modifier, onClick: () -> Unit) {
    NothingCard(
        CardShape,
        modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpecPreview(spec, PreviewSize.Small, data)
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
