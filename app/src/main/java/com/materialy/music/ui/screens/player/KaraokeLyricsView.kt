package com.materialy.music.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialy.music.data.lyrics.LrcLine
import com.materialy.music.data.lyrics.LrcParser

@Composable
fun KaraokeLyricsView(
    lyrics: List<LrcLine>,
    currentPositionMs: Long,
    isLoading: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Поиск текста песни...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Синхронизированный текст для этого трека пока недоступен",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val activeIndex = remember(currentPositionMs, lyrics) {
        LrcParser.findCurrentLineIndex(lyrics, currentPositionMs)
    }

    // Smooth auto-scroll to keep active line visible in focus zone
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = activeIndex.coerceAtLeast(0),
                scrollOffset = -180
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        itemsIndexed(lyrics, key = { index, item -> "${index}_${item.timestampMs}" }) { index, line ->
            val isActive = index == activeIndex

            val targetAlpha = when {
                isActive -> 1.0f
                index < activeIndex -> 0.45f
                else -> 0.30f
            }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(280),
                label = "lyricsAlpha"
            )

            val targetScale = if (isActive) 1.03f else 1.0f
            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tween(280),
                label = "lyricsScale"
            )

            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(280),
                label = "lyricsColor"
            )

            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (isActive) 22.sp else 19.sp,
                    lineHeight = if (isActive) 30.sp else 26.sp
                ),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale)
                    .alpha(alpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSeekTo(line.timestampMs)
                    }
            )
        }
    }
}
