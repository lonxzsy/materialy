package com.materialy.music.ui.screens.artist

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.repository.MusicRepository
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.components.SongItem
import com.materialy.music.ui.components.bouncy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepo: MusicRepository,
    val player: PlayerManager
) : ViewModel() {
    private val rawArtistName: String = savedStateHandle["artistName"] ?: ""
    val artistName: String = runCatching {
        URLDecoder.decode(rawArtistName, StandardCharsets.UTF_8.name())
    }.getOrDefault(rawArtistName)

    val songs: StateFlow<List<SongEntity>> = musicRepo.observeSongsByArtist(artistName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPlaying = player.isPlaying
    val currentSong = player.currentSong

    fun playAll(startIndex: Int = 0) {
        val list = songs.value
        if (list.isNotEmpty()) {
            player.playSongs(list, startIndex.coerceIn(0, list.lastIndex))
        }
    }

    fun shufflePlay() {
        val list = songs.value
        if (list.isNotEmpty()) {
            player.playSongs(list.shuffled(), 0)
        }
    }

    fun toggleFavorite(songId: Long, currentFav: Boolean) {
        viewModelScope.launch {
            musicRepo.toggleFavorite(songId, !currentFav)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit,
    onPlayer: () -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()

    val artworkUrl = songs.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath
        ?: songs.firstOrNull { !it.fileUri.isNullOrBlank() }?.fileUri

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Navigation Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .bouncy()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Исполнитель",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier
                            .size(160.dp)
                            .shadow(elevation = 12.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        if (!artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = viewModel.artistName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(72.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = viewModel.artistName.ifBlank { "Неизвестный исполнитель" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${songs.size} треков в медиатеке",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.playAll()
                                onPlayer()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .bouncy(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = songs.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Слушать", fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.shufflePlay()
                                onPlayer()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .bouncy(),
                            shape = RoundedCornerShape(16.dp),
                            enabled = songs.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Перемешать", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            itemsIndexed(songs, key = { _, song -> song.songId }) { index, song ->
                val isCurrent = currentSong?.songId == song.songId
                SongItem(
                    song = song,
                    isSelected = isCurrent,
                    isPlaying = isPlaying && isCurrent,
                    isAvailable = true,
                    onClick = {
                        viewModel.playAll(index)
                        onPlayer()
                    },
                    onFavorite = {
                        viewModel.toggleFavorite(song.songId, song.isFavorite)
                    }
                )
            }
        }
    }
}
