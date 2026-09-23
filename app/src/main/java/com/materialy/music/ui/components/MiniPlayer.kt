package com.materialy.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import coil.compose.AsyncImage
import com.materialy.music.data.db.entity.SongEntity
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.sin

@Composable
fun MiniPlayer(
    song: SongEntity?,
    isPlaying: Boolean,
    progress: Float = 0f,
    bassEnergy: Float = 0.5f,
    isVisible: Boolean = true,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (isPlaying) 320 else 100,
            easing = LinearEasing
        ),
        label = "miniPlayerProgress"
    )

    AnimatedVisibility(
        visible = song != null && isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + expandVertically(
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(220, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(tween(180))
    ) {
        if (song == null) return@AnimatedVisibility

        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .shadow(14.dp, RoundedCornerShape(22.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(22.dp))
                .semantics(mergeDescendants = false) {
                    contentDescription = "Мини-плеер: ${song.title}, ${song.artistName}. Открыть экран воспроизведения"
                    stateDescription = if (isPlaying) "Воспроизводится" else "На паузе"
                }
                .pointerInput(song.songId) {
                    detectDragGestures(
                        onDragEnd = {},
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y < -15f) {
                                onClick()
                            } else if (dragAmount.x < -25f) {
                                onNext()
                            } else if (dragAmount.x > 25f) {
                                onPrev()
                            }
                        }
                    )
                }
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Smooth animated organic wave background synchronized with music bass & rhythm
                MiniPlayerWaveBackground(
                    isPlaying = isPlaying,
                    bassEnergy = bassEnergy,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(22.dp))
                )

                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val hasArtwork = !song.artworkPath.isNullOrBlank() &&
                                    (song.artworkPath.startsWith("http://") || song.artworkPath.startsWith("https://") || File(song.artworkPath).exists())
                            if (hasArtwork) {
                                val model = if (song.artworkPath!!.startsWith("http://") || song.artworkPath!!.startsWith("https://")) {
                                    song.artworkPath
                                } else {
                                    File(song.artworkPath!!)
                                }
                                AsyncImage(
                                    model = model,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(46.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title / Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Controls with bouncy effects
                        IconButton(
                            onClick = onPrev,
                            modifier = Modifier
                                .size(48.dp)
                                .bouncy(scaleDown = 0.92f)
                        ) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(20.dp))
                        }

                        FilledIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(48.dp)
                                .bouncy(scaleDown = 0.90f),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Приостановить" else "Продолжить воспроизведение",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .size(48.dp)
                                .bouncy(scaleDown = 0.92f)
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Bottom Progress Bar
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerWaveBackground(
    isPlaying: Boolean,
    bassEnergy: Float = 0.5f,
    modifier: Modifier = Modifier
) {
    var wavePhase1 by remember { mutableFloatStateOf(0f) }
    var wavePhase2 by remember { mutableFloatStateOf(0f) }

    val animatedBass by animateFloatAsState(
        targetValue = bassEnergy.coerceIn(0.1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "animatedBass"
    )

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastTime = withFrameMillis { it }
        while (isActive) {
            val currentTime = withFrameMillis { it }
            val dt = ((currentTime - lastTime).coerceIn(1L, 100L) / 1000f)
            lastTime = currentTime

            // Speed dynamically accelerates during bass kicks
            val speed1 = 1.3f + (animatedBass * 3.2f)
            val speed2 = 1.8f + (animatedBass * 4.0f)

            wavePhase1 = (wavePhase1 + speed1 * dt) % (2f * Math.PI.toFloat())
            wavePhase2 = (wavePhase2 + speed2 * dt) % (2f * Math.PI.toFloat())
        }
    }

    val waveAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "waveAlpha"
    )

    if (waveAlpha > 0.001f) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val tertiaryColor = MaterialTheme.colorScheme.tertiary
        val secondaryColor = MaterialTheme.colorScheme.secondary

        Canvas(modifier = modifier) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            // Dynamic amplitude scaled with bass
            val baseAmp1 = height * (0.09f + animatedBass * 0.11f)
            val baseAmp2 = height * (0.06f + animatedBass * 0.08f)
            val centerY1 = height * 0.52f
            val centerY2 = height * 0.62f

            // Wave 1: Gentle translucent gradient body (super subtle for pristine text readability)
            val path1 = Path().apply {
                moveTo(0f, height)
                lineTo(0f, centerY1)
                val step = 6f
                var x = 0f
                while (x <= width) {
                    val angle = (x / width) * 2f * Math.PI.toFloat() * 1.4f + wavePhase1
                    val y = centerY1 + sin(angle.toDouble()).toFloat() * baseAmp1
                    lineTo(x, y)
                    x += step
                }
                lineTo(width, height)
                close()
            }

            drawPath(
                path = path1,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.08f * waveAlpha),
                        primaryColor.copy(alpha = 0.02f * waveAlpha),
                        Color.Transparent
                    ),
                    startY = centerY1 - baseAmp1,
                    endY = height
                )
            )

            // Wave 2: Secondary / Tertiary translucent gradient body
            val path2 = Path().apply {
                moveTo(0f, height)
                lineTo(0f, centerY2)
                val step = 6f
                var x = 0f
                while (x <= width) {
                    val angle = (x / width) * 2f * Math.PI.toFloat() * 2.1f + wavePhase2
                    val y = centerY2 + sin(angle.toDouble()).toFloat() * baseAmp2
                    lineTo(x, y)
                    x += step
                }
                lineTo(width, height)
                close()
            }

            drawPath(
                path = path2,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = 0.06f * waveAlpha),
                        secondaryColor.copy(alpha = 0.01f * waveAlpha),
                        Color.Transparent
                    ),
                    startY = centerY2 - baseAmp2,
                    endY = height
                )
            )

            // Wave 1 subtle glowing crest stroke
            val crestPath1 = Path().apply {
                val step = 6f
                var x = 0f
                var first = true
                while (x <= width) {
                    val angle = (x / width) * 2f * Math.PI.toFloat() * 1.4f + wavePhase1
                    val y = centerY1 + sin(angle.toDouble()).toFloat() * baseAmp1
                    if (first) {
                        moveTo(x, y)
                        first = false
                    } else {
                        lineTo(x, y)
                    }
                    x += step
                }
            }
            drawPath(
                path = crestPath1,
                color = primaryColor.copy(alpha = 0.16f * waveAlpha),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Wave 2 subtle glowing crest stroke
            val crestPath2 = Path().apply {
                val step = 6f
                var x = 0f
                var first = true
                while (x <= width) {
                    val angle = (x / width) * 2f * Math.PI.toFloat() * 2.1f + wavePhase2
                    val y = centerY2 + sin(angle.toDouble()).toFloat() * baseAmp2
                    if (first) {
                        moveTo(x, y)
                        first = false
                    } else {
                        lineTo(x, y)
                    }
                    x += step
                }
            }
            drawPath(
                path = crestPath2,
                color = tertiaryColor.copy(alpha = 0.12f * waveAlpha),
                style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
