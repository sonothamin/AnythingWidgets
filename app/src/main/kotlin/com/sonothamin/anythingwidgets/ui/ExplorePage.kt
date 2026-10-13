package com.sonothamin.anythingwidgets.ui

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.ui.components.CardShape
import com.sonothamin.anythingwidgets.ui.components.DotLoader
import com.sonothamin.anythingwidgets.ui.components.NothingArt
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.NothingEmptyState
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.WidgetRuntime
import com.sonothamin.anythingwidgets.widget.data.Registry
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.spec.IndexEntry
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI

private sealed interface ExploreState {
    data object Loading : ExploreState
    data object Failed : ExploreState
    data class Ready(val widgets: List<IndexEntry>) : ExploreState
}

/** What the corner dot of a tile says about the widget. */
private enum class TileStatus { None, Installed, Update }

/** Bento rhythm: every fifth tile spans the full width, the rest sit two to a row. */
private fun isWide(index: Int) = index % 5 == 0

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
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    // Specs fetched only to draw previews of widgets that aren't installed yet.
    val previews = remember { mutableStateMapOf<String, WidgetSpec>() }
    val previewFailed = remember { mutableStateSetOf<String>() }

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

    fun install(entry: IndexEntry) {
        scope.launch {
            busy = busy + entry.id
            try {
                val spec = registry.install(entry)
                installed = installed + (spec.id to spec.version)
                SpecWidget.redrawAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, "Couldn't install ${entry.name}. Try again.", Toast.LENGTH_SHORT).show()
            } finally {
                busy = busy - entry.id
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("Explore")

        when (val s = state) {
            ExploreState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                DotLoader()
            }

            ExploreState.Failed -> Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 120.dp), Alignment.Center) {
                NothingEmptyState(
                    art = NothingArt.Offline,
                    title = "No signal",
                    message = "Check your connection and try again.",
                    actionLabel = "Retry",
                    onAction = { load() },
                )
            }

            is ExploreState.Ready -> {
                BentoGrid(Modifier.weight(1f)) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SourceHeader(registry.baseUrl) }
                    itemsIndexed(
                        s.widgets,
                        key = { _, e -> e.id },
                        span = { i, _ -> GridItemSpan(if (isWide(i)) maxLineSpan else 1) },
                    ) { i, entry ->
                        val localVersion = installed[entry.id]
                        val localSpec = remember(localVersion) { if (localVersion != null) store.load(entry.id) else null }
                        val key = "${entry.id}@${entry.version}"
                        if (localSpec == null) {
                            LaunchedEffect(key) {
                                if (previews[key] == null) {
                                    try {
                                        previews[key] = registry.fetchSpec(entry).spec
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        previewFailed += key
                                    }
                                }
                            }
                        }
                        BentoTile(
                            title = entry.name,
                            subtitle = entry.categories.firstOrNull(),
                            spec = localSpec ?: previews[key],
                            previewFailed = key in previewFailed,
                            wide = isWide(i),
                            status = when {
                                localVersion == null -> TileStatus.None
                                localVersion < entry.version -> TileStatus.Update
                                else -> TileStatus.Installed
                            },
                            refreshKey = refreshKey,
                            onClick = { selectedId = entry.id },
                        )
                    }
                    val registryIds = s.widgets.map { it.id }.toSet()
                    onDevice(store.installed().filter { it.id !in registryIds }, refreshKey, onOpen)
                }

                s.widgets.firstOrNull { it.id == selectedId }?.let { entry ->
                    val localVersion = installed[entry.id]
                    WidgetSheet(
                        entry = entry,
                        localVersion = localVersion,
                        busy = entry.id in busy,
                        onInstall = { install(entry) },
                        onDetails = {
                            selectedId = null
                            onOpen(entry.id)
                        },
                        onDismiss = { selectedId = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoGrid(modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Where the widgets come from; plain http gets a red tag so it's never a surprise. */
@Composable
private fun SourceHeader(baseUrl: String) {
    val uri = remember(baseUrl) { runCatching { URI(baseUrl) }.getOrNull() }
    val host = uri?.host?.let { h -> if (uri.port != -1) "$h:${uri.port}" else h } ?: "registry"
    val insecure = baseUrl.startsWith("http://", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "From $host",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (insecure) Tag("HTTP", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun GridLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** A widget as it really looks, with its name underneath and a status dot in the corner. */
@Composable
private fun BentoTile(
    title: String,
    subtitle: String?,
    spec: WidgetSpec?,
    previewFailed: Boolean,
    wide: Boolean,
    status: TileStatus,
    refreshKey: Any?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    NothingCard(
        CardShape,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
            when {
                spec != null -> SpecPreview(
                    spec,
                    if (wide) PreviewSize.Wide else PreviewSize.Small,
                    remember(spec, refreshKey) { WidgetRuntime.data(context, spec) },
                )
                previewFailed -> Text(
                    "Preview unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> DotLoader(diameter = 28.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatusDot(status)
        }
    }
}

/** White dot: installed. Blinking red dot: an update is waiting. */
@Composable
private fun StatusDot(status: TileStatus) {
    if (status == TileStatus.None) return
    val scheme = MaterialTheme.colorScheme
    val blink = rememberInfiniteTransition(label = "update-dot").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    val update = status == TileStatus.Update
    Box(
        Modifier
            .padding(start = 12.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background((if (update) scheme.tertiary else scheme.onSurface).copy(alpha = if (update) blink.value else 0.9f))
            .semantics { contentDescription = if (update) "Update available" else "Installed" },
    )
}

/** What you can do with a widget: install or update it, or open its details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSheet(
    entry: IndexEntry,
    localVersion: Int?,
    busy: Boolean,
    onInstall: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val upToDate = localVersion != null && localVersion >= entry.version
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(entry.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            entry.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entry.categories.take(3).forEach { Tag(it) }
                Text(
                    listOfNotNull(entry.author, "v${entry.version}").joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onInstall,
                    enabled = !busy && !upToDate,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(
                        when {
                            busy -> if (localVersion == null) "Installing…" else "Updating…"
                            upToDate -> "Installed"
                            localVersion == null -> "Install"
                            else -> "Update"
                        },
                    )
                }
                OutlinedButton(onClick = onDetails, modifier = Modifier.weight(1f).height(52.dp)) { Text("Details") }
            }
        }
    }
}

/** Widgets that live on this device but aren't in the registry (imported or from another source). */
private fun LazyGridScope.onDevice(specs: List<WidgetSpec>, refreshKey: Any?, onOpen: (String) -> Unit) {
    if (specs.isEmpty()) return
    item(key = "on-device-label", span = { GridItemSpan(maxLineSpan) }) { GridLabel("On this device") }
    itemsIndexed(
        specs,
        key = { _, spec -> "local-${spec.id}" },
        span = { i, _ -> GridItemSpan(if (isWide(i)) maxLineSpan else 1) },
    ) { i, spec ->
        BentoTile(
            title = spec.name,
            subtitle = spec.categories.firstOrNull(),
            spec = spec,
            previewFailed = false,
            wide = isWide(i),
            status = TileStatus.Installed,
            refreshKey = refreshKey,
            onClick = { onOpen(spec.id) },
        )
    }
}
