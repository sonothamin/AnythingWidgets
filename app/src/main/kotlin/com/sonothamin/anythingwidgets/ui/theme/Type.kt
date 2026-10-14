package com.sonothamin.anythingwidgets.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sonothamin.anythingwidgets.R

/** App bar (page header) titles: Old Standard TT (OFL). */
val AppBarFont = FontFamily(Font(R.font.old_standard_tt, FontWeight.Normal))

/** Body titles: NDOT 47 (dot-matrix display font, OFL). */
val TitleFont = FontFamily(Font(R.font.ndot_47, FontWeight.Normal))

/** Everything else: Geist (OFL). */
val BodyFont = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_semibold, FontWeight.Bold),
)

private val base = Typography()

val AppTypography = Typography(
    // Titles -> NDOT 47
    displayLarge = base.displayLarge.copy(fontFamily = TitleFont),
    displayMedium = base.displayMedium.copy(fontFamily = TitleFont),
    displaySmall = base.displaySmall.copy(fontFamily = TitleFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = TitleFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = TitleFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = TitleFont),
    titleLarge = base.titleLarge.copy(fontFamily = TitleFont),
    // Small titles, body and labels -> Geist
    titleMedium = base.titleMedium.copy(fontFamily = BodyFont),
    titleSmall = base.titleSmall.copy(fontFamily = BodyFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = BodyFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = BodyFont),
    bodySmall = base.bodySmall.copy(fontFamily = BodyFont),
    labelLarge = base.labelLarge.copy(fontFamily = BodyFont),
    labelMedium = base.labelMedium.copy(fontFamily = BodyFont),
    labelSmall = base.labelSmall.copy(fontFamily = BodyFont),
)
