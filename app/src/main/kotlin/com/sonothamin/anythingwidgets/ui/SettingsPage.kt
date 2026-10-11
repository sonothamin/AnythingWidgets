package com.sonothamin.anythingwidgets.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ui.theme.GlassCard
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.data.RegistrySettings
import com.sonothamin.anythingwidgets.widget.data.RegistrySettings.Source
import com.sonothamin.anythingwidgets.widget.data.WidgetImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SettingsPage() {
    val context = LocalContext.current
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
                ImportOutcome.Failed(e.message ?: "Import failed")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Widget repository", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            RepoOption(
                selected = source == Source.OFFICIAL,
                title = "sonothamin.dev",
                subtitle = RegistrySettings.OFFICIAL_URL,
                onSelect = {
                    RegistrySettings.useOfficial(context)
                    source = Source.OFFICIAL
                },
            )
            RepoOption(
                selected = source == Source.CUSTOM,
                title = "Custom repository",
                subtitle = custom ?: "Not set",
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
            Text(
                "Widgets are data only, but a repository controls what they show. Only use ones you trust.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text("Import widgets", style = MaterialTheme.typography.titleLarge)
            Text(
                "Add widgets from a .zip containing index.json and a widgets/ folder of <id>.json files. " +
                    "Existing widgets with the same id are replaced.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FilledTonalButton(
                onClick = {
                    picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                },
            ) { Text("Choose zip") }
        }
    }

    importResult?.let { ImportResultDialog(it) { importResult = null } }

    if (askUrl) {
        CustomRepoDialog(
            initial = custom.orEmpty(),
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
    subtitle: String,
    onSelect: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        trailing?.invoke()
    }
}

@Composable
private fun CustomRepoDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the HTTPS URL of the folder that contains index.json.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; showError = false },
                    singleLine = true,
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://example.com/widgets/") },
                    isError = showError,
                    supportingText = { if (showError) Text("Enter a valid https:// URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = RegistrySettings.normalize(text)
                if (url != null) onSave(url) else showError = true
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private sealed interface ImportOutcome {
    data class Done(val result: WidgetImporter.Result) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}

@Composable
private fun ImportResultDialog(outcome: ImportOutcome, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (outcome is ImportOutcome.Failed) "Import failed" else "Import finished") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (outcome) {
                    is ImportOutcome.Failed -> Text(outcome.message)
                    is ImportOutcome.Done -> {
                        val r = outcome.result
                        Text("Imported ${r.imported.size} widget${if (r.imported.size == 1) "" else "s"}.")
                        r.imported.forEach { Text("• ${it.name} (v${it.version})", style = MaterialTheme.typography.bodySmall) }
                        if (r.skipped.isNotEmpty()) {
                            Text("Skipped ${r.skipped.size}:", style = MaterialTheme.typography.labelLarge)
                            r.skipped.forEach { (file, why) ->
                                Text("• $file: $why", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
