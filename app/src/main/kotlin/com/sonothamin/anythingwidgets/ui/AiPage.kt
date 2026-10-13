package com.sonothamin.anythingwidgets.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.sonothamin.anythingwidgets.ai.AiClient
import com.sonothamin.anythingwidgets.ai.AiException
import com.sonothamin.anythingwidgets.ai.AiFailure
import com.sonothamin.anythingwidgets.ai.AiKeyStore
import com.sonothamin.anythingwidgets.debug.DebugLog
import com.sonothamin.anythingwidgets.ui.components.DotLoader
import com.sonothamin.anythingwidgets.ui.components.DotMatrix
import com.sonothamin.anythingwidgets.ui.components.GeneratingState
import com.sonothamin.anythingwidgets.ui.components.NothingArt
import com.sonothamin.anythingwidgets.ui.components.NothingEmptyState
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.SpecWidget
import com.sonothamin.anythingwidgets.widget.WidgetRuntime
import com.sonothamin.anythingwidgets.widget.data.SpecStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Starter ideas shown on the empty screen, and the fallback when the model can't be asked for one. */
private val StarterIdeas = listOf(
    "A dot grid showing how much of the year has passed",
    "A tally counter I tap to add one",
    "Battery level as a row of dots that drain",
    "Days left until a date I pick, in big dot lettering",
    "A coin flip that shows heads or tails when tapped",
    "A dark pill with the time and a red dot for my next alarm",
    "A habit tracker with seven dots for the week",
    "A tiny dice roller, tap to roll",
    "A big analog clock with the date underneath",
    "A progress bar for today, midnight to midnight",
    "A quote of the day in serif type on a warm card",
    "A wifi and battery status strip in monospace",
)

/** Describe a widget, watch it get made, step through the iterations, install the one you like. */
@Composable
fun AiPage(
    refreshKey: Any? = null,
    onOpenSettings: () -> Unit = {},
    onOpen: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasKey = remember(refreshKey) { AiKeyStore(context).hasUsableKey() }

    var loading by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<String?>(null) }
    var askedRequest by remember { mutableStateOf("") }
    var ideaBusy by remember { mutableStateOf(false) }
    var lastIdea by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableStateOf<List<AiFailure>>(emptyList()) }
    var size by rememberSaveable { mutableStateOf(PreviewSize.Wide) }
    val starters = remember { StarterIdeas.shuffled().take(3) }

    if (!hasKey) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("AI")
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 120.dp), Alignment.Center) {
                NothingEmptyState(
                    art = NothingArt.Key,
                    title = "Bring your own key",
                    message = "Add an API key in Settings to generate widgets.",
                    actionLabel = "Open Settings",
                    onAction = onOpenSettings,
                )
            }
        }
        return
    }

    val current = AiSession.current

    fun generate() {
        val text = AiSession.prompt.trim()
        if (text.isEmpty() || loading) return
        // Refining continues from whichever version is showing, not necessarily the newest.
        val base = AiSession.current
        loading = true
        error = null
        asking = null
        askedRequest = text
        scope.launch {
            try {
                val r = AiClient.generate(
                    context, text, base?.result?.spec, base?.result?.raw,
                    onAttempt = { k -> asking = "${k.preset.label} · ${k.model.ifBlank { "default model" }}" },
                )
                failures = r.failures
                AiSession.add(AiVersion(r, text))
                AiSession.prompt = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                error = e.message
                failures = e.failures
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
                failures = emptyList()
            } finally {
                loading = false
            }
        }
    }

    fun surprise() {
        if (ideaBusy || loading) return
        ideaBusy = true
        scope.launch {
            try {
                val idea = try {
                    AiClient.idea(context, lastIdea)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DebugLog.w(context, "ai", "Idea request failed, using a built-in one: ${e.message}")
                    StarterIdeas.filter { it != lastIdea }.random()
                }
                lastIdea = idea
                AiSession.prompt = idea
            } finally {
                ideaBusy = false
            }
        }
    }

    fun install(v: AiVersion) {
        scope.launch {
            runCatching { SpecStore(context).save(v.result.spec, v.result.raw, "ai") }
                .onSuccess {
                    SpecWidget().updateAll(context)
                    Toast.makeText(context, "${v.result.spec.name} installed", Toast.LENGTH_SHORT).show()
                    onOpen(v.result.spec.id)
                    AiSession.clear()
                }
                .onFailure { Toast.makeText(context, "Couldn't install it. Try again.", Toast.LENGTH_SHORT).show() }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader("AI")

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> GeneratingState(askedRequest, asking, Modifier.align(Alignment.Center))
                current == null -> AiHero(starters, { AiSession.prompt = it }, Modifier.align(Alignment.Center))
                else -> AiResultView(
                    version = current,
                    size = size,
                    onSize = { size = it },
                    refreshKey = refreshKey,
                    onInstall = { install(current) },
                )
            }
        }

        if (!loading) {
            error?.let { message ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    failures.forEach {
                        Text(
                            "${it.keyLabel}: ${it.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        AiComposer(
            value = AiSession.prompt,
            onValueChange = { AiSession.prompt = it },
            placeholder = if (current == null) "Describe a widget" else "Describe a change to v${AiSession.index + 1}",
            busy = loading,
            ideaBusy = ideaBusy,
            onSend = ::generate,
            onSurprise = ::surprise,
        )
    }
}

// --- empty screen -------------------------------------------------------------------------------

@Composable
private fun AiHero(starters: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DotMatrix(NothingArt.Spark)
        Text(
            "What should it show?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            starters.forEach { idea ->
                Surface(
                    onClick = { onPick(idea) },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        idea,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

// --- result ---------------------------------------------------------------------------------------

@Composable
private fun AiResultView(
    version: AiVersion,
    size: PreviewSize,
    onSize: (PreviewSize) -> Unit,
    refreshKey: Any?,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val versions = AiSession.versions
    val r = version.result
    val spec = r.spec
    val data = remember(version, refreshKey) { WidgetRuntime.data(context, spec) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        DotStage(Modifier.pageMargin().padding(top = 4.dp)) {
            Box(Modifier.padding(vertical = 32.dp)) { SpecPreview(spec, size, data) }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewSize.entries.forEach { s -> ChoicePill(s.label, s == size) { onSize(s) } }
        }

        if (versions.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Versions", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                versions.forEachIndexed { i, _ ->
                    ChoicePill("v${i + 1}", i == AiSession.index) { AiSession.index = i }
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp)) {
            Text(spec.name, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
            spec.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                buildString {
                    append(r.provider)
                    if (r.model.isNotBlank()) append(" · ${r.model}")
                    append(" · ${"%.1f".format(r.millis / 1000.0)}s")
                },
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (r.failures.isNotEmpty()) {
                Text(
                    "${r.failures.size} other key${if (r.failures.size == 1) "" else "s"} failed first",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                "“${version.request}”",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Row(
            Modifier.padding(horizontal = 12.dp).padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onInstall,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onSurface, contentColor = scheme.surface),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(if (versions.size > 1) "Install v${AiSession.index + 1}" else "Install") }
            TextButton(onClick = { AiSession.clear() }) { Text("Start over", color = scheme.onSurfaceVariant) }
        }
    }
}

/** The preview sits on a field of faint dots inside a hairline outline: a stage, not a card. */
@Composable
private fun DotStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val dots = scheme.onSurface.copy(alpha = 0.13f)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .drawBehind {
                val step = 14.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(dots, 1.4.dp.toPx(), Offset(x, y))
                        x += step
                    }
                    y += step
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) scheme.onSurface else Color.Transparent,
        contentColor = if (selected) scheme.surface else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

// --- composer -------------------------------------------------------------------------------------

/**
 * A pill pinned to the bottom of the screen. With nothing typed it offers a dice that asks the
 * model for an idea; once there's text, the round button sends it.
 */
@Composable
private fun AiComposer(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    busy: Boolean,
    ideaBusy: Boolean,
    onSend: () -> Unit,
    onSurprise: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val imeShown = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val canSend = value.isNotBlank() && !busy

    Row(
        Modifier
            .pageMargin()
            .padding(bottom = if (imeShown) 8.dp else 112.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = !busy,
            minLines = 1,
            maxLines = 5,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface, fontSize = 17.sp, lineHeight = 24.sp),
            cursorBrush = SolidColor(scheme.onSurface),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).padding(top = 11.dp, bottom = 13.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (value.isEmpty()) {
            Surface(
                onClick = onSurprise,
                enabled = !busy && !ideaBusy,
                shape = CircleShape,
                color = scheme.surfaceContainerHighest,
                modifier = Modifier.padding(end = 8.dp).size(48.dp),
            ) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    if (ideaBusy) DotLoader(diameter = 22.dp)
                    else Icon(Icons.Rounded.Casino, contentDescription = "Surprise me", tint = scheme.onSurface)
                }
            }
        }
        Surface(
            onClick = onSend,
            enabled = canSend,
            shape = CircleShape,
            color = if (canSend) scheme.onSurface else scheme.surfaceContainerHighest,
            modifier = Modifier.size(48.dp),
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "Generate",
                    tint = if (canSend) scheme.surface else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
