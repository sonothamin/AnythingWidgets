package com.sonothamin.anythingwidgets.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.groupShape
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.data.Registry
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.IndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI

private sealed interface ExploreState {
    data object Loading : ExploreState
    data object Failed : ExploreState
    data class Ready(val widgets: List<IndexEntry>) : ExploreState
}

@Composable
fun ExplorePage(refreshKey: Any? = null, onOpen: (String) -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val registry = remember { Registry(context) }
    val store = remember { SpecStore(context) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<ExploreState>(ExploreState.Loading) }
    var installed by remember(refreshKey) {
        mutableStateOf(
            store.installed().filter { store.sourceOf(it.id) == registry.baseUrl }.associate { it.id to it.version },
        )
    }
    var busy by remember { mutableStateOf(emptySet<String>()) }

    fun load() {
        state = ExploreState.Loading
        scope.launch {
            state = try {
                ExploreState.Ready(registry.fetchIndex().widgets)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExploreState.Failed
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Explore")

        when (val s = state) {
            ExploreState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                CircularProgressIndicator()
            }

            ExploreState.Failed -> Column(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Couldn't reach the widget registry.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { load() }) { Text("Retry") }
            }

            is ExploreState.Ready -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item {
                    val host = runCatching { URI(registry.baseUrl).host }.getOrNull() ?: "registry"
                    SectionLabel("From $host")
                }
                itemsIndexed(s.widgets, key = { _, e -> e.id }) { i, entry ->
                    val local = installed[entry.id]
                    val upToDate = local != null && local >= entry.version
                    NothingItem(
                        title = entry.name,
                        subtitle = buildString {
                            append(listOfNotNull(entry.author, "v${entry.version}").joinToString(" · "))
                            entry.description?.let { append("\n").append(it) }
                            if (entry.categories.isNotEmpty()) append("\n").append(entry.categories.joinToString(" · "))
                        },
                        shape = groupShape(i, s.widgets.size),
                        modifier = Modifier.pageMargin().padding(top = if (i > 0) 2.dp else 0.dp),
                        onClick = { onOpen(entry.id) },
                        trailing = {
                            FilledTonalButton(
                                enabled = !upToDate && entry.id !in busy,
                                onClick = {
                                    scope.launch {
                                        busy = busy + entry.id
                                        try {
                                            val spec = registry.install(entry)
                                            installed = installed + (spec.id to spec.version)
                                            SpecWidget().updateAll(context)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Couldn't install ${entry.name}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            busy = busy - entry.id
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    when {
                                        entry.id in busy -> "Installing…"
                                        local == null -> "Install"
                                        upToDate -> "Installed"
                                        else -> "Update"
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
