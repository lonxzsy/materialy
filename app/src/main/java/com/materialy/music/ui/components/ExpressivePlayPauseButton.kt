package com.materialy.music.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Play/Pause Hero Button.
 * Inspired by Google Material Components Android M3 Expressive Floating Action Button & Icon Button specs:
 * - Dynamic shape morphing between squircle (paused) and pill/circle (playing)
 * - Spring-driven interactive scale with bouncy press feedback
 * - Ambient pulse aura reactive to bass energy
 * - Animated icon rotation and crossfade
 */
@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    iconSize: Dp = 38.dp,
    bassEnergy: Float = 0.5f,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Tactile press scale with spring bounce
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expressivePlayPauseScale"
    )

    // Dynamic shape corner radius morph:
    // Paused = Squircle (e.g. 24dp for 72dp button), Playing = Rounded Circle (e.g. 36dp)
    val maxRadius = size / 2
    val pausedRadius = (size * 0.35f).coerceAtLeast(14.dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (isPlaying) maxRadius else pausedRadius,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expressiveCornerRadius"
    )

    // Ambient pulse ring when playing
    val infiniteTransition = rememberInfiniteTransition(label = "auraTransition")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f + (bassEnergy * 0.12f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraAlpha"
    )

    Box(
        modifier = modifier.size(size + 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ambient pulse aura (active only when playing)
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = auraScale * scale
                        scaleY = auraScale * scale
                        alpha = auraAlpha
                    }
                    .clip(CircleShape)
                    .background(containerColor)
            )
        }

        // Main Expressive Button Surface
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isPressed) 4.dp else 12.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    spotColor = containerColor.copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(containerColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = tween(180),
                    label = "iconCrossfade"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Пауза" else "Воспроизведение",
                        tint = contentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
