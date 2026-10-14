package com.sonothamin.anythingwidgets.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.ui.components.CardShape
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.NothingDialog
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.StackScope
import com.sonothamin.anythingwidgets.widget.spec.NothingPalette
import com.sonothamin.anythingwidgets.widget.spec.ParamSpec
import com.sonothamin.anythingwidgets.widget.spec.SpecRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** One settings row per declared parameter. [values] holds the current (validated) value of each. */
fun StackScope.paramRows(params: List<ParamSpec>, values: Map<String, String>, onChange: (id: String, value: String) -> Unit) {
    params.forEach { p ->
        row { shape -> ParamRow(p, values[p.id] ?: SpecRules.paramDefault(p), shape) { onChange(p.id, it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ParamRow(p: ParamSpec, value: String, shape: Shape, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    when (p.type) {
        "toggle" -> NothingItem(
            title = p.label,
            subtitle = p.description,
            shape = shape,
            onClick = { onChange((value != "true").toString()) },
            trailing = { Switch(checked = value == "true", onCheckedChange = null) },
        )

        "choice" -> {
            var open by remember { mutableStateOf(false) }
            Box {
                NothingItem(
                    title = p.label,
                    subtitle = p.choices.firstOrNull { it.value == value }?.let { it.label ?: it.value },
                    shape = shape,
                    onClick = { open = true },
                    trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = scheme.onSurfaceVariant) },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    p.choices.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label ?: c.value) }, onClick = { open = false; onChange(c.value) })
                    }
                }
            }
        }

        "number" -> {
            val lo = (p.min ?: 0.0).toFloat()
            val hi = (p.max ?: 100.0).toFloat()
            val step = p.step?.toFloat()
            var drag by remember(value) { mutableFloatStateOf(value.toFloatOrNull() ?: lo) }
            fun snap(v: Float) = if (step != null) (lo + ((v - lo) / step).roundToInt() * step).coerceIn(lo, hi) else v
            NothingCard(shape, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp), color = scheme.onSurface)
                        p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                    }
                    Text(
                        SpecRules.fmt(snap(drag).toDouble()) + (p.unit?.let { " $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = drag,
                    onValueChange = { drag = snap(it) },
                    onValueChangeFinished = { onChange(SpecRules.fmt(snap(drag).toDouble())) },
                    valueRange = lo..hi,
                    steps = if (step != null) (((hi - lo) / step).roundToInt() - 1).coerceAtLeast(0) else 0,
                )
            }
        }

        "text" -> {
            var open by remember { mutableStateOf(false) }
            NothingItem(
                title = p.label,
                subtitle = value.ifEmpty { "Empty" },
                shape = shape,
                onClick = { open = true },
            )
            if (open) {
                var draft by remember { mutableStateOf(value) }
                val max = p.maxLength ?: 60
                NothingDialog(
                    title = p.label,
                    onDismiss = { open = false },
                    confirmText = "Save",
                    dismissText = "Cancel",
                    onConfirm = { onChange(draft.take(max)); open = false },
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(max) },
                        label = { Text(p.label) },
                        supportingText = { Text("${draft.length}/$max") },
                    )
                }
            }
        }

        "color" -> {
            var open by remember { mutableStateOf(false) }
            NothingItem(
                title = p.label,
                subtitle = p.description ?: value,
                shape = shape,
                onClick = { open = true },
                trailing = { Swatch(value, selected = false, size = 28) },
            )
            if (open) ColorDialog(p.label, value, onDismiss = { open = false }) { onChange(it); open = false }
        }

        "date" -> {
            var open by remember { mutableStateOf(false) }
            val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
            NothingItem(
                title = p.label,
                subtitle = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                shape = shape,
                onClick = { open = true },
            )
            if (open) {
                val state = rememberDatePickerState(
                    initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                )
                DatePickerDialog(
                    onDismissRequest = { open = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let {
                                onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                            }
                            open = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
                ) { DatePicker(state = state) }
            }
        }
    }
}

@Composable
private fun Swatch(ref: String, selected: Boolean, size: Int = 36) {
    val scheme = MaterialTheme.colorScheme
    val color = colorOf(ref, Color.Gray, scheme)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) scheme.onSurface else scheme.outline),
                CircleShape,
            ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDialog(title: String, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var hex by remember { mutableStateOf(if (current.startsWith("#")) current else "#") }
    val theme = listOf("primary", "secondary", "tertiary", "error", "onSurface", "primaryContainer", "secondaryContainer", "tertiaryContainer")
    val fixed = NothingPalette.fixedNames
    val adaptive = NothingPalette.adaptiveNames
    val hexOk = hex.length > 1 && SpecRules.validColor(hex)

    @Composable
    fun Group(label: String, refs: List<String>) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            refs.forEach { ref -> Box(Modifier.clickable { onPick(ref) }) { Swatch(ref, selected = ref == current) } }
        }
    }

    NothingDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = "Use hex",
        dismissText = "Cancel",
        onConfirm = { if (hexOk) onPick(hex) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Group("Follows your wallpaper", theme)
            Group("Light and dark", adaptive)
            Group("Fixed colors", fixed)
            OutlinedTextField(
                value = hex,
                onValueChange = { hex = it.take(9) },
                singleLine = true,
                label = { Text("Custom hex") },
                isError = hex.length > 1 && !hexOk,
                supportingText = { Text("#RRGGBB or #AARRGGBB") },
            )
        }
    }
}
