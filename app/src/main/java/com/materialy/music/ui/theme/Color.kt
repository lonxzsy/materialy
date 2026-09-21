package com.materialy.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Legacy brand palette — kept only for @Preview / tests.
 * Not used in production since Dynamic Colors are mandatory.
 */
object LegacyBrandColors {
    val primaryLight = Color(0xFF5A43C8)
    val primaryDark = Color(0xFFC7B8FF)
    val backgroundDark = Color(0xFF0A0712)
}

object MaterialyBrushes {
    val primaryGradient: Brush
        @Composable get() = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        )
    val heroOverlay: Brush
        @Composable get() = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)
            )
        )
}
