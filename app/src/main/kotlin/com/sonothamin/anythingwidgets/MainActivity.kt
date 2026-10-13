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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.platform.LocalContext
import com.sonothamin.anythingwidgets.ai.AiSettings
import com.sonothamin.anythingwidgets.ui.AiPage
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

private enum class Page(val tab: NavTab) {
    MyWidgets(NavTab("My Widgets", Icons.Rounded.Widgets)),
    Explore(NavTab("Explore", Icons.Rounded.Explore)),
    Ai(NavTab("AI", Icons.Rounded.AutoAwesome)),
    Settings(NavTab("Settings", Icons.Rounded.Settings)),
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var aiEnabled by remember { mutableStateOf(AiSettings.tabEnabled(context)) }
    var requested by rememberSaveable { mutableStateOf(Page.MyWidgets) }
    // The AI tab can be switched off; if it was open when that happened, fall back to the first tab.
    val pages = Page.entries.filter { it != Page.Ai || aiEnabled }
    val page = if (requested in pages) requested else Page.MyWidgets
    // Id of the widget whose details are open, or null. Kept above the tabs so their state survives.
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = detailsId != null) { detailsId = null }

    GlassHost(
        overlay = { backdrop ->
            if (detailsId == null) {
                GlassNavBar(
                    tabs = pages.map { it.tab },
                    selectedIndex = pages.indexOf(page),
                    onSelect = { requested = pages[it] },
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
                    targetState = page,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab",
                ) { current ->
                    when (current) {
                        Page.MyWidgets -> LibraryPage(refreshKey = detailsId, onOpen = { detailsId = it }, onExplore = { requested = Page.Explore })
                        Page.Explore -> ExplorePage(refreshKey = detailsId, onOpen = { detailsId = it })
                        Page.Ai -> AiPage(
                            refreshKey = detailsId,
                            onOpenSettings = { requested = Page.Settings },
                            onOpen = { detailsId = it },
                        )
                        Page.Settings -> SettingsPage(
                            aiEnabled = aiEnabled,
                            onAiEnabledChange = {
                                aiEnabled = it
                                AiSettings.setTabEnabled(context, it)
                            },
                        )
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
