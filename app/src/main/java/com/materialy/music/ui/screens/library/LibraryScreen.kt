package com.materialy.music.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialy.music.core.util.AutoMix
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.repository.MusicRepository
import com.materialy.music.data.repository.OnlineRepository
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.components.AddToPlaylistSheet
import com.materialy.music.ui.components.MiniPlayer
import com.materialy.music.ui.components.SongItem
import com.materialy.music.ui.components.ToastManager
import com.materialy.music.ui.components.bouncy
import com.materialy.music.ui.components.morphClick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter {
    ALL, FAVORITES, RECENT, BY_ARTIST
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val onlineRepo: OnlineRepository,
    val player: PlayerManager
) : ViewModel() {
    val songs = repo.observeSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repo.observePlaylistsWithSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isBackendOnline = onlineRepo.isBackendOnline
    val isPlaying = player.isPlaying
    val currentSong = player.currentSong
    val currentPositionMs = player.currentPositionMs
    val durationMs = player.durationMs

    fun addToPlaylist(playlistId: Long, song: SongEntity) = viewModelScope.launch {
        repo.addToPlaylist(playlistId, song)
    }

    fun createPlaylistAndAdd(name: String, song: SongEntity) = viewModelScope.launch {
        val id = repo.createPlaylist(name)
        repo.addToPlaylist(id, song)
    }

    init {
        checkBackend()
        viewModelScope.launch {
            onlineRepo.serverUrlFlow.collect {
                checkBackend()
            }
        }
    }

    fun checkBackend() {
        viewModelScope.launch {
            onlineRepo.checkBackendHealth()
        }
    }

    fun playAll(index: Int = 0) {
        viewModelScope.launch {
            val list = songs.value
            if (list.isNotEmpty()) {
                val safeIdx = index.coerceIn(0, list.size - 1)
                player.playSongs(list, safeIdx)
            } else {
                ToastManager.error("Нет треков, доступных без интернета")
            }
        }
    }

    fun shufflePlay() {
        viewModelScope.launch {
            val list = songs.value
            if (list.isNotEmpty()) {
                val shuffled = AutoMix.smartShuffle(list)
                player.playSongs(shuffled, 0)
            } else {
                ToastManager.error("Нет треков, доступных без интернета")
            }
        }
    }

    fun playSong(song: SongEntity, list: List<SongEntity>) {
        // Direct stream resolution does not depend on the embedded backend health check.
        // Local files are also independently playable, so a failed check must not block taps.
        val idx = list.indexOfFirst { it.songId == song.songId }.coerceAtLeast(0)
        if (list.isNotEmpty()) {
            player.playSongs(list, idx)
        }
    }

    fun toggleFav(id: Long, fav: Boolean) = viewModelScope.launch {
        repo.toggleFavorite(id, !fav)
    }

    fun deleteSong(song: SongEntity) = viewModelScope.launch {
        repo.deleteSong(song.songId)
        if (!song.artworkPath.isNullOrBlank() && !song.artworkPath.startsWith("http")) {
            try { java.io.File(song.artworkPath).delete() } catch (_: Exception) {}
        }
        if (song.relativePath.isNotBlank()) {
            try { java.io.File(song.relativePath).delete() } catch (_: Exception) {}
        }
    }

    fun next() = player.next()
    fun prev() = player.previous()
    fun togglePlay() = player.togglePlayPause()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onPlayer: () -> Unit,
    onNavigateToDownload: () -> Unit = {},
    onNavigateToOnline: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val current by viewModel.currentSong.collectAsState()
    val currentPos by viewModel.currentPositionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val bassEnergy by viewModel.player.bassEnergy.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var songToDelete by remember { mutableStateOf<SongEntity?>(null) }
    var songForPlaylist by remember { mutableStateOf<SongEntity?>(null) }

    // Filtered song list
    val filtered = remember(songs, query, selectedFilter) {
        var list = when (selectedFilter) {
            LibraryFilter.ALL -> songs
            LibraryFilter.FAVORITES -> songs.filter { it.isFavorite }
            LibraryFilter.RECENT -> songs.sortedByDescending { it.dateAdded }
            LibraryFilter.BY_ARTIST -> songs.sortedBy { it.artistName.lowercase() }
        }
        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artistName.contains(query, ignoreCase = true) ||
                        (it.albumName?.contains(query, ignoreCase = true) == true) ||
                        (it.genre?.contains(query, ignoreCase = true) == true)
            }
        }
        list
    }

    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }
    val totalHours = (totalDurationMs / 1000) / 3600
    val totalMins = ((totalDurationMs / 1000) % 3600) / 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Медиатека",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (songs.isNotEmpty()) {
                            val timeStr = if (totalHours > 0) "${totalHours} ч ${totalMins} мин" else "${totalMins} мин"
                            "${songs.size} треков • $timeStr звучания"
                        } else {
                            "Треков пока нет"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onNavigateToPlaylists,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .bouncy()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Плейлисты",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .bouncy()
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Настройки",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (songs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.playAll(0)
                            ToastManager.playback("Воспроизведение медиатеки")
                            onPlayer()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .bouncy(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Слушать всё", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.shufflePlay()
                            ToastManager.info("Очередь перемешана")
                            onPlayer()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .bouncy(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Перемешать", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

        // Search Bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск треков, артистов, альбомов...") },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )

        // Filter Chips Row with smooth animated response
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == LibraryFilter.ALL,
                    onClick = { selectedFilter = LibraryFilter.ALL },
                    label = { Text("Все (${songs.size})") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                )
            }
            item {
                val favCount = songs.count { it.isFavorite }
                FilterChip(
                    selected = selectedFilter == LibraryFilter.FAVORITES,
                    onClick = { selectedFilter = LibraryFilter.FAVORITES },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedFilter == LibraryFilter.FAVORITES) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
                        )
                    },
                    label = { Text("Избранное ($favCount)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == LibraryFilter.RECENT,
                    onClick = { selectedFilter = LibraryFilter.RECENT },
                    label = { Text("Недавние") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == LibraryFilter.BY_ARTIST,
                    onClick = { selectedFilter = LibraryFilter.BY_ARTIST },
                    label = { Text("По авторам") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                )
            }
        }

        // Song List with Smooth Item Placement Animations
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.LibraryMusic,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (query.isNotBlank()) "Ничего не найдено" else "В медиатеке пока пусто",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (query.isNotBlank()) "Попробуйте изменить поисковый запрос"
                        else "Найдите музыку через Поиск или откройте список сохранённых загрузок.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (query.isBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onNavigateToOnline,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .bouncy()
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Найти музыку",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            OutlinedButton(
                                onClick = onNavigateToDownload,
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .bouncy()
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Загрузки",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 150.dp)
            ) {
                items(filtered, key = { it.songId }) { song ->
                    val isCurrent = current?.songId == song.songId
                    Box(
                        modifier = Modifier.animateItem()
                    ) {
                        SongItem(
                            song = song,
                            isSelected = isCurrent,
                            isPlaying = isPlaying && isCurrent,
                            isAvailable = true,
                            onClick = {
                                viewModel.playSong(song, filtered)
                                ToastManager.playback("Открываем: ${song.title}")
                                onPlayer()
                            },
                            onLongClick = {
                                songToDelete = song
                            },
                            onFavorite = {
                                viewModel.toggleFav(song.songId, song.isFavorite)
                                val msg = if (!song.isFavorite) "Добавлено в избранное" else "Удалено из избранного"
                                ToastManager.success(msg)
                            },
                            onAddToPlaylist = {
                                songForPlaylist = song
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    songToDelete?.let { song ->
        val isOnline = song.sourceType == "online" || song.sourceType == "playlist" || song.songId < 0
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            icon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (isOnline) "Удалить онлайн-трек?" else "Удалить трек?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = if (isOnline) {
                        "Вы уверены, что хотите удалить онлайн-трек «${song.title}» из сохранённых?"
                    } else {
                        "Вы уверены, что хотите удалить трек «${song.title}» из медиатеки?"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSong(song)
                        ToastManager.info("Трек «${song.title}» удален")
                        songToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { songToDelete = null },
                    modifier = Modifier.bouncy()
                ) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (songForPlaylist != null) {
        val playlists by viewModel.playlists.collectAsState()
        AddToPlaylistSheet(
            song = songForPlaylist,
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onAddToPlaylist = { playlistId, song ->
                viewModel.addToPlaylist(playlistId, song)
            },
            onCreatePlaylistAndAdd = { name, song ->
                viewModel.createPlaylistAndAdd(name, song)
            }
        )
    }
}
