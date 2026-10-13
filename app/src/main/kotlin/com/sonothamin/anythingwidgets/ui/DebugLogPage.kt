package com.sonothamin.anythingwidgets.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonothamin.anythingwidgets.debug.DebugLog
import com.sonothamin.anythingwidgets.debug.DebugLog.Level
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Newest-first view of [DebugLog] with level, tag and text filters, plus clear / copy / share. */
@Composable
fun DebugLogPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val all = remember(reload) { DebugLog.read(context).asReversed() }

    var minLevel by rememberSaveable { mutableStateOf(Level.DEBUG) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val tags = remember(all) { all.map { it.tag }.distinct().sorted() }
    val shown by remember(all, minLevel, tag, query) {
        derivedStateOf {
            all.filter { e ->
                e.level >= minLevel &&
                    (tag == null || e.tag == tag) &&
                    (query.isBlank() || e.message.contains(query.trim(), ignoreCase = true) || e.tag.contains(query.trim(), ignoreCase = true))
            }
        }
    }
    val time = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
    fun line(e: DebugLog.Entry) = "${time.format(Date(e.time))} ${e.level.letter}/${e.tag}: ${e.message}"

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        PageHeader(
            "Debug log",
            onBack = onBack,
            action = {
                IconButton(onClick = { reload++ }) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh") }
                IconButton(
                    enabled = shown.isNotEmpty(),
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Debug log", shown.joinToString("\n", transform = ::line)))
                        Toast.makeText(context, "Copied ${shown.size} lines", Toast.LENGTH_SHORT).show()
                    },
                ) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy") }
                IconButton(
                    enabled = shown.isNotEmpty(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, shown.joinToString("\n", transform = ::line))
                        context.startActivity(Intent.createChooser(send, "Share log"))
                    },
                ) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
                IconButton(
                    enabled = all.isNotEmpty(),
                    onClick = { DebugLog.clear(context); tag = null; reload++ },
                ) { Icon(Icons.Rounded.Delete, contentDescription = "Clear") }
            },
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Filter") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Level.entries.forEach { l ->
                FilterChip(
                    selected = minLevel == l,
                    onClick = { minLevel = l },
                    label = { Text(if (l == Level.DEBUG) "All" else "${l.name.lowercase().replaceFirstChar { it.uppercase() }}+") },
                )
            }
        }
        if (tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { t ->
                    FilterChip(selected = tag == t, onClick = { tag = if (tag == t) null else t }, label = { Text(t) })
                }
            }
        }
        Text(
            "${shown.size} of ${all.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 120.dp), Alignment.Center) {
                Text(
                    if (all.isEmpty()) "Nothing logged yet" else "No matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown) { e ->
                        val color = when (e.level) {
                            Level.ERROR -> MaterialTheme.colorScheme.error
                            Level.WARN -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            line(e),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                            color = color,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
