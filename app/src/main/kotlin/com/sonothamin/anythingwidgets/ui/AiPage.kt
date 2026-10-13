package com.sonothamin.anythingwidgets.ui

import com.sonothamin.anythingwidgets.ui.components.DotLoader
import com.sonothamin.anythingwidgets.ui.components.NothingArt
import com.sonothamin.anythingwidgets.ui.components.NothingStateCard
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.ai.AiClient
import com.sonothamin.anythingwidgets.ai.AiException
import com.sonothamin.anythingwidgets.ai.AiFailure
import com.sonothamin.anythingwidgets.ai.AiKeyStore
import com.sonothamin.anythingwidgets.ai.AiResult
import com.sonothamin.anythingwidgets.ui.components.CardShape
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.WidgetRuntime
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Describe a widget, preview what the model made, tweak it, install it. */
@Composable
fun AiPage(
    refreshKey: Any? = null,
    onOpenSettings: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasKey = remember(refreshKey) { AiKeyStore(context).hasUsableKey() }

    var prompt by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AiResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableStateOf<List<AiFailure>>(emptyList()) }
    var size by rememberSaveable { mutableStateOf(PreviewSize.Wide) }

    fun run(refine: Boolean) {
        val text = prompt.trim()
        if (text.isEmpty() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val current = if (refine) result else null
                val r = AiClient.generate(context, text, current?.spec, current?.raw)
                result = r
                failures = r.failures
                prompt = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                error = e.message
                failures = e.failures
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("AI")

        if (!hasKey) {
            NothingStateCard(
                art = NothingArt.Key,
                title = "Bring your own key",
                message = "Add an API key for Gemini, OpenAI, Claude or a local Ollama server in Settings, then describe the widget you want.",
                modifier = Modifier.pageMargin(),
                actionLabel = "Open Settings",
                onAction = onOpenSettings,
            )
            return@Column
        }

        val current = result
        NothingCard(CardShape, Modifier.pageMargin(), contentPadding = PaddingValues(20.dp)) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !loading,
                label = { Text(if (current == null) "Describe your widget" else "Describe a change") },
                placeholder = {
                    Text(if (current == null) "A dark card with the day of the year as a dot grid" else "Make the text bigger and use red")
                },
            )
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { run(refine = current != null) }, enabled = prompt.isNotBlank() && !loading) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (current == null) "Generate" else "Refine", modifier = Modifier.padding(start = 8.dp))
                }
                if (current != null) {
                    TextButton(onClick = { run(refine = false) }, enabled = prompt.isNotBlank() && !loading) { Text("New instead") }
                }
                if (loading) DotLoader(diameter = 24.dp)
            }
        }

        error?.let { message ->
            NothingCard(CardShape, Modifier.pageMargin().padding(top = 8.dp)) {
                Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                failures.forEach {
                    Text(
                        "${it.keyLabel}: ${it.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (current != null) {
            SectionLabel("Preview")
            Row(Modifier.pageMargin().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewSize.entries.forEach { s ->
                    FilterChip(selected = size == s, onClick = { size = s }, label = { Text(s.label) })
                }
            }
            Box(Modifier.pageMargin().fillMaxWidth(), contentAlignment = Alignment.Center) {
                SpecPreview(current.spec, size, remember(current, refreshKey) { WidgetRuntime.data(context, current.spec) })
            }
            NothingCard(CardShape, Modifier.pageMargin().padding(top = 16.dp)) {
                Text(
                    current.spec.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                current.spec.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    buildString {
                        append("Made with ${current.usedLabel}")
                        if (current.failures.isNotEmpty()) append(" after ${current.failures.size} other key${if (current.failures.size == 1) "" else "s"} failed")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        scope.launch {
                            runCatching { SpecStore(context).save(current.spec, current.raw, "ai") }
                                .onSuccess {
                                    SpecWidget().updateAll(context)
                                    Toast.makeText(context, "${current.spec.name} installed", Toast.LENGTH_SHORT).show()
                                    onOpen(current.spec.id)
                                    result = null
                                }
                                .onFailure { Toast.makeText(context, "Couldn't install it. Try again.", Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("Install") }
                    OutlinedButton(onClick = { result = null; error = null }) { Text("Discard") }
                }
            }
            Text(
                "AI widgets are checked by the same validator as every other widget. They can only show data, never run code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
