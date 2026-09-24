package com.materialy.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.materialy.music.data.db.entity.SongEntity
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: SongEntity,
    isSelected: Boolean = false,
    isPlaying: Boolean = false,
    isAvailable: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFavorite: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(1f) }

    val favTint by animateColorAsState(
        targetValue = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        else if (song.isFavorite) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favTint"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (!isAvailable) Color.Transparent
        else if (isSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        animationSpec = tween(durationMillis = 350),
        label = "borderColor"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = if (!isAvailable) MaterialTheme.colorScheme.surfaceContainerLowest
        else if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(durationMillis = 350),
        label = "containerColor"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = if (!isAvailable) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 300),
        label = "titleColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bouncy(scaleDown = 0.97f)
            .clip(RoundedCornerShape(20.dp))
            .semantics {
                stateDescription = when {
                    !isAvailable -> "Недоступен"
                    isSelected && isPlaying -> "Сейчас играет"
                    isSelected -> "Выбран, на паузе"
                    else -> "Доступен"
                }
            }
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, animatedBorderColor),
        colors = CardDefaults.cardColors(
            containerColor = animatedContainerColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork / Cover with pure fade animated equalizer overlay
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
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
                        contentDescription = "Обложка трека",
                        contentScale = ContentScale.Crop,
                        colorFilter = if (!isAvailable) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                        alpha = if (!isAvailable) 0.35f else 1f,
                        modifier = Modifier.size(52.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (!isAvailable) MaterialTheme.colorScheme.surfaceContainerLowest
                                else if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Cloud off indicator when unavailable
                if (!isAvailable) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                        contentDescription = "Сейчас недоступен",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Smoothly animated pure fade Equalizer Overlay
                PlayingEqualizerOverlay(visible = isSelected && isPlaying && isAvailable)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected && isAvailable) FontWeight.Bold else FontWeight.Medium,
                    color = animatedTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Artist & optional clean album
                val hasDistinctAlbum = !song.albumName.isNullOrBlank() &&
                        song.albumName != "Materialy Downloads" &&
                        song.albumName != "Онлайн Стриминг" &&
                        song.albumName != "Materialy Online" &&
                        song.albumName != song.artistName &&
                        song.albumName != song.title

                val subtitle = if (hasDistinctAlbum) "${song.artistName} • ${song.albumName}" else song.artistName

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Minimalist metadata: duration & subtle online/offline icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isOnline = song.sourceType == "online" || song.sourceType == "playlist" || song.fileUri.startsWith("http") || song.songId < 0
                    if (isOnline) {
                        Icon(
                            imageVector = if (isAvailable) Icons.Filled.CloudQueue else Icons.Filled.CloudOff,
                            contentDescription = if (isAvailable) "Онлайн" else "Оффлайн",
                            tint = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (song.isOfflineAvailable()) {
                        Icon(
                            imageVector = Icons.Filled.DownloadForOffline,
                            contentDescription = "Скачано (офлайн)",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (song.durationMs > 0) {
                        val minutes = (song.durationMs / 1000) / 60
                        val seconds = (song.durationMs / 1000) % 60
                        Text(
                            text = "${minutes}:${String.format("%02d", seconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    if (!isAvailable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Недоступен",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            // Add to Playlist Button
            if (onAddToPlaylist != null && isAvailable) {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Добавить в плейлист",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Remove from Playlist Button
            if (onRemoveFromPlaylist != null) {
                IconButton(
                    onClick = onRemoveFromPlaylist,
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RemoveCircleOutline,
                        contentDescription = "Убрать из плейлиста",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Favorite Button with bouncy spring scale pop (disabled if unavailable)
            IconButton(
                onClick = {
                    if (isAvailable) {
                        scope.launch {
                            heartScale.animateTo(1.35f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh))
                            heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                        onFavorite()
                    }
                },
                enabled = isAvailable,
                modifier = Modifier
                    .size(48.dp)
                    .scale(if (isAvailable) heartScale.value else 1f)
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Удалить из избранного" else "Добавить в избранное",
                    tint = favTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EqualizerAnimation() {
    val transition = rememberInfiniteTransition(label = "eq")
    val h1 by transition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.9f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(320), RepeatMode.Reverse), label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(440), RepeatMode.Reverse), label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(290), RepeatMode.Reverse), label = "h4"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp)
    ) {
        Box(modifier = Modifier.width(2.5.dp).height((18 * h1).dp).clip(CircleShape).background(Color.White))
        Box(modifier = Modifier.width(2.5.dp).height((18 * h2).dp).clip(CircleShape).background(Color.White))
        Box(modifier = Modifier.width(2.5.dp).height((18 * h3).dp).clip(CircleShape).background(Color.White))
        Box(modifier = Modifier.width(2.5.dp).height((18 * h4).dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun PlayingEqualizerOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(350))
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            EqualizerAnimation()
        }
    }
}
