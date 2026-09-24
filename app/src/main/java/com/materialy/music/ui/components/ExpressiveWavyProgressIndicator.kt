package com.materialy.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Material 3 Expressive Wavy Progress Indicator.
 * Inspired by Google Material Components Android M3 Expressive WavyProgressIndicator:
 * - Fluid sine wave motion along the active segment
 * - Wave amplitude reacts to audio energy and playback state
 * - Smooth transition between active wave and dormant flat line
 */
@Composable
fun ExpressiveWavyProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    bassEnergy: Float = 0.5f,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    waveHeight: Dp = 6.dp,
    strokeWidth: Dp = 3.5.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        label = "wavyProgressValue"
    )

    val animatedWaveAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) (1.2f + bassEnergy * 2.2f) else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "wavyProgressAmplitude"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wavyProgressTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavyProgressPhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(waveHeight * 2 + strokeWidth)
    ) {
        val totalWidth = size.width
        val centerY = size.height / 2
        val activeWidth = (animatedProgress * totalWidth).coerceIn(0f, totalWidth)
        val strokeWidthPx = strokeWidth.toPx()

        // 1. Inactive Track (clean horizontal line with round cap)
        if (activeWidth < totalWidth) {
            drawLine(
                color = trackColor,
                start = Offset(activeWidth, centerY),
                end = Offset(totalWidth, centerY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }

        // 2. Active Track (Dynamic animated wave)
        if (activeWidth > 0f) {
            val wavePath = Path()
            val waveLength = 36f
            val amplitudePx = waveHeight.toPx() * (animatedWaveAmplitude / 3f)

            var x = 0f
            var first = true

            while (x <= activeWidth) {
                val y = centerY + amplitudePx * sin((x / waveLength) * 2 * Math.PI + phase).toFloat()
                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += 3f
            }

            drawPath(
                path = wavePath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.8f),
                        activeColor
                    ),
                    startX = 0f,
                    endX = activeWidth
                ),
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )

            // Leading head dot on progress line
            drawCircle(
                color = activeColor,
                radius = strokeWidthPx * 0.9f,
                center = Offset(
                    activeWidth,
                    centerY + amplitudePx * sin((activeWidth / waveLength) * 2 * Math.PI + phase).toFloat()
                )
            )
        }
    }
}
