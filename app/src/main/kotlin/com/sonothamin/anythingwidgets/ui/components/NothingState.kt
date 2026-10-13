package com.sonothamin.anythingwidgets.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Nothing-style states: a dot-matrix picture that breathes, a short dry headline in the dot-matrix
 * face, and one clear action. Pictures are plain text grids so they're easy to read and to add.
 *
 *   #  lit dot, takes part in the slow wave
 *   R  red dot, blinks like a status LED
 *   .  unlit dot (the faint grid)
 *      (space) no dot
 */
object NothingArt {
    /** An empty widget frame with a status LED. */
    val Empty = listOf(
        "..#########..",
        ".#.........#.",
        "#...........#",
        "#...........#",
        "#.....R.....#",
        "#...........#",
        "#...........#",
        ".#.........#.",
        "..#########..",
    )

    /** Two ends of a link with the connection dropped between them. */
    val Offline = listOf(
        "...............",
        "..###.....###..",
        ".#####.R.#####.",
        "..###.....###..",
        "...............",
    ).map { it.take(15).padEnd(15, '.') }

    /** A question mark whose dot is the LED. */
    val Missing = listOf(
        ".#####.",
        "#.....#",
        "......#",
        ".....#.",
        "....#..",
        ".......",
        "....R..",
    )

    /** A key. */
    val Key = listOf(
        ".###.........",
        "#...#........",
        "#.R.#########",
        "#...#...#.#..",
        ".###....#.#..",
    ).map { it.padEnd(13, '.') }
}

/** Dot-matrix picture. Lit dots swell in a slow diagonal wave; red dots blink. */
@Composable
fun DotMatrix(
    pattern: List<String>,
    modifier: Modifier = Modifier,
    dot: Dp = 8.dp,
    gap: Dp = 6.dp,
) {
    val rows = pattern.size
    val cols = pattern.maxOf { it.length }
    val scheme = MaterialTheme.colorScheme
    val lit = scheme.onSurface
    val accent = scheme.tertiary

    val transition = rememberInfiniteTransition(label = "dot-matrix")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )

    Canvas(
        modifier
            .size(dot * cols + gap * (cols - 1), dot * rows + gap * (rows - 1))
            .clearAndSetSemantics { },
    ) {
        val d = dot.toPx()
        val step = d + gap.toPx()
        val span = (rows + cols).toFloat()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val ch = pattern[r].getOrElse(c) { ' ' }
                val center = Offset(c * step + d / 2, r * step + d / 2)
                when (ch) {
                    '#' -> {
                        val swell = 0.5f + 0.5f * cos(2f * PI.toFloat() * (wave - (r + c) / span))
                        drawCircle(lit.copy(alpha = 0.45f + 0.55f * swell), d / 2, center)
                    }
                    'R' -> drawCircle(accent.copy(alpha = if (blink < 0.55f) 1f else 0.12f), d / 2, center)
                    '.' -> drawCircle(lit.copy(alpha = 0.14f), d / 2, center)
                }
            }
        }
    }
}

/** Replaces the spinner: a ring of dots with a bright head that leaves a fading tail. */
@Composable
fun DotLoader(modifier: Modifier = Modifier, diameter: Dp = 40.dp, count: Int = 12) {
    val color = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "dot-loader")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = count.toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "head",
    )
    Canvas(modifier.size(diameter).semantics { contentDescription = "Loading" }) {
        val radius = this.size.minDimension / 2f
        val dotRadius = radius * 0.14f
        val ring = radius - dotRadius
        for (i in 0 until count) {
            val angle = 2f * PI.toFloat() * i / count - PI.toFloat() / 2f
            val behind = (((head - i) % count) + count) % count
            val fade = 1f - behind / count
            drawCircle(
                color.copy(alpha = 0.12f + 0.88f * fade * fade),
                dotRadius,
                Offset(center.x + ring * cos(angle), center.y + ring * sin(angle)),
            )
        }
    }
}

/** Full-width state that sits on the page itself: picture, headline, line of text, one action. */
@Composable
fun NothingEmptyState(
    art: List<String>,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DotMatrix(art)
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 24.dp)) { Text(actionLabel) }
        }
    }
}
