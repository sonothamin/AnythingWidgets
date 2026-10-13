package com.sonothamin.anythingwidgets.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.ui.components.ChevronIcon
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.NothingDialog
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingStack
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.data.RegistrySettings
import com.sonothamin.anythingwidgets.widget.data.RegistrySettings.Source
import com.sonothamin.anythingwidgets.widget.data.WidgetImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(aiEnabled: Boolean = true, onAiEnabledChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var keysOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var source by remember { mutableStateOf(RegistrySettings.source(context)) }
    var custom by remember { mutableStateOf(RegistrySettings.customUrl(context)) }
    var askUrl by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportOutcome?>(null) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importResult = try {
                val result = WidgetImporter.import(context, uri)
                if (result.imported.isNotEmpty()) SpecWidget().updateAll(context)
                ImportOutcome.Done(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportOutcome.Failed(e.message ?: "That file couldn't be read as a widget pack.")
            }
        }
    }

    if (keysOpen) {
        AiKeysPage(onBack = { keysOpen = false })
        return
    }

    if (aboutOpen) {
        AboutPage(onBack = { aboutOpen = false })
        return
    }

    if (advancedOpen) {
        AdvancedPage(onBack = {
            advancedOpen = false
            // Turning HTTP off can switch the source back to the official one.
            source = RegistrySettings.source(context)
            custom = RegistrySettings.customUrl(context)
        })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("Settings")

        SectionLabel("Widget source")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                RepoOption(
                    selected = source == Source.OFFICIAL,
                    title = "sonothamin.dev",
                    subtitle = null,
                    shape = shape,
                    onSelect = {
                        RegistrySettings.useOfficial(context)
                        source = Source.OFFICIAL
                    },
                )
            }
            row { shape ->
                RepoOption(
                    selected = source == Source.CUSTOM,
                    title = "Custom source",
                    subtitle = custom ?: "Not set up yet",
                    shape = shape,
                    onSelect = {
                        val url = custom
                        if (url == null) askUrl = true
                        else {
                            RegistrySettings.useCustom(context, url)
                            source = Source.CUSTOM
                        }
                    },
                    trailing = if (custom != null) {
                        { TextButton(onClick = { askUrl = true }) { Text("Change") } }
                    } else null,
                )
            }
        }

        SectionLabel("Import")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "Import from zip",
                    shape = shape,
                    onClick = {
                        picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    },
                    trailing = { ChevronIcon() },
                )
            }
        }

        SectionLabel("AI")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "Show AI tab",
                    shape = shape,
                    onClick = { onAiEnabledChange(!aiEnabled) },
                    trailing = { Switch(checked = aiEnabled, onCheckedChange = onAiEnabledChange) },
                )
            }
            row { shape ->
                NothingItem(
                    title = "AI keys",
                    shape = shape,
                    onClick = { keysOpen = true },
                    trailing = { ChevronIcon() },
                )
            }
        }

        SectionLabel("Advanced")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "Advanced options",
                    shape = shape,
                    onClick = { advancedOpen = true },
                    trailing = { ChevronIcon() },
                )
            }
        }

        SectionLabel("About")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "About AnythingWidgets",
                    shape = shape,
                    onClick = { aboutOpen = true },
                    trailing = { ChevronIcon() },
                )
            }
        }
    }

    importResult?.let { ImportResultDialog(it) { importResult = null } }

    if (askUrl) {
        CustomRepoDialog(
            initial = custom.orEmpty(),
            allowHttp = RegistrySettings.allowHttp(context),
            onDismiss = { askUrl = false },
            onSave = { url ->
                RegistrySettings.useCustom(context, url)
                custom = url
                source = Source.CUSTOM
                askUrl = false
            },
        )
    }
}

@Composable
private fun RepoOption(
    selected: Boolean,
    title: String,
    subtitle: String?,
    shape: Shape,
    onSelect: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    NothingCard(shape, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun CustomRepoDialog(initial: String, allowHttp: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var showError by remember { mutableStateOf(false) }

    NothingDialog(
        title = "Custom source",
        onDismiss = onDismiss,
        confirmText = "Save",
        dismissText = "Cancel",
        onConfirm = {
            val url = RegistrySettings.normalize(text, allowHttp)
            if (url != null) onSave(url) else showError = true
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Link to the folder that holds your index.json.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; showError = false },
                singleLine = true,
                label = { Text("Source URL") },
                placeholder = { Text(if (allowHttp) "http://192.168.1.10/widgets/" else "https://example.com/widgets/") },
                isError = showError,
                supportingText = {
                    if (showError) {
                        Text(
                            when {
                                allowHttp -> "That needs to be a valid http:// or https:// link."
                                text.trim().startsWith("http://", ignoreCase = true) ->
                                    "Plain http:// links are off. You can allow them under Advanced options."
                                else -> "That needs to be a valid https:// link."
                            },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
    }
}

private sealed interface ImportOutcome {
    data class Done(val result: WidgetImporter.Result) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}

@Composable
private fun ImportResultDialog(outcome: ImportOutcome, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NothingDialog(
        title = if (outcome is ImportOutcome.Failed) "Couldn't import" else "Widgets imported",
        onDismiss = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (outcome) {
                is ImportOutcome.Failed -> Text(outcome.message, style = MaterialTheme.typography.bodyMedium)
                is ImportOutcome.Done -> {
                    val r = outcome.result
                    Text(
                        if (r.imported.isEmpty()) "Nothing new was added."
                        else "${r.imported.size} widget${if (r.imported.size == 1) "" else "s"} added.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    r.imported.forEach {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text("v${it.version}", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        }
                    }
                    if (r.skipped.isNotEmpty()) {
                        Text(
                            "Skipped (${r.skipped.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        r.skipped.forEach { (file, why) ->
                            Column {
                                Text(file, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                                Text(why, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
