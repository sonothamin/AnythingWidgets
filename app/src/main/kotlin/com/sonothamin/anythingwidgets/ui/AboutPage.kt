package com.sonothamin.anythingwidgets.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.BuildConfig
import com.sonothamin.anythingwidgets.R
import com.sonothamin.anythingwidgets.ui.components.ChevronIcon
import com.sonothamin.anythingwidgets.ui.components.NothingDialog
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingStack
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin

private const val REPO_URL = "https://github.com/sonothamin/AnythingWidgets"
private const val SITE_URL = "https://sonothamin.dev"
private const val PROFILE_URL = "https://github.com/sonothamin"
private const val LICENSES_URL = "$REPO_URL/tree/main/licenses"

/** A third-party piece the app ships with, and the notice that goes with it. */
private data class Notice(val name: String, val license: String, val text: String)

private val notices = listOf(
    Notice(
        name = "Geist",
        license = "SIL Open Font License 1.1",
        text = "Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font)\n\n" +
            "Licensed under the SIL Open Font License, Version 1.1. The full license is at https://openfontlicense.org",
    ),
    Notice(
        name = "NDOT 47 (inspired by NOTHING)",
        license = "SIL Open Font License 1.1",
        text = "Copyright (c) 2021-2026, \u201cInteractivate\u201d (https://fontstruct.com/fontstructors/1939594/interactivate)\n\n" +
            "Licensed under the SIL Open Font License, Version 1.1. The full license is at http://scripts.sil.org/OFL",
    ),
    Notice(
        name = "AndroidX, Jetpack Compose, Glance, WorkManager, DataStore",
        license = "Apache License 2.0",
        text = "Copyright The Android Open Source Project\n\n" +
            "Licensed under the Apache License, Version 2.0. You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0",
    ),
    Notice(
        name = "kotlinx.serialization",
        license = "Apache License 2.0",
        text = "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors\n\n" +
            "Licensed under the Apache License, Version 2.0. You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0",
    ),
)

@Composable
fun AboutPage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    var notice by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("About", onBack = onBack)

        // App icon: the launcher's own foreground drawn over its background colour.
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "AnythingWidgets app icon",
                    modifier = Modifier.size(96.dp),
                )
            }
            Text(
                "AnythingWidgets",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionLabel("Credits")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "Sonoth Amin",
                    subtitle = "Designed and built AnythingWidgets",
                    shape = shape,
                    onClick = { uri.openUri(SITE_URL) },
                    trailing = { ChevronIcon() },
                )
            }
            row { shape ->
                NothingItem(
                    title = "Source code",
                    subtitle = "github.com/sonothamin/AnythingWidgets",
                    shape = shape,
                    onClick = { uri.openUri(REPO_URL) },
                    trailing = { ChevronIcon() },
                )
            }
            row { shape ->
                NothingItem(
                    title = "Fonts",
                    subtitle = "Geist by Vercel, NDOT 47 by Interactivate",
                    shape = shape,
                )
            }
        }

        SectionLabel("Legal")
        NothingStack(Modifier.pageMargin()) {
            notices.forEachIndexed { i, n ->
                row { shape ->
                    NothingItem(
                        title = n.name,
                        subtitle = n.license,
                        shape = shape,
                        onClick = { notice = i },
                        trailing = { ChevronIcon() },
                    )
                }
            }
            row { shape ->
                NothingItem(
                    title = "Full license texts",
                    subtitle = "Open on GitHub",
                    shape = shape,
                    onClick = { uri.openUri(LICENSES_URL) },
                    trailing = { ChevronIcon() },
                )
            }
        }

        Text(
            "Widgets come from the source you pick in Settings and are made by their own creators.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }

    notice?.let { i ->
        val n = notices[i]
        NothingDialog(
            title = n.name,
            onDismiss = { notice = null },
            confirmText = "Done",
            onConfirm = { notice = null },
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(n.license, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(n.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
