package com.sonothamin.anythingwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ui.ExplorePage
import com.sonothamin.anythingwidgets.ui.LibraryPage
import com.sonothamin.anythingwidgets.ui.SettingsPage
import com.sonothamin.anythingwidgets.ui.WidgetDetailsPage
import com.sonothamin.anythingwidgets.ui.components.GlassHost
import com.sonothamin.anythingwidgets.ui.components.GlassNavBar
import com.sonothamin.anythingwidgets.ui.components.NavTab
import com.sonothamin.anythingwidgets.ui.theme.AnythingWidgetsTheme

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

@Composable
fun HomeScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Id of the widget whose details are open, or null. Kept above the tabs so their state survives.
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = detailsId != null) { detailsId = null }

    GlassHost(
        overlay = { backdrop ->
            if (detailsId == null) {
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
            }
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab",
                ) { page ->
                    when (page) {
                        0 -> LibraryPage(refreshKey = detailsId, onOpen = { detailsId = it }, onExplore = { tab = 1 })
                        1 -> ExplorePage(refreshKey = detailsId, onOpen = { detailsId = it })
                        else -> SettingsPage()
                    }
                }

                AnimatedContent(
                    targetState = detailsId,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "details",
                ) { id ->
                    if (id != null) {
                        Surface(Modifier.fillMaxSize()) {
                            WidgetDetailsPage(id = id, onBack = { detailsId = null })
                        }
                    }
                }

                // Content scrolls under the transparent status bar; keep the clock and icons readable.
                val bg = MaterialTheme.colorScheme.background
                Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg))
                    Spacer(Modifier.fillMaxWidth().height(16.dp).background(Brush.verticalGradient(listOf(bg, Color.Transparent))))
                }
            }
        }
    }
}
