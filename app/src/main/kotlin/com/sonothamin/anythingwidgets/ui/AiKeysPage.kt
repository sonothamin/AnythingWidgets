package com.sonothamin.anythingwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ai.AiKey
import com.sonothamin.anythingwidgets.ai.AiKeyStore
import com.sonothamin.anythingwidgets.ai.AiPreset
import com.sonothamin.anythingwidgets.ai.AiProtocol
import com.sonothamin.anythingwidgets.ui.components.NothingDialog
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingStack
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import java.util.UUID

/** Key manager: the top of the list is tried first, and a failing key falls through to the next. */
@Composable
fun AiKeysPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AiKeyStore(context) }
    var keys by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<AiKey?>(null) }
    var isNew by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    fun commit(list: List<AiKey>) {
        keys = list
        store.save(list)
    }

    fun move(index: Int, by: Int) {
        val target = index + by
        if (target !in keys.indices) return
        commit(keys.toMutableList().apply { add(target, removeAt(index)) })
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("AI keys", onBack = onBack)

        Text(
            "Requests go to the first enabled key. If it fails (bad key, quota, offline server, unusable answer), the next one is tried. Drag order is set with the arrows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        SectionLabel("Priority")
        NothingStack(Modifier.pageMargin()) {
            keys.forEachIndexed { i, key ->
                row { shape ->
                    NothingItem(
                        title = "${i + 1}. ${key.label}",
                        subtitle = "${key.preset.label} · ${key.model.ifBlank { "no model" }}" +
                            if (key.apiKey.isBlank() && !key.preset.keyOptional) " · key missing" else "",
                        shape = shape,
                        onClick = { editing = key; isNew = false },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { move(i, -1) }, enabled = i > 0) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Higher priority")
                                }
                                IconButton(onClick = { move(i, 1) }, enabled = i < keys.lastIndex) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Lower priority")
                                }
                                Switch(
                                    checked = key.enabled,
                                    onCheckedChange = { on -> commit(keys.map { if (it.id == key.id) it.copy(enabled = on) else it }) },
                                )
                            }
                        },
                    )
                }
            }
            row { shape ->
                NothingItem(
                    title = "Add a key",
                    subtitle = "Gemini, OpenAI, Claude, Ollama or any OpenAI-compatible server.",
                    shape = shape,
                    onClick = { editing = blankKey(AiPreset.GEMINI); isNew = true },
                    trailing = { Icon(Icons.Rounded.Add, contentDescription = null) },
                )
            }
        }
        Text(
            "Keys are encrypted on this device and only ever sent to the address you set for that key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }

    editing?.let { key ->
        KeyDialog(
            initial = key,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { saved ->
                commit(if (isNew) keys + saved else keys.map { if (it.id == saved.id) saved else it })
                editing = null
            },
            onRemove = {
                commit(keys.filterNot { it.id == key.id })
                editing = null
            },
        )
    }
}

private fun blankKey(preset: AiPreset) = AiKey(
    id = UUID.randomUUID().toString(),
    label = preset.label,
    preset = preset,
    apiKey = "",
    baseUrl = preset.baseUrl,
    model = preset.model,
)

@Composable
private fun KeyDialog(
    initial: AiKey,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (AiKey) -> Unit,
    onRemove: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val preset = draft.preset

    fun pick(p: AiPreset) {
        // Switching provider refreshes the defaults, but keeps a label the user typed themselves.
        val keepLabel = draft.label.isNotBlank() && draft.label != draft.preset.label
        draft = draft.copy(
            preset = p,
            label = if (keepLabel) draft.label else p.label,
            baseUrl = p.baseUrl,
            model = p.model,
        )
        error = null
    }

    NothingDialog(
        title = if (isNew) "Add key" else "Edit key",
        onDismiss = onDismiss,
        confirmText = "Save",
        dismissText = "Cancel",
        onConfirm = {
            val url = draft.baseUrl.trim()
            val okUrl = url.startsWith("https://") || (preset.allowHttp && url.startsWith("http://"))
            error = when {
                draft.label.isBlank() -> "Give it a name."
                !okUrl -> if (preset.allowHttp) "Address must start with http:// or https://" else "Address must start with https://"
                url.removePrefix("https://").removePrefix("http://").isBlank() -> "Address is incomplete."
                draft.model.isBlank() -> "Enter a model name."
                draft.apiKey.isBlank() && !preset.keyOptional -> "This provider needs an API key."
                else -> null
            }
            if (error == null) onSave(draft.copy(label = draft.label.trim(), baseUrl = url, model = draft.model.trim(), apiKey = draft.apiKey.trim()))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiPreset.entries.forEach { p ->
                    FilterChip(selected = preset == p, onClick = { pick(p) }, label = { Text(p.label) })
                }
            }
            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it) },
                singleLine = true,
                label = { Text("Name") },
            )
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { draft = draft.copy(apiKey = it) },
                singleLine = true,
                label = { Text("API key") },
                placeholder = { Text(preset.keyHint) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(
                value = draft.model,
                onValueChange = { draft = draft.copy(model = it) },
                singleLine = true,
                label = { Text("Model") },
            )
            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { draft = draft.copy(baseUrl = it) },
                singleLine = true,
                label = { Text(if (preset.protocol == AiProtocol.ANTHROPIC) "Address" else "Address (OpenAI-compatible)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            if (preset == AiPreset.OLLAMA) {
                Text(
                    "Use your computer's address on the network, not localhost. Ollama must listen on it (OLLAMA_HOST=0.0.0.0).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (preset.protocol == AiProtocol.OPENAI_COMPAT) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("JSON mode", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("Ask for strict JSON output. Turn off if the server rejects it.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = draft.jsonMode, onCheckedChange = { draft = draft.copy(jsonMode = it) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (!isNew) {
                TextButton(onClick = onRemove) { Text("Remove key", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
