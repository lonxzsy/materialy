package com.materialy.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Material 3 Expressive Slider.
 * Inspired by Google Material Components Android M3 Expressive slider specification:
 * - Thick, tactile pill track (10-14dp)
 * - Inset/Floating thumb with elastic spring bounce on press/scrub
 * - Dynamic sine wave ripple inside the active track when playing
 * - Floating time pill indicator above thumb with spring animation
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    trackHeight: Dp = 12.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    formattedCurrentTime: String = "",
    formattedTotalTime: String = ""
) {
    var isDragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()

    // Smooth value interpolation when not dragging
    val animatedProgress by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh)
        else tween(durationMillis = 150, easing = LinearEasing),
        label = "expressiveSliderProgress"
    )

    // Wave animation phase when playing
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sliderWavePhase"
    )

    // Thumb bounce scale
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.45f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "thumbScale"
    )

    // Thumb halo opacity
    val haloAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.35f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "haloAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value,
                    range = 0f..1f
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Floating Time Tooltip during scrub
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val thumbXOffset = (animatedProgress * widthPx)
                .coerceIn(40f, (widthPx - 40f).coerceAtLeast(40f))

            androidx.compose.animation.AnimatedVisibility(
                visible = isDragging && formattedCurrentTime.isNotEmpty(),
                enter = fadeIn(tween(140)) + scaleIn(spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(120)) + scaleOut(tween(120)),
                modifier = Modifier
                    .offset { IntOffset(x = (thumbXOffset - 50.dp.roundToPx()).toInt().coerceAtLeast(0), y = 0) }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = if (formattedTotalTime.isNotEmpty()) "$formattedCurrentTime / $formattedTotalTime" else formattedCurrentTime,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Tactile Slider Track & Thumb Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onValueChange(newProgress)
                            val released = tryAwaitRelease()
                            isDragging = false
                            if (released) {
                                onValueChangeFinished?.invoke()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onValueChange(newProgress)
                        },
                        onDragEnd = {
                            isDragging = false
                            onValueChangeFinished?.invoke()
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                            onValueChange(newProgress)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val trackHeightPx = trackHeight
            val activeCornerRadius = trackHeight / 2

            // Track Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                val totalWidth = size.width
                val h = size.height
                val progressX = (animatedProgress * totalWidth).coerceIn(0f, totalWidth)
                val corner = CornerRadius(h / 2, h / 2)

                // 1. Inactive Track (Full capsule)
                drawRoundRect(
                    color = inactiveColor,
                    size = Size(totalWidth, h),
                    cornerRadius = corner
                )

                // 2. Active Track (Progress capsule)
                if (progressX > 0f) {
                    val activePath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = androidx.compose.ui.geometry.Rect(0f, 0f, progressX, h),
                                topLeft = corner,
                                bottomLeft = corner,
                                topRight = corner,
                                bottomRight = corner
                            )
                        )
                    }

                    clipPath(activePath) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    activeColor.copy(alpha = 0.85f),
                                    activeColor
                                ),
                                startX = 0f,
                                endX = progressX
                            ),
                            size = Size(progressX, h)
                        )

                        // Subtle expressive dynamic wave inside active track when playing
                        if (isPlaying && progressX > 20f) {
                            val wavePath = Path()
                            val waveAmplitude = (h * 0.16f).coerceAtLeast(1.5f)
                            val waveLength = 48f
                            var first = true

                            var x = 0f
                            while (x <= progressX) {
                                val y = (h / 2) + waveAmplitude * sin((x / waveLength) * 2 * Math.PI + wavePhase).toFloat()
                                if (first) {
                                    wavePath.moveTo(x, y)
                                    first = false
                                } else {
                                    wavePath.lineTo(x, y)
                                }
                                x += 4f
                            }

                            drawPath(
                                path = wavePath,
                                color = Color.White.copy(alpha = 0.35f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }

            // 3. Tactile Thumb with elastic bounce and glowing halo
            val thumbCenterX = (animatedProgress * widthPx).coerceIn(0f, widthPx)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (thumbCenterX - 14.dp.roundToPx()).toInt(),
                            y = 0
                        )
                    }
                    .size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer glowing halo on touch
                if (haloAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                                scaleX = thumbScale
                                scaleY = thumbScale
                                alpha = haloAlpha
                            }
                            .clip(CircleShape)
                            .background(thumbColor.copy(alpha = 0.4f))
                    )
                }

                // Core Thumb
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        }
                        .shadow(elevation = if (isDragging) 8.dp else 4.dp, shape = CircleShape, spotColor = thumbColor)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    // Inner colored dot (Material 3 Expressive accent)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(thumbColor)
                            .align(Alignment.Center)
                    )
                }
            }
        }
    }
}
