package com.materialy.music.ui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialy.music.data.lyrics.LrcLine
import com.materialy.music.data.lyrics.LrcParser
import com.materialy.music.ui.components.bouncy
import kotlin.math.abs

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

    // Smooth spring auto-scroll to keep active line centered in focus zone
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyrics.indices) {
            val visibleInfo = listState.layoutInfo
            val viewportHeight = visibleInfo.viewportSize.height
            val offset = if (viewportHeight > 0) -(viewportHeight / 3) else -220
            listState.animateScrollToItem(
                index = activeIndex.coerceAtLeast(0),
                scrollOffset = offset
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(lyrics, key = { index, item -> "${index}_${item.timestampMs}" }) { index, line ->
            val isActive = index == activeIndex
            val distance = abs(index - activeIndex)

            val targetAlpha = when {
                isActive -> 1.0f
                distance == 1 -> 0.65f
                distance == 2 -> 0.40f
                else -> 0.22f
            }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "lyricsAlpha"
            )

            val targetScale = if (isActive) 1.035f else 0.985f
            val scale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "lyricsScale"
            )

            val textColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "lyricsColor"
            )

            val containerColor by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                label = "lyricsBgColor"
            )

            val barWidth by animateDpAsState(
                targetValue = if (isActive) 4.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "indicatorWidth"
            )

            val barAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "indicatorAlpha"
            )

            val barSpacing by animateDpAsState(
                targetValue = if (isActive) 12.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "indicatorSpacing"
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .alpha(alpha)
                    .bouncy(scaleDown = 0.98f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSeekTo(line.timestampMs)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (barWidth > 0.dp || barAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .size(width = barWidth, height = 24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .alpha(barAlpha)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(barSpacing))
                    }
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 21.sp,
                            lineHeight = 29.sp
                        ),
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
