package com.materialy.music.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.ui.platform.LocalContext
import com.materialy.music.work.TrackDownloadManager
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.materialy.music.data.lyrics.LrcLine
import com.materialy.music.data.repository.LyricsRepository
import com.materialy.music.data.repository.MusicRepository
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.components.BouncyHeartButton
import com.materialy.music.ui.components.ExpressivePlayPauseButton
import com.materialy.music.ui.components.ExpressiveSlider
import com.materialy.music.ui.components.ToastManager
import com.materialy.music.ui.components.bouncy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerManager,
    private val repo: MusicRepository,
    private val lyricsRepo: LyricsRepository
) : ViewModel() {
    val current = player.currentSong
    val isPlaying = player.isPlaying
    val currentPos = player.currentPositionMs
    val duration = player.durationMs
    val repeatMode = player.repeatMode
    val shuffleEnabled = player.shuffleEnabled
    val bassEnergy = player.bassEnergy

    private val _lyrics = MutableStateFlow<List<LrcLine>>(emptyList())
    val lyrics: StateFlow<List<LrcLine>> = _lyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var lastLoadedSongId: Long? = null

    init {
        viewModelScope.launch {
            current.collect { song ->
                if (song != null && song.songId != lastLoadedSongId) {
                    lastLoadedSongId = song.songId
                    loadLyrics(song.title, song.artistName, (song.durationMs / 1000).toInt())
                }
            }
        }
    }

    fun toggleLyricsView() {
        _showLyrics.value = !_showLyrics.value
    }

    private fun loadLyrics(title: String, artist: String, durationSeconds: Int) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            try {
                val list = lyricsRepo.getLyrics(title, artist, durationSeconds)
                _lyrics.value = list
            } catch (_: Exception) {
                _lyrics.value = emptyList()
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes != null && minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                val totalMs = minutes * 60 * 1000L
                val fadeMs = 6000L.coerceAtMost(totalMs / 2)
                val initialDelay = (totalMs - fadeMs).coerceAtLeast(0L)
                delay(initialDelay)
                player.fadeOutAndPause(durationMs = fadeMs) {
                    _sleepTimerMinutes.value = null
                    ToastManager.info("Таймер сна сработал: плавное затухание")
                }
            }
        }
    }

    fun toggleFav() {
        val song = current.value ?: return
        val newFav = !song.isFavorite
        viewModelScope.launch {
            repo.toggleFavorite(song, newFav)
            player.updateCurrentSongFavorite(newFav)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit = {},
    onQueue: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val song by viewModel.current.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPos by viewModel.currentPos.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val showLyrics by viewModel.showLyrics.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()
    val bassEnergy by viewModel.bassEnergy.collectAsState()

    var showSleepTimerDialog by remember { mutableStateOf(false) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val safeDuration = duration.coerceAtLeast(1L)
    val rawProgress = (currentPos.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = if (isPlaying) 320 else 100, easing = LinearEasing),
        label = "playerSliderProgress"
    )

    val sliderProgress = if (isDraggingSlider) dragPosition else animatedProgress

    val currentDisplayMs = if (isDraggingSlider) (dragPosition * safeDuration).toLong() else (animatedProgress * safeDuration).toLong()
    val curMins = (currentDisplayMs / 1000) / 60
    val curSecs = (currentDisplayMs / 1000) % 60
    val totalMins = (safeDuration / 1000) / 60
    val totalSecs = (safeDuration / 1000) % 60

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "coverScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Fluid Animated Mesh Gradient Canvas (Apple Music & Tidal 2026 style)
        FluidMeshBackground(
            primaryColor = MaterialTheme.colorScheme.primary,
            secondaryColor = MaterialTheme.colorScheme.secondary,
            tertiaryColor = MaterialTheme.colorScheme.tertiary,
            surfaceColor = MaterialTheme.colorScheme.surface,
            bassEnergy = bassEnergy,
            isPlaying = isPlaying
        )

        // Subtle Blurred Album Artwork overlay
        val artworkPath = song?.artworkPath
        if (!artworkPath.isNullOrBlank()) {
            val model = if (artworkPath.startsWith("http://") || artworkPath.startsWith("https://")) {
                artworkPath
            } else {
                File(artworkPath)
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(70.dp)
                    .alpha(0.40f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .bouncy()
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Свернуть",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "СЕЙЧАС ИГРАЕТ",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.albumName?.takeIf { it != "Materialy Downloads" } ?: "Медиатека",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onQueue,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .bouncy()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Очередь воспроизведения",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center Area: Vinyl/Artwork vs Synced Karaoke Lyrics
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = {
                    fadeIn(animationSpec = tween(240)) togetherWith fadeOut(animationSpec = tween(200))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "centerPlayerContent"
            ) { lyricsActive ->
                if (lyricsActive) {
                    KaraokeLyricsView(
                        lyrics = lyrics,
                        currentPositionMs = currentPos,
                        isLoading = isLoadingLyrics,
                        onSeekTo = { viewModel.player.seekTo(it) }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .aspectRatio(1f)
                                .scale(coverScale)
                                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!artworkPath.isNullOrBlank()) {
                                val model = if (artworkPath.startsWith("http://") || artworkPath.startsWith("https://")) {
                                    artworkPath
                                } else {
                                    File(artworkPath)
                                }
                                AsyncImage(
                                    model = model,
                                    contentDescription = "Обложка трека",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                                    Color(0xFF121418)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                        modifier = Modifier.size(90.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(42.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Track Title & Favorite Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "Трек не выбран",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.artistName ?: "Выберите трек",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    val currentSong = song
                    val isOffline = currentSong != null && (currentSong.isOfflineAvailable() || currentSong.fileUri.startsWith("file://") || currentSong.fileUri.startsWith("content://"))
                    IconButton(
                        onClick = {
                            if (currentSong != null && !isOffline) {
                                TrackDownloadManager.download(
                                    context = context,
                                    title = currentSong.title,
                                    artist = currentSong.artistName,
                                    sourceUrl = currentSong.fileUri,
                                    thumbnailUrl = currentSong.artworkPath,
                                    durationSec = currentSong.durationMs / 1000
                                )
                            } else {
                                ToastManager.info("Этот трек уже сохранен на устройстве")
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .bouncy(scaleDown = 0.92f)
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Filled.DownloadForOffline else Icons.Filled.CloudDownload,
                            contentDescription = if (isOffline) "Трек скачан (офлайн)" else "Скачать трек",
                            tint = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    BouncyHeartButton(
                        isFavorite = song?.isFavorite == true,
                        onToggle = {
                            viewModel.toggleFav()
                            val msg = if (song?.isFavorite != true) "Добавлено в избранное" else "Удалено из избранного"
                            ToastManager.success(msg)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Material 3 Expressive Tactile Seek Bar (Thick Track, Inset/Elastic Thumb, Floating Tooltip)
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpressiveSlider(
                    value = sliderProgress,
                    onValueChange = {
                        isDraggingSlider = true
                        dragPosition = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        viewModel.player.seekTo((dragPosition * safeDuration).toLong())
                    },
                    isPlaying = isPlaying,
                    trackHeight = 12.dp,
                    formattedCurrentTime = String.format("%d:%02d", curMins, curSecs),
                    formattedTotalTime = String.format("%d:%02d", totalMins, totalSecs),
                    activeColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format("%d:%02d", curMins, curSecs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isDraggingSlider) FontWeight.Bold else FontWeight.Normal,
                        color = if (isDraggingSlider) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%d:%02d", totalMins, totalSecs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Shuffle
                IconButton(
                    onClick = {
                        viewModel.player.toggleShuffle()
                        val msg = if (!shuffleEnabled) "Случайный порядок включен" else "Случайный порядок выключен"
                        ToastManager.info(msg)
                    },
                    modifier = Modifier.bouncy(scaleDown = 0.93f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Случайно",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Previous Track
                IconButton(
                    onClick = { viewModel.player.previous() },
                    modifier = Modifier.size(48.dp).bouncy(scaleDown = 0.93f)
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Предыдущий",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Expressive Play / Pause Hero Button (76dp)
                ExpressivePlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = { viewModel.player.togglePlayPause() },
                    size = 76.dp,
                    iconSize = 40.dp,
                    bassEnergy = bassEnergy,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )

                // Next Track
                IconButton(
                    onClick = { viewModel.player.next() },
                    modifier = Modifier.size(48.dp).bouncy(scaleDown = 0.93f)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Следующий",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Repeat
                IconButton(
                    onClick = {
                        viewModel.player.toggleRepeat()
                        val msg = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> "Повтор всех треков"
                            Player.REPEAT_MODE_ALL -> "Повтор одного трека"
                            else -> "Повтор выключен"
                        }
                        ToastManager.info(msg)
                    },
                    modifier = Modifier.bouncy(scaleDown = 0.93f)
                ) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Повтор",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Auxiliary Bar (Lyrics toggle chip & Equalizer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showLyrics,
                    onClick = { viewModel.toggleLyricsView() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lyrics,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text("Текст песни") },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.bouncy(scaleDown = 0.94f)
                )
            }
        }
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Таймер сна") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sleepTimer == mins) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSleepTimer(mins)
                                    showSleepTimerDialog = false
                                }
                        ) {
                            Text(
                                text = "$mins минут",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (sleepTimer != null) {
                        TextButton(
                            onClick = {
                                viewModel.setSleepTimer(null)
                                showSleepTimerDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отключить таймер", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
fun FluidMeshBackground(
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    surfaceColor: Color,
    bassEnergy: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluidMesh")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshPhase"
    )

    val animatedBass by animateFloatAsState(
        targetValue = if (isPlaying) bassEnergy.coerceIn(0.15f, 0.95f) else 0.15f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "meshBass"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        drawRect(color = surfaceColor)

        val c1X = w * (0.35f + 0.20f * sin(phase))
        val c1Y = h * (0.30f + 0.15f * cos(phase * 0.8f))
        val r1 = (w * 0.75f) * (0.85f + 0.35f * animatedBass)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.42f + 0.20f * animatedBass),
                    primaryColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(c1X, c1Y),
                radius = r1
            ),
            radius = r1,
            center = Offset(c1X, c1Y)
        )

        val c2X = w * (0.70f + 0.18f * cos(phase * 1.1f))
        val c2Y = h * (0.65f + 0.16f * sin(phase * 0.7f))
        val r2 = (w * 0.80f) * (0.80f + 0.30f * animatedBass)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryColor.copy(alpha = 0.38f + 0.18f * animatedBass),
                    secondaryColor.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(c2X, c2Y),
                radius = r2
            ),
            radius = r2,
            center = Offset(c2X, c2Y)
        )

        val c3X = w * (0.25f + 0.22f * cos(phase * 0.6f + 1.2f))
        val c3Y = h * (0.80f + 0.12f * sin(phase * 0.9f))
        val r3 = (w * 0.70f) * (0.75f + 0.25f * animatedBass)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tertiaryColor.copy(alpha = 0.32f + 0.15f * animatedBass),
                    tertiaryColor.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(c3X, c3Y),
                radius = r3
            ),
            radius = r3,
            center = Offset(c3X, c3Y)
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    surfaceColor.copy(alpha = 0.65f),
                    surfaceColor.copy(alpha = 0.45f),
                    surfaceColor.copy(alpha = 0.85f),
                    surfaceColor.copy(alpha = 0.95f)
                )
            )
        )
    }
}
