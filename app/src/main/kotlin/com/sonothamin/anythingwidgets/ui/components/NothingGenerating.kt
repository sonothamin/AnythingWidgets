package com.sonothamin.anythingwidgets.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

private val Stages = listOf(
    "Reading your idea",
    "Choosing a layout",
    "Picking colors",
    "Wiring up data",
    "Checking the spec",
)

private const val Cols = 19
private const val Rows = 7

/**
 * The wait for a generated widget: a dot field that cycles through a wave, a pulse and falling rain,
 * a headline that steps through what the model is doing, and who is being asked.
 */
@Composable
fun GeneratingState(
    request: String,
    asking: String?,
    modifier: Modifier = Modifier,
) {
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            stage = (stage + 1) % Stages.size
        }
    }

    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotField()
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) togetherWith
                    (fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 3 })
            },
            label = "stage",
            modifier = Modifier.padding(top = 36.dp),
        ) { i ->
            Text(
                Stages[i],
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            asking?.let { "Asking $it" } ?: "Starting",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "“$request”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/** 19x7 dots. Three looks, 3 seconds each, easing in and out so the switch isn't a jump cut. */
@Composable
private fun DotField() {
    val scheme = MaterialTheme.colorScheme
    val lit = scheme.onSurface
    val accent = scheme.tertiary
    val transition = rememberInfiniteTransition(label = "dot-field")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )

    val dot = 7.dp
    val gap = 7.dp
    Canvas(Modifier.size(dot * Cols + gap * (Cols - 1), dot * Rows + gap * (Rows - 1))) {
        val d = dot.toPx()
        val step = d + gap.toPx()
        val mode = phase.toInt().coerceIn(0, 2)
        val t = phase - mode
        val envelope = (minOf(t, 1f - t) * 5f).coerceIn(0.15f, 1f)
        val cx = (Cols - 1) / 2f
        val cy = (Rows - 1) / 2f

        for (r in 0 until Rows) {
            for (c in 0 until Cols) {
                val v: Float = when (mode) {
                    // Diagonal wave.
                    0 -> {
                        val w = sin(2 * PI * (t - (c * 0.045f + r * 0.09f))).toFloat()
                        (0.5f + 0.5f * w).let { it * it }
                    }
                    // Rings pulsing out from the middle.
                    1 -> {
                        val dist = sqrt(((c - cx) / cx) * ((c - cx) / cx) + ((r - cy) / cy) * ((r - cy) / cy))
                        val w = sin(2 * PI * (t * 1.5f - dist * 0.6f)).toFloat()
                        (0.5f + 0.5f * w).let { it * it * it }
                    }
                    // Rain: each column drops a streak at its own pace.
                    else -> {
                        val offset = ((c * 37) % 19) / 19f
                        val head = ((t * 2f + offset) % 1f) * (Rows + 3) - 1.5f
                        val behind = head - r
                        if (behind < 0f || behind > 3f) 0f else 1f - behind / 3f
                    }
                }
                val a = (0.14f + 0.86f * v * envelope).coerceIn(0f, 1f)
                drawCircle(lit.copy(alpha = a), d / 2, Offset(c * step + d / 2, r * step + d / 2))
            }
        }
        // The LED: one red dot in the corner that blinks the whole time.
        val led = if (abs(blink - 0.5f) < 0.28f) 1f else 0.25f
        drawCircle(accent.copy(alpha = led), d / 2, Offset((Cols - 1) * step + d / 2, d / 2))
    }
}
