package com.sonothamin.anythingwidgets.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.ui.components.CardShape
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingStack
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.DataSnapshot
import com.sonothamin.anythingwidgets.widget.LargeWidgetReceiver
import com.sonothamin.anythingwidgets.widget.SmallWidgetReceiver
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.WideWidgetReceiver
import com.sonothamin.anythingwidgets.widget.data.Registry
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import com.sonothamin.anythingwidgets.widget.data.WidgetImporter
import com.sonothamin.anythingwidgets.widget.data.WidgetPrefs
import com.sonothamin.anythingwidgets.widget.spec.IndexEntry
import com.sonothamin.anythingwidgets.widget.spec.SpecInfo
import com.sonothamin.anythingwidgets.widget.spec.WidgetSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Everything about one widget: live preview, metadata, per-widget settings.
 * Works for installed widgets and for registry widgets that aren't installed yet (previewed from a
 * verified download, nothing is stored until the user taps Install).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WidgetDetailsPage(id: String, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val registry = remember { Registry(context) }
    val store = remember { SpecStore(context) }
    val scope = rememberCoroutineScope()

    var installed by remember { mutableStateOf(store.load(id)) }
    var entry by remember { mutableStateOf<IndexEntry?>(null) }
    var remote by remember { mutableStateOf<Registry.Fetched?>(null) }
    var loading by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var autoUpdate by remember { mutableStateOf(WidgetPrefs.autoUpdate(context, id)) }
    var confirmRemove by remember { mutableStateOf(false) }
    var sizeIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(id) {
        loading = true
        offline = false
        try {
            val found = registry.fetchIndex().widgets.firstOrNull { it.id == id }
            val local = installed
            // An installed widget only tracks the repository it came from.
            val relevant = local == null || store.sourceOf(id) == registry.baseUrl
            entry = if (relevant) found else null
            if (local == null && found != null) remote = registry.fetchSpec(found)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            offline = true
        }
        loading = false
    }

    val spec: WidgetSpec? = installed ?: remote?.spec
    val update: IndexEntry? = installed?.let { local -> entry?.takeIf { it.version > local.version } }
    val source = remember(installed) { store.sourceOf(id) }
    val managed = installed != null && source == registry.baseUrl
    val data = remember { DataSnapshot.build(context) }

    fun install(e: IndexEntry) {
        scope.launch {
            busy = true
            try {
                val s = registry.install(e)
                installed = s
                remote = null
                SpecWidget().updateAll(context)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                Toast.makeText(context, "Couldn't install ${e.name}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun pin(receiver: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, receiver), null, null)
        } else {
            Toast.makeText(context, "Your launcher can't add widgets from here", Toast.LENGTH_SHORT).show()
        }
    }

    val sizes = PreviewSize.entries
    val size = sizes[sizeIndex.coerceIn(0, sizes.lastIndex)]
    val addedAt = remember(installed) { installed?.let { store.addedAt(it.id) } }
    val bytes: Long? = if (installed != null) store.sizeOf(id) else remote?.sizeBytes?.toLong()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 32.dp),
    ) {
        PageHeader(spec?.name ?: "Widget", onBack = onBack)

        if (spec == null) {
            Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        if (offline) "Couldn't reach the widget registry." else "This widget isn't available.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        } else {
            val dataUsed = SpecInfo.dataUsed(spec)
            val actions = SpecInfo.actions(spec)

            // Author, version, description
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(spec.author, "v${spec.version}").joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                spec.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }

            // Install / update
            when {
                installed == null -> Button(
                    onClick = { entry?.let(::install) },
                    enabled = entry != null && !busy,
                    modifier = Modifier.pageMargin().padding(top = 24.dp).fillMaxWidth().height(56.dp),
                ) { Text(if (busy) "Installing…" else "Install") }

                update != null -> Button(
                    onClick = { install(update) },
                    enabled = !busy,
                    modifier = Modifier.pageMargin().padding(top = 24.dp).fillMaxWidth().height(56.dp),
                ) { Text(if (busy) "Updating…" else "Update to v${update.version}") }
            }

            // Preview
            SectionLabel("Preview")
            NothingCard(CardShape, Modifier.pageMargin()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizes.forEachIndexed { i, s ->
                        FilterChip(
                            selected = i == sizeIndex,
                            onClick = { sizeIndex = i },
                            label = { Text(s.label) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SpecPreview(spec, size, data)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "${size.width} × ${size.height} dp · uses this device's live data. " +
                        "Your launcher may size widgets a little differently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Categories and tags
            if (spec.categories.isNotEmpty() || spec.tags.isNotEmpty()) {
                SectionLabel("Categories & tags")
                NothingCard(CardShape, Modifier.pageMargin()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        spec.categories.forEach {
                            Pill(it, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        }
                        spec.tags.forEach {
                            Pill("#$it", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Details
            SectionLabel("Details")
            val rows = buildList {
                add("Created by" to (spec.author ?: "Unknown"))
                add("Created" to (spec.created?.let(::formatIsoDate) ?: "Unknown"))
                add("Added" to (addedAt?.let(::formatMillis) ?: "Not added yet"))
                add(
                    "Version" to buildString {
                        append("v${spec.version}")
                        if (update != null) append(" (v${update.version} available)")
                    },
                )
                add("File size" to (bytes?.let(::formatSize) ?: "Unknown"))
                add("Layouts" to if (SpecInfo.hasCompactLayout(spec)) "Full and compact (adapts to size)" else "Single layout")
                add("Data used" to (dataUsed.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "None (static)"))
                add("Tapping" to (actions.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Does nothing"))
                add(
                    "Source" to when {
                        installed == null -> hostOf(registry.baseUrl)
                        source == WidgetImporter.SOURCE -> "Imported from zip"
                        source == null -> "Unknown"
                        else -> hostOf(source)
                    },
                )
                add("ID" to spec.id)
            }
            NothingStack(Modifier.pageMargin()) {
                rows.forEach { (label, value) ->
                    row { shape -> NothingItem(title = label, subtitle = value, shape = shape) }
                }
            }

            // Per-widget settings (only once it's on the device)
            if (installed != null) {
                SectionLabel("Settings")
                NothingStack(Modifier.pageMargin()) {
                    row { shape ->
                        NothingItem(
                            title = "Update automatically",
                            subtitle = if (managed) "Install new versions from the registry as they're published."
                            else "Only widgets installed from your current repository update automatically.",
                            shape = shape,
                            onClick = if (managed) {
                                {
                                    autoUpdate = !autoUpdate
                                    WidgetPrefs.setAutoUpdate(context, id, autoUpdate)
                                }
                            } else null,
                            trailing = {
                                Switch(checked = managed && autoUpdate, onCheckedChange = null, enabled = managed)
                            },
                        )
                    }
                    row { shape ->
                        NothingCard(shape) {
                            Text(
                                "Add to home screen",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                            )
                            Text(
                                "You'll pick which widget to show after placing it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { pin(SmallWidgetReceiver::class.java) }) { Text("Small") }
                                FilledTonalButton(onClick = { pin(WideWidgetReceiver::class.java) }) { Text("Wide") }
                                FilledTonalButton(onClick = { pin(LargeWidgetReceiver::class.java) }) { Text("Large") }
                            }
                        }
                    }
                    row { shape ->
                        NothingItem(
                            title = "Refresh widgets",
                            subtitle = "Redraw every widget on your home screen.",
                            shape = shape,
                            onClick = {
                                scope.launch {
                                    SpecWidget().updateAll(context)
                                    Toast.makeText(context, "Widgets refreshed", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                    row { shape ->
                        NothingItem(
                            title = "Remove",
                            titleColor = MaterialTheme.colorScheme.error,
                            subtitle = "Uninstall this widget.",
                            shape = shape,
                            onClick = { confirmRemove = true },
                        )
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${spec?.name ?: "widget"}?") },
            text = {
                Text("Copies already on your home screen will ask you to pick another widget. You can install it again from Explore.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        store.delete(id)
                        WidgetPrefs.clear(context, id)
                        SpecWidget().updateAll(context)
                        onBack()
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Pill(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun formatIsoDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)

private fun formatMillis(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

private fun formatSize(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "%.1f KB".format(Locale.getDefault(), bytes / 1024.0)

private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
