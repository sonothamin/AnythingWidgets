package com.sonothamin.anythingwidgets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NothingRed = Color(0xFFD71921)

/** Near-black canvas, charcoal cards, white as the only "accent", red reserved for attention. */
private val NothingDark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFD0D0D0),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFF2F2F2),
    tertiary = NothingRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF4A1B1E),
    onTertiaryContainer = Color(0xFFFFDAD9),
    background = Color(0xFF1B1B1B),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFF9E9E9E),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF212121),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF2F2F2F),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    outline = Color(0xFF6E6E6E),
    outlineVariant = Color(0xFF404040),
    error = Color(0xFFFF5A5F),
    onError = Color(0xFF121212),
)

private val NothingLight = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF3A3A3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E2E2),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = NothingRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD9),
    onTertiaryContainer = Color(0xFF410004),
    background = Color(0xFFEFEFEF),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFEFEFEF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF6B6B6B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE6E6E6),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFD0D0D0),
    error = NothingRed,
    onError = Color.White,
)

/**
 * Nothing-style Material 3 Expressive theme: a fixed monochrome palette (instead of wallpaper colors),
 * dot-matrix titles, expressive motion. Widgets themselves still follow the system's dynamic colors.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnythingWidgetsTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) NothingDark else NothingLight,
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        content = content,
    )
}
