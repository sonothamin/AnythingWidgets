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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonothamin.anythingwidgets.BuildConfig
import com.sonothamin.anythingwidgets.R
import com.sonothamin.anythingwidgets.ui.components.ChevronIcon
import com.sonothamin.anythingwidgets.ui.components.NothingCard
import com.sonothamin.anythingwidgets.ui.components.NothingDialog
import com.sonothamin.anythingwidgets.ui.components.NothingField
import com.sonothamin.anythingwidgets.ui.components.NothingItem
import com.sonothamin.anythingwidgets.ui.components.NothingStack
import com.sonothamin.anythingwidgets.ui.components.PageHeader
import com.sonothamin.anythingwidgets.ui.components.SectionLabel
import com.sonothamin.anythingwidgets.ui.components.pageMargin

private const val REPO_URL = "https://github.com/sonothamin/AnythingWidgets"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val RELEASES_URL = "$REPO_URL/releases"
private const val LICENSES_URL = "$REPO_URL/tree/main/licenses"
private const val SITE_URL = "https://sonothamin.dev"

/** Opens [url] in the browser; a device with no browser installed shouldn't crash the app. */
private fun UriHandler.open(url: String) {
    try {
        openUri(url)
    } catch (_: Exception) {
    }
}

@Composable
fun AboutPage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    var licensesOpen by rememberSaveable { mutableStateOf(false) }
    var trademarksOpen by rememberSaveable { mutableStateOf(false) }

    if (licensesOpen) {
        LicensesPage(onBack = { licensesOpen = false })
        return
    }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("About", onBack = onBack)

        // Identity: launcher icon, dot-matrix name, version.
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
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp),
            )
            Text(
                "Home-screen widgets from a simple spec.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        SectionLabel("App")
        NothingStack(Modifier.pageMargin()) {
            row { shape -> NothingField("Version", BuildConfig.VERSION_NAME, shape) }
            row { shape -> NothingField("Build", BuildConfig.VERSION_CODE.toString(), shape) }
            row { shape -> NothingField("Package", BuildConfig.APPLICATION_ID, shape) }
        }

        SectionLabel("Developer")
        NothingStack(Modifier.pageMargin()) {
            row { shape -> LinkItem("Sonoth Amin", "sonothamin.dev", Icons.Rounded.Language, shape) { uri.open(SITE_URL) } }
        }

        SectionLabel("Project")
        NothingStack(Modifier.pageMargin()) {
            row { shape -> LinkItem("Source code", "github.com/sonothamin/AnythingWidgets", Icons.Rounded.Code, shape) { uri.open(REPO_URL) } }
            row { shape -> LinkItem("Release notes", "What changed in each version", null, shape) { uri.open(RELEASES_URL) } }
            row { shape -> LinkItem("Report an issue", "Bugs and feature requests", null, shape) { uri.open(ISSUES_URL) } }
        }

        SectionLabel("Legal")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                NothingItem(
                    title = "Open-source licenses",
                    subtitle = "Fonts and libraries used in this app",
                    shape = shape,
                    onClick = { licensesOpen = true },
                    trailing = { ChevronIcon() },
                )
            }
            row { shape ->
                NothingItem(
                    title = "Trademarks and affiliation",
                    subtitle = "Nothing OS",
                    shape = shape,
                    onClick = { trademarksOpen = true },
                    trailing = { ChevronIcon() },
                )
            }
        }

        Text(
            "\u00A9 2026 Sonoth Amin",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        )
    }

    if (trademarksOpen) {
        NothingDialog(
            title = "Trademarks and affiliation",
            onDismiss = { trademarksOpen = false },
            confirmText = "Close",
            onConfirm = { trademarksOpen = false },
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "The visual style of AnythingWidgets is inspired by Nothing OS. AnythingWidgets is an independent project and is not affiliated with, sponsored by or endorsed by Nothing Technology Limited.\n\n" +
                        "Nothing and Nothing OS are trademarks of Nothing Technology Limited.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** A row that leaves the app: optional leading glyph, outbound arrow on the right. */
@Composable
private fun LinkItem(
    title: String,
    subtitle: String,
    icon: ImageVector?,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    NothingItem(
        title = title,
        subtitle = subtitle,
        shape = shape,
        onClick = onClick,
        leading = icon?.let {
            { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        trailing = {
            Icon(
                Icons.Rounded.ArrowOutward,
                contentDescription = "Opens in browser",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** A third-party component the app ships with, and the notice that goes with it. */
private data class Notice(val name: String, val license: String, val text: String)

private val notices = listOf(
    Notice(
        name = "Geist",
        license = "SIL Open Font License 1.1",
        text = "Copyright 2024 The Geist Project Authors (https://github.com/vercel/geist-font).\n\n" +
            "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
            "The license is available at https://openfontlicense.org",
    ),
    Notice(
        name = "NDOT 47 (inspired by NOTHING)",
        license = "SIL Open Font License 1.1",
        text = "Copyright (c) 2021-2026, \u201CInteractivate\u201D " +
            "(https://fontstruct.com/fontstructors/1939594/interactivate).\n\n" +
            "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
            "The license is available at http://scripts.sil.org/OFL",
    ),
    Notice(
        name = "AndroidX and Jetpack Compose",
        license = "Apache License 2.0",
        text = "Core KTX, Activity, Compose UI, Material 3, Material Icons, Glance, WorkManager and DataStore.\n\n" +
            "Copyright The Android Open Source Project.\n\n" +
            "Licensed under the Apache License, Version 2.0. " +
            "You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0",
    ),
    Notice(
        name = "kotlinx.serialization",
        license = "Apache License 2.0",
        text = "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.\n\n" +
            "Licensed under the Apache License, Version 2.0. " +
            "You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0",
    ),
)

@Composable
private fun LicensesPage(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        PageHeader("Open-source licenses", onBack = onBack)

        Text(
            "AnythingWidgets is built with the following open-source components.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        SectionLabel("Components")
        NothingStack(Modifier.pageMargin()) {
            notices.forEachIndexed { i, n ->
                row { shape ->
                    NothingItem(
                        title = n.name,
                        subtitle = n.license,
                        shape = shape,
                        onClick = { selected = i },
                        trailing = { ChevronIcon() },
                    )
                }
            }
        }

        SectionLabel("Full texts")
        NothingStack(Modifier.pageMargin()) {
            row { shape ->
                LinkItem("License files", "Bundled in the repository", null, shape) { uri.open(LICENSES_URL) }
            }
        }
    }

    selected?.let { i ->
        val n = notices[i]
        NothingDialog(
            title = n.name,
            onDismiss = { selected = null },
            confirmText = "Close",
            onConfirm = { selected = null },
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(n.license, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(n.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
