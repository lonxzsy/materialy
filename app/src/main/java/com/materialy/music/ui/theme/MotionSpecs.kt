package com.materialy.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Material Design 3 Motion Tokens and Easings.
 * Official cubic-bezier curves from Google Material 3 spec.
 */
object MotionSpecs {
    // MD3 Standard Easings
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    // Duration Tokens (ms)
    const val DurationShort1 = 50
    const val DurationShort2 = 100
    const val DurationShort3 = 150
    const val DurationShort4 = 200
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationMedium3 = 350
    const val DurationMedium4 = 400
    const val DurationLong1 = 450
    const val DurationLong2 = 500

    // Tween Specs
    fun <T> emphasizedTween(durationMillis: Int = DurationMedium2) = tween<T>(
        durationMillis = durationMillis,
        easing = Emphasized
    )

    fun <T> standardTween(durationMillis: Int = DurationMedium1) = tween<T>(
        durationMillis = durationMillis,
        easing = Standard
    )

    // Spring Specs
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Material 3 Expressive Physics Tokens (ColorSpec 2025 & MotionScheme)
    fun <T> fastSpatialSpec() = spring<T>(
        dampingRatio = 0.80f,
        stiffness = Spring.StiffnessHigh
    )

    fun <T> defaultSpatialSpec() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> slowSpatialSpec() = spring<T>(
        dampingRatio = 0.70f,
        stiffness = Spring.StiffnessLow
    )

    fun <T> defaultEffectsSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> fastEffectsSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    fun <T> slowEffectsSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
}
