package com.sonothamin.anythingwidgets.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.ui.theme.AppBarFont

/*
 * Nothing OS-style building blocks: big dot-matrix title, quiet gray section labels, and grouped
 * rounded cards (large outer corners, small inner corners, hairline gaps), built on Material 3 surfaces.
 */

/** Outer margin of grouped cards. Section labels and titles line up 12dp further in. */
fun Modifier.pageMargin(): Modifier = padding(horizontal = 12.dp)

private val Outer = 28.dp
private val Inner = 6.dp

/** Corner shape for item [index] of [count] in a group. */
fun groupShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(Outer)
    index == 0 -> RoundedCornerShape(topStart = Outer, topEnd = Outer, bottomStart = Inner, bottomEnd = Inner)
    index == count - 1 -> RoundedCornerShape(topStart = Inner, topEnd = Inner, bottomStart = Outer, bottomEnd = Outer)
    else -> RoundedCornerShape(Inner)
}

val CardShape: Shape = RoundedCornerShape(Outer)

/** Page title below an optional top row (back button / trailing action), like Settings screens. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp)) {
        if (onBack != null || action != null) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
        } else {
            Spacer(Modifier.height(40.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = AppBarFont),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
    )
}

/** A charcoal card. Clickable when [onClick] is given. */
@Composable
fun NothingCard(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Collects the rows of a [NothingStack]. */
class StackScope {
    internal val rows = mutableListOf<@Composable (Shape) -> Unit>()
    fun row(content: @Composable (Shape) -> Unit) {
        rows += content
    }
}

/** Rows drawn as one visual group: big corners on the outside, small ones between rows. */
@Composable
fun NothingStack(modifier: Modifier = Modifier, build: StackScope.() -> Unit) {
    val rows = StackScope().apply(build).rows
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, row -> row(groupShape(i, rows.size)) }
    }
}

/** Title over a gray subtitle, with optional leading icon and trailing control. */
@Composable
fun NothingItem(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    NothingCard(shape, modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
                    color = if (titleColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else titleColor,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

/** Read-only label/value row: muted label on top, the value itself at full contrast. */
@Composable
fun NothingField(label: String, value: String, shape: Shape, modifier: Modifier = Modifier) {
    NothingCard(shape, modifier, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Dialog in the app's own language: charcoal card, dot-matrix title, body text muted unless
 * the content sets its own color, and a clear primary action.
 */
@Composable
fun NothingDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = CardShape,
        containerColor = scheme.surfaceContainerHigh,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = content,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (destructive) scheme.error else scheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = dismissText?.let {
            { TextButton(onClick = onDismiss) { Text(it, color = scheme.onSurfaceVariant) } }
        },
    )
}

@Composable
fun ChevronIcon() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
