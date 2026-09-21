package com.materialy.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 Typography Scale for Materialy Music.
 * Uses the complete MD3 scale. Font delivery is tracked separately from these tokens.
 */
val MaterialyTypography = Typography(
    // DISPLAY
    displayLarge = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.0.sp
    ),

    // HEADLINE
    headlineLarge = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.0.sp
    ),

    // TITLE
    titleLarge = TextStyle(
        fontFamily = AccentFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // BODY
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // LABEL
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Emphasized Typography roles from the latest Material Design 3 specification.
 * Used for focused accents, active items, track headers, and prominent badges.
 */
val Typography.displaySmallEmphasized: TextStyle
    get() = displaySmall.copy(fontWeight = FontWeight.Bold)

val Typography.headlineLargeEmphasized: TextStyle
    get() = headlineLarge.copy(fontWeight = FontWeight.Bold)

val Typography.headlineMediumEmphasized: TextStyle
    get() = headlineMedium.copy(fontWeight = FontWeight.Bold)

val Typography.headlineSmallEmphasized: TextStyle
    get() = headlineSmall.copy(fontWeight = FontWeight.Bold)

val Typography.titleLargeEmphasized: TextStyle
    get() = titleLarge.copy(fontWeight = FontWeight.Bold)

val Typography.titleMediumEmphasized: TextStyle
    get() = titleMedium.copy(fontWeight = FontWeight.SemiBold)

val Typography.titleSmallEmphasized: TextStyle
    get() = titleSmall.copy(fontWeight = FontWeight.SemiBold)

val Typography.bodyLargeEmphasized: TextStyle
    get() = bodyLarge.copy(fontWeight = FontWeight.SemiBold)

val Typography.bodyMediumEmphasized: TextStyle
    get() = bodyMedium.copy(fontWeight = FontWeight.SemiBold)

val Typography.labelLargeEmphasized: TextStyle
    get() = labelLarge.copy(fontWeight = FontWeight.SemiBold)

val Typography.labelMediumEmphasized: TextStyle
    get() = labelMedium.copy(fontWeight = FontWeight.SemiBold)

