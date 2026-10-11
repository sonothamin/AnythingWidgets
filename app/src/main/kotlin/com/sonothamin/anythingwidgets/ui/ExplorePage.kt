package com.sonothamin.anythingwidgets.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.data.Registry
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.IndexEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface ExploreState {
    data object Loading : ExploreState
    data object Failed : ExploreState
    data class Ready(val widgets: List<IndexEntry>) : ExploreState
}

@Composable
fun ExplorePage() {
    val context = LocalContext.current.applicationContext
    val registry = remember { Registry(context) }
    val store = remember { SpecStore(context) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<ExploreState>(ExploreState.Loading) }
    var installed by remember { mutableStateOf(store.installed().associate { it.id to it.version }) }
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

    when (val s = state) {
        ExploreState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        ExploreState.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Couldn't reach the widget registry.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { load() }) { Text("Retry") }
        }

        is ExploreState.Ready -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(s.widgets, key = { it.id }) { entry ->
                val local = installed[entry.id]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            listOfNotNull(entry.author, "v${entry.version}").joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        entry.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Spacer(Modifier.height(4.dp))
                        val upToDate = local != null && local >= entry.version
                        Button(
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
                    }
                }
            }
        }
    }
}
