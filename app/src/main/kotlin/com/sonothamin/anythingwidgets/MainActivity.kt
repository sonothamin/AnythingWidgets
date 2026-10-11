package com.sonothamin.anythingwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ui.components.GlassHost
import com.sonothamin.anythingwidgets.ui.components.GlassNavBar
import com.sonothamin.anythingwidgets.ui.components.NavTab
import com.sonothamin.anythingwidgets.ui.theme.AnythingWidgetsTheme
import com.sonothamin.anythingwidgets.ui.theme.GlassCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnythingWidgetsTheme { HomeScreen() } }
    }
}

private val tabs = listOf(
    NavTab("Widgets", Icons.Rounded.Widgets),
    NavTab("Explore", Icons.Rounded.Explore),
    NavTab("Settings", Icons.Rounded.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    GlassHost(
        overlay = { backdrop ->
            GlassNavBar(
                tabs = tabs,
                selectedIndex = tab,
                onSelect = { tab = it },
                backdrop = backdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        },
    ) {
        Scaffold(topBar = { LargeTopAppBar(title = { Text("AnythingWidgets") }) }) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                label = "tab",
            ) { page ->
                when (page) {
                    0 -> WidgetList()
                    else -> Box(Modifier.padding(16.dp)) {
                        GlassCard(Modifier.fillMaxWidth()) {
                            Text(tabs[page].label, style = MaterialTheme.typography.titleLarge)
                            Text("Coming soon.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetList() {
    val scheme = MaterialTheme.colorScheme
    val names = listOf("Clock", "Weather", "Battery", "Notes", "Calendar", "Music", "Steps")
    val brushes = listOf(
        listOf(scheme.primary, scheme.tertiary),
        listOf(scheme.tertiary, scheme.secondary),
        listOf(scheme.secondary, scheme.primary),
    )
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(names.size) { i ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(brushes[i % brushes.size]))
                    .padding(20.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(names[i], style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimary)
            }
        }
    }
}
