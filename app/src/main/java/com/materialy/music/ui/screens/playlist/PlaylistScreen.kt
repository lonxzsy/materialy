package com.materialy.music.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialy.music.core.util.AutoMix
import com.materialy.music.data.db.entity.Playlist
import com.materialy.music.data.db.entity.PlaylistWithSongs
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.repository.MusicRepository
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.components.AddSongsToPlaylistSheet
import com.materialy.music.ui.components.SongItem
import com.materialy.music.ui.components.ToastManager
import com.materialy.music.ui.components.bounceClick
import com.materialy.music.ui.components.bouncy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repo: MusicRepository,
    val player: PlayerManager
) : ViewModel() {
    val playlists = repo.observePlaylistsWithSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val songs = repo.observeSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentSong = player.currentSong
    val isPlaying = player.isPlaying

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId = _selectedPlaylistId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPlaylist: StateFlow<PlaylistWithSongs?> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repo.observePlaylistWithSongs(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectPlaylist(id: Long?) {
        _selectedPlaylistId.value = id
    }

    fun create(name: String, onCreated: ((Long) -> Unit)? = null) = viewModelScope.launch {
        val id = repo.createPlaylist(name)
        onCreated?.invoke(id)
    }

    fun rename(playlistId: Long, newName: String) = viewModelScope.launch {
        repo.renamePlaylist(playlistId, newName)
    }

    fun delete(playlistId: Long) = viewModelScope.launch {
        if (_selectedPlaylistId.value == playlistId) {
            _selectedPlaylistId.value = null
        }
        repo.deletePlaylist(playlistId)
    }

    fun addSong(playlistId: Long, song: SongEntity) = viewModelScope.launch {
        repo.addToPlaylist(playlistId, song)
    }

    fun removeSong(playlistId: Long, songId: Long) = viewModelScope.launch {
        repo.removeFromPlaylist(playlistId, songId)
    }

    fun play(playlist: PlaylistWithSongs, startIndex: Int = 0) {
        if (playlist.songs.isNotEmpty()) {
            val safeIndex = startIndex.coerceIn(0, playlist.songs.size - 1)
            player.playSongs(playlist.songs, safeIndex)
        }
    }

    fun shufflePlay(playlist: PlaylistWithSongs) {
        if (playlist.songs.isNotEmpty()) {
            player.playSongs(AutoMix.smartShuffle(playlist.songs), 0)
        }
    }

    fun toggleFav(songId: Long, isFav: Boolean) = viewModelScope.launch {
        repo.toggleFavorite(songId, !isFav)
    }

    fun autoMixCreate() = viewModelScope.launch {
        val all = songs.value
        if (all.size < 3) return@launch
        val seed = all.random()
        val mix = AutoMix.autoMix(seed, all, 15)
        val id = repo.createPlaylist("AutoMix: ${seed.title.take(20)}", isAuto = true)
        mix.forEach { repo.addToPlaylist(id, it) }
    }
}

@Composable
fun PlaylistScreen(
    onPlayer: () -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val allSongs by viewModel.songs.collectAsState()
    val current by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var showAddSongsSheet by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = selectedPlaylistId,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally { (it * 0.3f).toInt() } + fadeIn() togetherWith
                        slideOutHorizontally { -(it * 0.2f).toInt() } + fadeOut()
            } else {
                slideInHorizontally { -(it * 0.2f).toInt() } + fadeIn() togetherWith
                        slideOutHorizontally { (it * 0.3f).toInt() } + fadeOut()
            }
        },
        label = "playlistNavigationTransition"
    ) { activeId ->
        if (activeId == null) {
            // PLAYLISTS LIST SCREEN
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
                // Header
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
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Плейлисты",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${playlists.size} подборок",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.bouncy()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Плейлист", fontWeight = FontWeight.Bold)
                        }

                        FilledTonalIconButton(
                            onClick = {
                                viewModel.autoMixCreate()
                                ToastManager.success("Умный AutoMix сгенерирован!")
                            },
                            enabled = allSongs.size >= 3,
                            modifier = Modifier.bouncy(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = "AutoMix",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (playlists.isEmpty()) {
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
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Плейлистов пока нет",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Создавайте свои плейлисты, добавляйте треки из медиатеки или создайте умный AutoMix.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.bouncy()
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Создать плейлист")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists, key = { it.playlist.playlistId }) { pl ->
                            PlaylistListItem(
                                playlistWithSongs = pl,
                                onClick = { viewModel.selectPlaylist(pl.playlist.playlistId) },
                                onPlay = {
                                    if (pl.songs.isNotEmpty()) {
                                        viewModel.play(pl, 0)
                                        ToastManager.playback("Воспроизведение: ${pl.playlist.name}")
                                        onPlayer()
                                    } else {
                                        ToastManager.info("В плейлисте нет треков")
                                    }
                                },
                                onRename = { playlistToRename = pl.playlist },
                                onDelete = { playlistToDelete = pl.playlist }
                            )
                        }
                    }
                }
            }
        } else {
            // PLAYLIST DETAIL SCREEN
            BackHandler { viewModel.selectPlaylist(null) }

            val pl = selectedPlaylist
            if (pl != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp)
                ) {
                    // Top App Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.selectPlaylist(null) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .bouncy()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад к плейлистам",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (pl.playlist.isAuto) "Умный автомикс" else "Пользовательский плейлист",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { playlistToRename = pl.playlist },
                            modifier = Modifier.bouncy()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Переименовать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(
                            onClick = { playlistToDelete = pl.playlist },
                            modifier = Modifier.bouncy()
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить плейлист", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hero Card with Gradient and Action Buttons
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = if (pl.playlist.isAuto)
                                                    listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
                                                else
                                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (pl.playlist.isAuto) Icons.Filled.AutoAwesome else Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.playlist.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val totalDurationSeconds = pl.songs.sumOf { it.durationMs } / 1000
                                    val totalMins = totalDurationSeconds / 60
                                    val durationStr = if (totalMins > 0) " • ${totalMins} мин" else ""
                                    Text(
                                        text = "${pl.songs.size} треков$durationStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Play, Shuffle, Add Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (pl.songs.isNotEmpty()) {
                                            viewModel.play(pl, 0)
                                            ToastManager.playback("Воспроизведение: ${pl.playlist.name}")
                                            onPlayer()
                                        } else {
                                            ToastManager.info("Добавьте треки в плейлист")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .bouncy(),
                                    shape = RoundedCornerShape(16.dp),
                                    enabled = pl.songs.isNotEmpty()
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Слушать", fontWeight = FontWeight.Bold)
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        if (pl.songs.isNotEmpty()) {
                                            viewModel.shufflePlay(pl)
                                            ToastManager.info("Очередь перемешана")
                                            onPlayer()
                                        } else {
                                            ToastManager.info("Добавьте треки в плейлист")
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .bouncy(),
                                    shape = RoundedCornerShape(16.dp),
                                    enabled = pl.songs.isNotEmpty()
                                ) {
                                    Icon(Icons.Filled.Shuffle, contentDescription = "Перемешать", modifier = Modifier.size(20.dp))
                                }

                                FilledTonalButton(
                                    onClick = { showAddSongsSheet = true },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .bouncy(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Добавить", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (pl.songs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "В этом плейлисте пока нет треков",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Добавляйте любимые треки прямо из медиатеки.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { showAddSongsSheet = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.bouncy()
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Добавить треки из медиатеки")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 150.dp)
                        ) {
                            itemsIndexed(pl.songs, key = { _, s -> s.songId }) { index, song ->
                                val isCurrent = current?.songId == song.songId
                                Box(modifier = Modifier.animateItem()) {
                                    SongItem(
                                        song = song,
                                        isSelected = isCurrent,
                                        isPlaying = isPlaying && isCurrent,
                                        isAvailable = true,
                                        onClick = {
                                            viewModel.play(pl, index)
                                            ToastManager.playback("Играет: ${song.title}")
                                            onPlayer()
                                        },
                                        onRemoveFromPlaylist = {
                                            viewModel.removeSong(pl.playlist.playlistId, song.songId)
                                            ToastManager.info("Трек «${song.title}» удален из плейлиста")
                                        },
                                        onFavorite = {
                                            viewModel.toggleFav(song.songId, song.isFavorite)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Add songs sheet
                if (showAddSongsSheet) {
                    AddSongsToPlaylistSheet(
                        playlistName = pl.playlist.name,
                        existingSongIds = pl.songs.map { it.songId }.toSet(),
                        allSongs = allSongs,
                        onDismiss = { showAddSongsSheet = false },
                        onAddSong = { song ->
                            viewModel.addSong(pl.playlist.playlistId, song)
                        }
                    )
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новый плейлист", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название плейлиста") },
                    placeholder = { Text("Мой плейлист") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.create(trimmed) { newId ->
                                viewModel.selectPlaylist(newId)
                            }
                            ToastManager.success("Плейлист «$trimmed» создан")
                        }
                        showCreateDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    modifier = Modifier.bouncy()
                ) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Rename Playlist Dialog
    playlistToRename?.let { pl ->
        var renameText by remember { mutableStateOf(pl.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Переименовать плейлист", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Новое название") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotBlank()) {
                            viewModel.rename(pl.playlistId, trimmed)
                            ToastManager.success("Плейлист переименован")
                        }
                        playlistToRename = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { playlistToRename = null },
                    modifier = Modifier.bouncy()
                ) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Delete Playlist Dialog
    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            icon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Удалить плейлист?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Вы уверены, что хотите удалить плейлист «${pl.name}»? Треки из медиатеки не будут удалены.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(pl.playlistId)
                        ToastManager.info("Плейлист «${pl.name}» удален")
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { playlistToDelete = null },
                    modifier = Modifier.bouncy()
                ) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun PlaylistListItem(
    playlistWithSongs: PlaylistWithSongs,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val pl = playlistWithSongs.playlist
    val songsCount = playlistWithSongs.songs.size
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (pl.isAuto)
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary))
                        else
                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (pl.isAuto) Icons.Filled.AutoAwesome else Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pl.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$songsCount треков" + if (pl.isAuto) " • Умный микс" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier.bouncy()
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Играть плейлист",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.bouncy()
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Действия",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Слушать") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onPlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
