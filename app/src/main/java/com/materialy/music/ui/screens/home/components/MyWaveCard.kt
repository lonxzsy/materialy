package com.materialy.music.ui.screens.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialy.music.data.repository.MyWaveState
import com.materialy.music.data.repository.MyWaveVibe
import com.materialy.music.ui.components.bouncy
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun MyWaveCard(
    waveState: MyWaveState,
    isPlayingWave: Boolean,
    bassEnergy: Float,
    onTogglePlayWave: () -> Unit,
    onSelectVibe: (MyWaveVibe) -> Unit,
    onSkipNext: () -> Unit,
    onOpenWaveScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlayingWave) 3200 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlayingWave) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlayingWave) 900 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = primaryColor.copy(alpha = 0.35f)
            )
            .bouncy(scaleDown = 0.98f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(28.dp))
        ) {
            // Animated Canvas Background with fluid waves
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Ambient Radial Glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = if (isPlayingWave) 0.35f else 0.18f),
                            secondaryColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(canvasWidth * 0.5f, canvasHeight * 0.45f),
                        radius = canvasWidth * 0.6f * pulseScale
                    )
                )

                // Wave 1: Primary Harmonic
                val path1 = Path()
                val baseHeight1 = canvasHeight * 0.58f
                val amplitude1 = (if (isPlayingWave) 28f + (bassEnergy * 22f) else 14f)
                val wavelength1 = canvasWidth * 0.75f

                path1.moveTo(0f, canvasHeight)
                path1.lineTo(0f, baseHeight1)
                for (x in 0..canvasWidth.toInt() step 6) {
                    val angle = (x / wavelength1) * 2 * PI + phase
                    val y = baseHeight1 + sin(angle).toFloat() * amplitude1
                    path1.lineTo(x.toFloat(), y)
                }
                path1.lineTo(canvasWidth, canvasHeight)
                path1.close()

                drawPath(
                    path = path1,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f),
                            tertiaryColor.copy(alpha = 0.38f),
                            secondaryColor.copy(alpha = 0.25f)
                        )
                    )
                )

                // Wave 2: Secondary Offset Wave
                val path2 = Path()
                val baseHeight2 = canvasHeight * 0.64f
                val amplitude2 = (if (isPlayingWave) 22f + (bassEnergy * 18f) else 10f)
                val wavelength2 = canvasWidth * 0.55f

                path2.moveTo(0f, canvasHeight)
                path2.lineTo(0f, baseHeight2)
                for (x in 0..canvasWidth.toInt() step 6) {
                    val angle = (x / wavelength2) * 2 * PI - (phase * 1.3f)
                    val y = baseHeight2 + sin(angle).toFloat() * amplitude2
                    path2.lineTo(x.toFloat(), y)
                }
                path2.lineTo(canvasWidth, canvasHeight)
                path2.close()

                drawPath(
                    path = path2,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.28f),
                            primaryColor.copy(alpha = 0.32f),
                            tertiaryColor.copy(alpha = 0.20f)
                        )
                    )
                )
            }

            // Foreground Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Header Badge & Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primaryColor.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlayingWave) Icons.Filled.GraphicEq else Icons.Filled.Waves,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Моя Волна",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = waveState.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPlayingWave) {
                            IconButton(
                                onClick = onSkipNext,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .bouncy()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Следующий трек",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (onOpenWaveScreen != null) {
                            IconButton(
                                onClick = onOpenWaveScreen,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .bouncy()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.OpenInFull,
                                    contentDescription = "Открыть Мою Волну",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Center: Big Play / Pause Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingActionButton(
                        onClick = onTogglePlayWave,
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = CircleShape,
                                spotColor = primaryColor.copy(alpha = 0.6f)
                            )
                            .bouncy(scaleDown = 0.92f),
                        shape = CircleShape,
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                    ) {
                        if (waveState.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            AnimatedContent(
                                targetState = isPlayingWave,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "play_pause"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Пауза" else "Запустить Волну",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Row: Vibe Filter Chips (Fluid Scrollable LazyRow with M3 Expressive physics)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(MyWaveVibe.entries) { vibe ->
                        val isSelected = waveState.vibe == vibe
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectVibe(vibe) },
                            label = {
                                Text(
                                    text = vibe.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = primaryColor.copy(alpha = 0.22f),
                                selectedLabelColor = primaryColor,
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) primaryColor else Color.Transparent,
                                selectedBorderColor = primaryColor
                            ),
                            modifier = Modifier.bouncy()
                        )
                    }
                }
            }
        }
    }
}
