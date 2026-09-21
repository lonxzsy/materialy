package com.materialy.music.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFEF7FF),
    surface = Color(0xFFFEF7FF),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    error = Color(0xFFBA1A1A)
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    error = Color(0xFFFFB4AB)
)

@Composable
fun MaterialyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    coverColorScheme: ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val rawScheme = when {
        coverColorScheme != null -> coverColorScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDark
        else -> FallbackLight
    }

    val animatedScheme = rawScheme.animated()

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = MaterialyTypography,
        shapes = MaterialyShapes,
        content = content
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val animSpec = spring<Color>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    val animPrimary by animateColorAsState(primary, animSpec, label = "primary")
    val animOnPrimary by animateColorAsState(onPrimary, animSpec, label = "onPrimary")
    val animPrimaryContainer by animateColorAsState(primaryContainer, animSpec, label = "primaryContainer")
    val animOnPrimaryContainer by animateColorAsState(onPrimaryContainer, animSpec, label = "onPrimaryContainer")
    val animSecondary by animateColorAsState(secondary, animSpec, label = "secondary")
    val animOnSecondary by animateColorAsState(onSecondary, animSpec, label = "onSecondary")
    val animSecondaryContainer by animateColorAsState(secondaryContainer, animSpec, label = "secondaryContainer")
    val animOnSecondaryContainer by animateColorAsState(onSecondaryContainer, animSpec, label = "onSecondaryContainer")
    val animTertiary by animateColorAsState(tertiary, animSpec, label = "tertiary")
    val animOnTertiary by animateColorAsState(onTertiary, animSpec, label = "onTertiary")
    val animTertiaryContainer by animateColorAsState(tertiaryContainer, animSpec, label = "tertiaryContainer")
    val animOnTertiaryContainer by animateColorAsState(onTertiaryContainer, animSpec, label = "onTertiaryContainer")
    val animSurface by animateColorAsState(surface, animSpec, label = "surface")
    val animOnSurface by animateColorAsState(onSurface, animSpec, label = "onSurface")
    val animSurfaceVariant by animateColorAsState(surfaceVariant, animSpec, label = "surfaceVariant")
    val animOnSurfaceVariant by animateColorAsState(onSurfaceVariant, animSpec, label = "onSurfaceVariant")
    val animSurfaceContainerLowest by animateColorAsState(surfaceContainerLowest, animSpec, label = "surfaceContainerLowest")
    val animSurfaceContainerLow by animateColorAsState(surfaceContainerLow, animSpec, label = "surfaceContainerLow")
    val animSurfaceContainer by animateColorAsState(surfaceContainer, animSpec, label = "surfaceContainer")
    val animSurfaceContainerHigh by animateColorAsState(surfaceContainerHigh, animSpec, label = "surfaceContainerHigh")
    val animSurfaceContainerHighest by animateColorAsState(surfaceContainerHighest, animSpec, label = "surfaceContainerHighest")
    val animBackground by animateColorAsState(background, animSpec, label = "background")
    val animOnBackground by animateColorAsState(onBackground, animSpec, label = "onBackground")
    val animOutline by animateColorAsState(outline, animSpec, label = "outline")
    val animOutlineVariant by animateColorAsState(outlineVariant, animSpec, label = "outlineVariant")

    return copy(
        primary = animPrimary,
        onPrimary = animOnPrimary,
        primaryContainer = animPrimaryContainer,
        onPrimaryContainer = animOnPrimaryContainer,
        secondary = animSecondary,
        onSecondary = animOnSecondary,
        secondaryContainer = animSecondaryContainer,
        onSecondaryContainer = animOnSecondaryContainer,
        tertiary = animTertiary,
        onTertiary = animOnTertiary,
        tertiaryContainer = animTertiaryContainer,
        onTertiaryContainer = animOnTertiaryContainer,
        surface = animSurface,
        onSurface = animOnSurface,
        surfaceVariant = animSurfaceVariant,
        onSurfaceVariant = animOnSurfaceVariant,
        surfaceContainerLowest = animSurfaceContainerLowest,
        surfaceContainerLow = animSurfaceContainerLow,
        surfaceContainer = animSurfaceContainer,
        surfaceContainerHigh = animSurfaceContainerHigh,
        surfaceContainerHighest = animSurfaceContainerHighest,
        background = animBackground,
        onBackground = animOnBackground,
        outline = animOutline,
        outlineVariant = animOutlineVariant
    )
}

@Composable
fun MaterialyThemePreview(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = MaterialyTheme(darkTheme = darkTheme, content = content)
