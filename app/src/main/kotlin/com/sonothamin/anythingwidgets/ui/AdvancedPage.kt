package com.sonothamin.anythingwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.ui.components.NothingDialog
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingStack
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin
import com.sonothamin.anythingwidgets.widget.data.RegistrySettings

/** Settings that loosen a safe default. Everything here is off until the user turns it on. */
@Composable
fun AdvancedPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var allowHttp by remember { mutableStateOf(RegistrySettings.allowHttp(context)) }
    var confirming by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("Advanced", onBack = onBack)

        SectionLabel("Widget sources")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "Allow HTTP sources",
                    subtitle = "Lets a custom source use plain http:// links, for example a server on your own network.",
                    shape = shape,
                    onClick = {
                        if (allowHttp) {
                            RegistrySettings.setAllowHttp(context, false)
                            allowHttp = false
                        } else confirming = true
                    },
                    trailing = {
                        Switch(
                            checked = allowHttp,
                            onCheckedChange = {
                                if (it) confirming = true
                                else {
                                    RegistrySettings.setAllowHttp(context, false)
                                    allowHttp = false
                                }
                            },
                        )
                    },
                )
            }
        }
        Text(
            "HTTP isn't encrypted. Anyone on the network can read or change what the source sends. " +
                "Turning this off also switches back to the official source if your custom source uses http://.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }

    if (confirming) {
        NothingDialog(
            title = "Allow HTTP sources?",
            onDismiss = { confirming = false },
            confirmText = "Allow",
            dismissText = "Cancel",
            destructive = true,
            onConfirm = {
                RegistrySettings.setAllowHttp(context, true)
                allowHttp = true
                confirming = false
            },
        ) {
            Text(
                "Widgets and their checksums would travel unencrypted, so someone on the network could swap a widget for another one. " +
                    "Only use this for sources you run yourself on a network you trust.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
