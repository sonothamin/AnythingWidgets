package com.sonothamin.anythingwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ui.theme.AnythingWidgetsTheme
import com.sonothamin.anythingwidgets.ui.theme.GlassCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnythingWidgetsTheme { HomeScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text("AnythingWidgets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = "Add widget") }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Your widgets", style = MaterialTheme.typography.titleLarge)
                Text("Nothing here yet. Tap + to create one.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
