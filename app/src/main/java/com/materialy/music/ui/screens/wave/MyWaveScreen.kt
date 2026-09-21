package com.materialy.music.ui.screens.wave

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.repository.MyWaveVibe
import com.materialy.music.ui.components.bouncy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWaveScreen(
    onNavigateToPlayer: () -> Unit,
    viewModel: MyWaveViewModel = hiltViewModel()
) {
    val waveState by viewModel.waveState.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val bassEnergy by viewModel.bassEnergy.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isLiked by viewModel.isLiked.collectAsStateWithLifecycle()

    var showSettingsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // 1. Dynamic Aurora Mesh Backdrop
        DynamicAuroraBackdrop(
            bassEnergy = bassEnergy,
            isPlaying = isPlaying,
            vibe = waveState.vibe
        )

        // Main Content Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 80.dp), // Clear bottom navigation bar
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            MyWaveTopBar(
                isPlaying = isPlaying,
                bassEnergy = bassEnergy,
                onOpenSettings = { showSettingsSheet = true }
            )

            // Vibe Ribbon Selector
            MyWaveVibeRibbon(
                selectedVibe = waveState.vibe,
                onSelectVibe = { viewModel.selectVibe(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Central Cosmic Wave Orb with Floating Disc
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CosmicWaveOrb(
                    isPlaying = isPlaying,
                    bassEnergy = bassEnergy,
                    currentSong = currentSong,
                    onDiscClick = onNavigateToPlayer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Recommendation Context Badge
            RecommendationReasonBadge(
                description = waveState.description
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Track & Artist Meta with M3 Typography
            TrackMetaDisplay(
                song = currentSong,
                onClick = onNavigateToPlayer
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Expressive Controls Dock
            MyWaveControlsDock(
                isPlaying = isPlaying,
                isLiked = isLiked,
                bassEnergy = bassEnergy,
                onTogglePlay = { viewModel.togglePlay() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrev = { viewModel.skipPrevious() },
                onToggleLike = { viewModel.toggleLike() },
                onDislike = { viewModel.dislikeAndSkip() }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Settings ModalBottomSheet
        if (showSettingsSheet) {
            MyWaveSettingsSheet(
                settings = settings,
                onUpdateSettings = { newSettings ->
                    viewModel.updateSettings(newSettings)
                },
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

/**
 * Top App Bar for My Wave with mini equalizer visualizer and tuning button.
 */
@Composable
private fun MyWaveTopBar(
    isPlaying: Boolean,
    bassEnergy: Float,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Waves,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = "Моя Волна",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isPlaying) "Бесконечный персональный поток" else "Поток на паузе",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .bouncy()
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Настройки волны",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Dynamic Aurora Mesh Backdrop that breathes with color gradients.
 */
@Composable
private fun DynamicAuroraBackdrop(
    bassEnergy: Float,
    isPlaying: Boolean,
    vibe: MyWaveVibe
) {
    val infiniteTransition = rememberInfiniteTransition(label = "auroraShift")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val vibeTint = when (vibe) {
        MyWaveVibe.MY_WAVE -> primaryColor
        MyWaveVibe.ENERGY -> Color(0xFFFF6D00)
        MyWaveVibe.CALM -> Color(0xFF00B4D8)
        MyWaveVibe.DISCOVERY -> Color(0xFF9D4EDD)
    }

    val dynamicAlpha = if (isPlaying) (0.25f + bassEnergy * 0.15f).coerceIn(0.2f, 0.45f) else 0.18f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Top glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(vibeTint.copy(alpha = dynamicAlpha), Color.Transparent),
                center = Offset(width * (0.3f + 0.4f * phase), height * 0.25f),
                radius = width * 0.85f
            )
        )

        // Center-right ambient orb
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiaryColor.copy(alpha = dynamicAlpha * 0.8f), Color.Transparent),
                center = Offset(width * (0.8f - 0.3f * phase), height * 0.55f),
                radius = width * 0.75f
            )
        )

        // Bottom subtle fill
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryColor.copy(alpha = dynamicAlpha * 0.6f), Color.Transparent),
                center = Offset(width * 0.5f, height * 0.85f),
                radius = width * 0.65f
            )
        )
    }
}

/**
 * Interactive Vibe Ribbon (MD3 Expressive Filter Chips).
 */
@Composable
private fun MyWaveVibeRibbon(
    selectedVibe: MyWaveVibe,
    onSelectVibe: (MyWaveVibe) -> Unit
) {
    val vibes = listOf(
        Triple(MyWaveVibe.MY_WAVE, "Моя волна", Icons.Filled.Waves),
        Triple(MyWaveVibe.ENERGY, "Энергия", Icons.Filled.ElectricBolt),
        Triple(MyWaveVibe.CALM, "Спокойствие", Icons.Filled.NightsStay),
        Triple(MyWaveVibe.DISCOVERY, "Открытия", Icons.Filled.Explore)
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(vibes, key = { it.first.name }) { (vibe, label, icon) ->
            val isSelected = selectedVibe == vibe
            FilterChip(
                selected = isSelected,
                onClick = { onSelectVibe(vibe) },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.Done else icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.bouncy()
            )
        }
    }
}

/**
 * Cosmic Wave Orb with multi-layer fluid harmonic animation + floating vinyl artwork.
 */
@Composable
private fun CosmicWaveOrb(
    isPlaying: Boolean,
    bassEnergy: Float,
    currentSong: SongEntity?,
    onDiscClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveOscillation")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 4000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val discRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discRotation"
    )

    val animatedBassScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f + bassEnergy * 0.12f else 1f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f),
        label = "bassScale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Stardust particle simulation (deterministic seed)
    val particles = remember {
        val random = Random(42)
        List(28) {
            Particle(
                angle = random.nextFloat() * 2f * PI.toFloat(),
                distanceRatio = 0.55f + random.nextFloat() * 0.45f,
                radius = 1.5f + random.nextFloat() * 2.5f,
                speed = 0.3f + random.nextFloat() * 0.7f
            )
        }
    }

    Box(
        modifier = Modifier
            .size(310.dp)
            .scale(animatedBassScale),
        contentAlignment = Alignment.Center
    ) {
        // 1. Fluid Canvas Wave & Stardust
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width * 0.38f

            // Draw Stardust Particles
            particles.forEach { p ->
                val dynamicAngle = p.angle + wavePhase * p.speed * 0.4f
                val dynamicDist = baseRadius * (p.distanceRatio + (if (isPlaying) bassEnergy * 0.18f else 0f))
                val px = center.x + cos(dynamicAngle) * dynamicDist
                val py = center.y + sin(dynamicAngle) * dynamicDist

                drawCircle(
                    color = primaryColor.copy(alpha = (0.35f + 0.4f * sin(wavePhase + p.angle)).coerceIn(0.1f, 0.8f)),
                    radius = p.radius * (1f + bassEnergy * 0.5f),
                    center = Offset(px, py)
                )
            }

            // Layer 1: Outer glowing bloom
            drawHarmonicWaveBlob(
                center = center,
                baseRadius = baseRadius * 1.12f,
                phase = wavePhase,
                bassEnergy = bassEnergy,
                k1 = 4, k2 = 6,
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.35f
                ),
                strokeWidth = 0f // Fill
            )

            // Layer 2: Shimmering contour
            drawHarmonicWaveBlob(
                center = center,
                baseRadius = baseRadius * 1.04f,
                phase = -wavePhase * 0.8f,
                bassEnergy = bassEnergy,
                k1 = 5, k2 = 3,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.6f),
                        tertiaryColor.copy(alpha = 0.6f),
                        secondaryColor.copy(alpha = 0.6f),
                        primaryColor.copy(alpha = 0.6f)
                    ),
                    center = center
                ),
                strokeWidth = 2.5f
            )

            // Layer 3: Inner fluid body
            drawHarmonicWaveBlob(
                center = center,
                baseRadius = baseRadius * 0.96f,
                phase = wavePhase * 1.2f,
                bassEnergy = bassEnergy,
                k1 = 3, k2 = 5,
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.22f),
                        tertiaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius
                ),
                strokeWidth = 0f
            )
        }

        // 2. Vinyl Record Disc Centerpiece
        VinylDiscArtwork(
            rotationAngle = if (isPlaying) discRotation else 0f,
            artworkPath = currentSong?.artworkPath,
            onClick = onDiscClick
        )
    }
}

/**
 * Draws an organic sinusoidal harmonic blob on Canvas.
 */
private fun DrawScope.drawHarmonicWaveBlob(
    center: Offset,
    baseRadius: Float,
    phase: Float,
    bassEnergy: Float,
    k1: Int,
    k2: Int,
    brush: Brush,
    strokeWidth: Float
) {
    val path = Path()
    val steps = 90
    val stepAngle = (2f * PI / steps).toFloat()

    val amp1 = 9f * (1f + bassEnergy * 1.4f)
    val amp2 = 6f * (1f + bassEnergy * 0.9f)

    for (i in 0..steps) {
        val angle = i * stepAngle
        val r = baseRadius + amp1 * sin(k1 * angle + phase) + amp2 * cos(k2 * angle - phase)
        val x = center.x + cos(angle) * r
        val y = center.y + sin(angle) * r

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    if (strokeWidth > 0f) {
        drawPath(path = path, brush = brush, style = Stroke(width = strokeWidth))
    } else {
        drawPath(path = path, brush = brush)
    }
}

private data class Particle(
    val angle: Float,
    val distanceRatio: Float,
    val radius: Float,
    val speed: Float
)

/**
 * Vinyl disc with grooved surface and center album art.
 */
@Composable
private fun VinylDiscArtwork(
    rotationAngle: Float,
    artworkPath: String?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(190.dp)
            .rotate(rotationAngle)
            .shadow(16.dp, CircleShape)
            .bouncy(scaleDown = 0.94f)
            .clickable { onClick() },
        shape = CircleShape,
        color = Color(0xFF141418),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Vinyl Groove Lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radii = listOf(0.92f, 0.85f, 0.78f, 0.71f, 0.64f)
                radii.forEach { factor ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = (size.width / 2f) * factor,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Center Label Artwork
            Surface(
                shape = CircleShape,
                modifier = Modifier.size(92.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.25f)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!artworkPath.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkPath,
                            contentDescription = "Обложка трека",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Spindle Hole
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF141418),
                        modifier = Modifier.size(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {}
                }
            }
        }
    }
}

/**
 * Dynamic Pill Badge displaying why this track was suggested by My Wave.
 */
@Composable
private fun RecommendationReasonBadge(description: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Track title, artist and format information rendered with expressive MD3 typography.
 */
@Composable
private fun TrackMetaDisplay(
    song: SongEntity?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = song?.title ?: "Моя Волна",
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "trackTitleAnim"
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        AnimatedContent(
            targetState = song?.artistName ?: "Нажмите Play для запуска потока",
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "trackArtistAnim"
        ) { artist ->
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quality & Stream Tag
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = "WAVE FLOW • HIGH FIDELITY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Expressive control dock featuring large bouncy FAB and distinct wave feedback.
 */
@Composable
private fun MyWaveControlsDock(
    isPlaying: Boolean,
    isLiked: Boolean,
    bassEnergy: Float,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onToggleLike: () -> Unit,
    onDislike: () -> Unit
) {
    val fabScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f + bassEnergy * 0.08f else 1f,
        animationSpec = spring(stiffness = 400f, dampingRatio = 0.5f),
        label = "fabPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dislike / Skip button
        IconButton(
            onClick = onDislike,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                .bouncy()
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Не нравится",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        // Previous button
        FilledTonalIconButton(
            onClick = onSkipPrev,
            modifier = Modifier
                .size(52.dp)
                .bouncy(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Предыдущий трек",
                modifier = Modifier.size(26.dp)
            )
        }

        // Giant Primary Play/Pause FAB with glowing halo
        Box(
            modifier = Modifier
                .size(84.dp)
                .scale(fabScale),
            contentAlignment = Alignment.Center
        ) {
            // Pulse ring
            if (isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f + bassEnergy * 0.2f),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }

            Surface(
                onClick = onTogglePlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(72.dp)
                    .bouncy(scaleDown = 0.90f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Next button
        FilledTonalIconButton(
            onClick = onSkipNext,
            modifier = Modifier
                .size(52.dp)
                .bouncy(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Следующий трек",
                modifier = Modifier.size(26.dp)
            )
        }

        // Like / Favorite button
        IconButton(
            onClick = onToggleLike,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isLiked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                )
                .bouncy()
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isLiked) "В любимых" else "Добавить в любимое",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Settings Modal BottomSheet for customizing the My Wave algorithmic flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyWaveSettingsSheet(
    settings: MyWaveSettings,
    onUpdateSettings: (MyWaveSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Настройки Моей Волны",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Section 1: Характер волны
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Характер потока",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaveCharacter.entries.forEach { character ->
                        val isSelected = settings.character == character
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateSettings(settings.copy(character = character)) },
                            label = { Text(character.label) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 2: Язык музыки
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Язык музыки",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaveLanguage.entries.forEach { language ->
                        val isSelected = settings.language == language
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateSettings(settings.copy(language = language)) },
                            label = { Text(language.label) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 3: Настроение музыки
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Настроение",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WaveMood.entries.forEach { mood ->
                        val isSelected = settings.mood == mood
                        FilterChip(
                            selected = isSelected,
                            onClick = { onUpdateSettings(settings.copy(mood = mood)) },
                            label = { Text(mood.label) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
