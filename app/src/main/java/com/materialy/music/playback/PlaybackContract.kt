package com.materialy.music.playback

import com.materialy.music.domain.model.ContentId
import kotlinx.coroutines.flow.StateFlow

data class QueueItem(val queueItemId: String, val contentId: ContentId, val title: String, val artist: String)
data class PlaybackContext(val name: String, val ids: List<ContentId>)
data class QueueSnapshot(
    val items: List<QueueItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val context: PlaybackContext,
    val revision: Long
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Connecting : PlaybackState
    data object Buffering : PlaybackState
    data object Ready : PlaybackState
    data object Ended : PlaybackState
    data class Error(val message: String, val recoverable: Boolean) : PlaybackState
}

interface PlaybackCoordinator {
    val state: StateFlow<PlaybackState>
    val queue: StateFlow<QueueSnapshot>
    suspend fun play(context: PlaybackContext, startId: ContentId?)
    suspend fun playNext(id: ContentId)
    suspend fun addToQueue(id: ContentId)
    suspend fun move(from: Int, to: Int)
    suspend fun remove(queueItemId: String)
    suspend fun clearManualQueue()
    suspend fun retry()
}
