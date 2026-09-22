package com.materialy.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.materialy.music.data.audio.AudioSettingsRepository
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioSettingsRepo: AudioSettingsRepository,
    private val audioEffectsManager: AudioEffectsManager,
    private val musicRepository: com.materialy.music.data.repository.MusicRepository,
    private val onlineSongDao: com.materialy.music.data.db.dao.OnlineSongDao,
    private val directStreamResolver: DirectStreamResolver
) : PlaybackCoordinator {
    private val TAG = "PlayerManager"
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError

    val bassEnergy: StateFlow<Float> = audioEffectsManager.bassEnergy

    private val songsByQueueItemId = mutableMapOf<String, SongEntity>()
    private var playbackContext = PlaybackContext("queue", emptyList())
    private var queueRevision = 0L
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Connecting)
    override val state: StateFlow<PlaybackState> = _state
    private val _queue = MutableStateFlow(emptyQueue())
    override val queue: StateFlow<QueueSnapshot> = _queue
    private var pendingSongsToPlay: Pair<List<SongEntity>, Int>? = null

    private var isSmoothAudioEnabled = true
    private var smoothDurationMs = 2000L

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var positionTickerJob: Job? = null
    private var tickerTicks = 0
    private val sessionPreferences = context.getSharedPreferences("playback_session", Context.MODE_PRIVATE)

    init {
        scope.launch {
            audioSettingsRepo.smoothAudioEnabledFlow.collect { isSmoothAudioEnabled = it }
        }
        scope.launch {
            audioSettingsRepo.smoothAudioDurationMsFlow.collect { smoothDurationMs = it }
        }
        init()
    }

    fun init() {
        if (controller != null || controllerFuture != null) return
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                try {
                    val c = future.get()
                    controller = c
                    c.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                            if (playing) {
                                startPositionTicker()
                            } else {
                                stopPositionTicker()
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            val song = mediaItem?.mediaId?.let(songsByQueueItemId::get)
                            if (song != null) {
                                _currentSong.value = song
                                _durationMs.value = if (song.durationMs > 0) song.durationMs else (c.duration.coerceAtLeast(0L))
                            }
                            _currentPositionMs.value = c.currentPosition.coerceAtLeast(0L)
                            publishQueue(c)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            _state.value = when (playbackState) {
                                Player.STATE_IDLE -> PlaybackState.Idle
                                Player.STATE_BUFFERING -> PlaybackState.Buffering
                                Player.STATE_READY -> PlaybackState.Ready
                                Player.STATE_ENDED -> PlaybackState.Ended
                                else -> PlaybackState.Idle
                            }
                            if (playbackState == Player.STATE_READY) {
                                val dur = c.duration.coerceAtLeast(0L)
                                if (dur > 0) _durationMs.value = dur
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                            _playbackError.value = "Ошибка воспроизведения: ${error.localizedMessage ?: error.errorCodeName}"
                            _isPlaying.value = false
                            _state.value = PlaybackState.Error(error.localizedMessage ?: error.errorCodeName, recoverable = true)
                        }

                        override fun onTimelineChanged(timeline: Timeline, reason: Int) = publishQueue(c)

                        override fun onRepeatModeChanged(repeatMode: Int) {
                            _repeatMode.value = repeatMode
                        }

                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            _shuffleEnabled.value = shuffleModeEnabled
                        }
                    })
                    if (c.mediaItemCount == 0 && pendingSongsToPlay == null) restoreSession(c)
                    publishQueue(c)

                    // Execute any pending play request that came in before connection
                    pendingSongsToPlay?.let { (songs, index) ->
                        pendingSongsToPlay = null
                        playSongs(songs, index)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect MediaController", e)
                    controllerFuture = null
                    val message = e.localizedMessage ?: "Не удалось подключить аудиосервис"
                    _playbackError.value = message
                    _state.value = PlaybackState.Error(message, recoverable = true)
                    if (pendingSongsToPlay != null) {
                        scope.launch {
                            delay(CONTROLLER_RETRY_DELAY_MS)
                            init()
                        }
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "SessionToken creation error", e)
            controllerFuture = null
            val message = e.localizedMessage ?: "Не удалось запустить аудиосервис"
            _playbackError.value = message
            _state.value = PlaybackState.Error(message, recoverable = true)
        }
    }

    private var lastPreloadedIndex = -1

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _currentPositionMs.value = c.currentPosition.coerceAtLeast(0L)
                    val dur = c.duration.coerceAtLeast(0L)
                    if (dur > 0) _durationMs.value = dur
                    if (tickerTicks++ % 10 == 0) {
                        _queue.value = _queue.value.copy(positionMs = c.currentPosition.coerceAtLeast(0L))
                        persistSession(c)
                    }

                    // Proactive preloading of next track when reached 75% of current song
                    val currentIndex = c.currentMediaItemIndex
                    val nextIndex = currentIndex + 1
                    if (dur > 0 && c.currentPosition > dur * 0.75f && nextIndex < c.mediaItemCount && nextIndex != lastPreloadedIndex) {
                        lastPreloadedIndex = nextIndex
                        preloadNextTrack(c, nextIndex)
                    }
                }
                delay(300)
            }
        }
    }

    private fun preloadNextTrack(c: MediaController, nextIndex: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                if (nextIndex in 0 until c.mediaItemCount) {
                    val nextItem = c.getMediaItemAt(nextIndex)
                    val uri = nextItem.localConfiguration?.uri?.toString() ?: ""
                    if (uri.isNotBlank()) {
                        directStreamResolver.resolveStreamAsync(uri)
                        Log.d(TAG, "Proactively pre-resolved next track stream: $uri")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Proactive preload error: ${e.message}")
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
    }

    private fun resolveMediaUri(song: SongEntity): Uri {
        val rawUri = song.fileUri.trim()

        if (rawUri.startsWith("file://")) {
            return Uri.parse(rawUri)
        }

        if (song.relativePath.isNotBlank()) {
            val file = java.io.File(song.relativePath)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        if (rawUri.startsWith("/")) {
            val file = java.io.File(rawUri)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        if (song.sourceType == "download") {
            try {
                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)?.let {
                    java.io.File(it, "Materialy")
                }
                if (musicDir != null && musicDir.exists()) {
                    val sanitizedTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
                    val candidate = musicDir.listFiles()?.firstOrNull {
                        it.name.contains(sanitizedTitle, ignoreCase = true) && it.length() > 0
                    }
                    if (candidate != null && candidate.exists()) {
                        return Uri.fromFile(candidate)
                    }
                }
            } catch (_: Exception) {}
        }

        return Uri.parse(rawUri)
    }

    fun playSongs(songs: List<SongEntity>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        playbackContext = PlaybackContext("selection", songs.map(::contentIdFor))

        // Immediate UI update
        _currentSong.value = songs[safeIndex]
        _durationMs.value = songs[safeIndex].durationMs
        _currentPositionMs.value = 0L
        _playbackError.value = null

        val c = controller
        if (c == null) {
            // Buffer command until MediaController is ready
            pendingSongsToPlay = Pair(songs, startIndex)
            init()
            return
        }

        songsByQueueItemId.clear()
        val playableSongs = songs.mapNotNull { song ->
            val uri = playbackUriFor(song)
            if (uri == null) {
                Log.w(TAG, "Skipping song without a playable URI: ${song.title}")
                null
            } else {
                song to mediaItemFor(song, uri)
            }
        }
        if (playableSongs.isEmpty()) {
            val message = "У трека нет доступного источника воспроизведения"
            _playbackError.value = message
            _state.value = PlaybackState.Error(message, recoverable = false)
            _isPlaying.value = false
            return
        }
        val selectedSong = songs[safeIndex]
        val playableIndex = playableSongs.indexOfFirst { it.first === selectedSong || it.first == selectedSong }
            .takeIf { it >= 0 } ?: 0
        val items = playableSongs.map { it.second }

        try {
            c.setMediaItems(items, playableIndex, 0)
            publishQueue(c)
            c.prepare()
            if (isSmoothAudioEnabled) {
                audioEffectsManager.fadeIn(c, durationMs = smoothDurationMs)
            } else {
                c.play()
            }
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            val message = e.localizedMessage ?: "Не удалось запустить воспроизведение"
            _playbackError.value = message
            _state.value = PlaybackState.Error(message, recoverable = true)
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) {
            if (isSmoothAudioEnabled) {
                audioEffectsManager.fadeOut(c, durationMs = (smoothDurationMs * 0.75f).toLong()) {
                    c.pause()
                }
            } else {
                c.pause()
            }
        } else {
            if (c.playbackState == Player.STATE_ENDED) {
                c.seekTo(0, 0)
            }
            if (isSmoothAudioEnabled) {
                audioEffectsManager.fadeIn(c, durationMs = smoothDurationMs)
            } else {
                c.play()
            }
        }
    }

    fun fadeOutAndPause(durationMs: Long = 2000L, onComplete: (() -> Unit)? = null) {
        val c = controller ?: return
        audioEffectsManager.fadeOut(c, durationMs = durationMs) {
            c.pause()
            _isPlaying.value = false
            onComplete?.invoke()
        }
    }

    fun next() {
        val c = controller ?: return
        if (isSmoothAudioEnabled) {
            audioEffectsManager.smoothTransition(c, durationMs = smoothDurationMs) {
                c.seekToNextMediaItem()
            }
        } else {
            c.seekToNextMediaItem()
        }
    }

    fun previous() {
        val c = controller ?: return
        if (isSmoothAudioEnabled) {
            audioEffectsManager.smoothTransition(c, durationMs = smoothDurationMs) {
                c.seekToPreviousMediaItem()
            }
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekTo(posMs: Long) {
        controller?.seekTo(posMs)
        _currentPositionMs.value = posMs
    }

    fun toggleRepeat() {
        val c = controller ?: return
        val nextMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val nextShuffle = !c.shuffleModeEnabled
        c.shuffleModeEnabled = nextShuffle
        _shuffleEnabled.value = nextShuffle
    }

    fun updateCurrentSongFavorite(isFav: Boolean) {
        _currentSong.value = _currentSong.value?.copy(isFavorite = isFav)
    }

    override suspend fun play(context: PlaybackContext, startId: ContentId?) {
        playbackContext = context
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull { index ->
            contentIdOf(c.getMediaItemAt(index)) == startId
        } ?: c.currentMediaItemIndex.coerceAtLeast(0)
        c.seekToDefaultPosition(index)
        c.prepare()
        c.play()
    }

    private suspend fun resolveMediaItemForContentId(id: ContentId): MediaItem? {
        val existing = findMediaItem(id)
        if (existing != null) return existing

        val song = when (id.provider) {
            ContentProvider.LOCAL -> {
                val longId = id.nativeId.toLongOrNull() ?: return null
                musicRepository.getSong(longId)
            }
            ContentProvider.YOUTUBE -> {
                val online = onlineSongDao.getAll().firstOrNull { it.sourceUrl == id.nativeId }
                if (online != null) {
                    online.toSongEntity(id.nativeId)
                } else {
                    SongEntity(
                        title = "Онлайн трек",
                        artistName = "YouTube",
                        fileUri = id.nativeId,
                        sourceUrl = id.nativeId,
                        sourceType = "youtube"
                    )
                }
            }
        } ?: return null

        val uri = playbackUriFor(song) ?: return null
        return mediaItemFor(song, uri)
    }

    override suspend fun playNext(id: ContentId) {
        val c = controller ?: return
        val item = resolveMediaItemForContentId(id) ?: return
        val insertIndex = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
        c.addMediaItem(insertIndex, item)
    }

    override suspend fun addToQueue(id: ContentId) {
        val c = controller ?: return
        val item = resolveMediaItemForContentId(id) ?: return
        c.addMediaItem(item)
    }

    override suspend fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return
        c.moveMediaItem(from, to)
    }

    override suspend fun remove(queueItemId: String) {
        val c = controller ?: return
        val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == queueItemId } ?: return
        c.removeMediaItem(index)
        songsByQueueItemId.remove(queueItemId)
    }

    override suspend fun clearManualQueue() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        for (index in c.mediaItemCount - 1 downTo 0) if (index != current) c.removeMediaItem(index)
    }

    override suspend fun retry() {
        val c = controller ?: return
        _playbackError.value = null
        _state.value = PlaybackState.Connecting
        c.prepare()
        c.play()
    }

    private fun mediaItemFor(song: SongEntity, uri: Uri): MediaItem {
        val queueId = UUID.randomUUID().toString()
        val contentId = contentIdFor(song)
        songsByQueueItemId[queueId] = song
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(queueId)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .setExtras(Bundle().apply { putString(CONTENT_ID, contentId.encode()) })
                    .build()
            ).build()
    }

    /**
     * Selects a durable input URI. Online database rows may contain an expired CDN URL or a
     * temporary loopback endpoint in fileUri, while sourceUrl is the stable YouTube identity.
     * Local and downloaded rows must always keep their content/file URI and bypass Innertube.
     */
    private fun playbackUriFor(song: SongEntity): Uri? {
        return SourceUriPolicy.selectInput(
            sourceType = song.sourceType,
            sourceUrl = song.sourceUrl,
            storedUri = song.fileUri,
            resolvedLocalUri = resolveMediaUri(song).toString()
        )?.let(Uri::parse)
    }

    private fun contentIdFor(song: SongEntity): ContentId {
        val remote = isRemoteSong(song)
        return ContentId(
            if (remote) ContentProvider.YOUTUBE else ContentProvider.LOCAL,
            ContentType.TRACK,
            (if (remote) song.sourceUrl ?: song.fileUri else song.fileUri).ifBlank { song.songId.toString() }
        )
    }

    private fun isRemoteSong(song: SongEntity): Boolean {
        return SourceUriPolicy.isRemoteTrack(song.sourceType, song.sourceUrl)
    }

    private fun contentIdOf(item: MediaItem): ContentId? =
        item.mediaMetadata.extras?.getString(CONTENT_ID)?.let(ContentId::decode)

    private fun findMediaItem(id: ContentId): MediaItem? {
        val c = controller ?: return null
        return (0 until c.mediaItemCount).map(c::getMediaItemAt).firstOrNull { contentIdOf(it) == id }
    }

    private fun publishQueue(c: Player) {
        queueRevision++
        val items = (0 until c.mediaItemCount).mapNotNull { index ->
            val item = c.getMediaItemAt(index)
            val id = contentIdOf(item) ?: return@mapNotNull null
            QueueItem(item.mediaId, id, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty())
        }
        _queue.value = QueueSnapshot(items, c.currentMediaItemIndex, c.currentPosition.coerceAtLeast(0), playbackContext, queueRevision)
        persistSession(c)
    }

    private fun persistSession(c: Player) {
        val media = JSONArray()
        for (index in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(index)
            val contentId = contentIdOf(item) ?: continue
            media.put(JSONObject().apply {
                put("queueId", item.mediaId)
                put("contentId", contentId.encode())
                put("uri", item.localConfiguration?.uri?.toString().orEmpty())
                put("title", item.mediaMetadata.title?.toString().orEmpty())
                put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                put("album", item.mediaMetadata.albumTitle?.toString().orEmpty())
            })
        }
        sessionPreferences.edit()
            .putString("items", media.toString())
            .putInt("index", c.currentMediaItemIndex)
            .putLong("position", c.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    private fun restoreSession(c: MediaController) {
        val raw = sessionPreferences.getString("items", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val restored = buildList {
                for (index in 0 until array.length()) {
                    val saved = array.getJSONObject(index)
                    val contentId = ContentId.decode(saved.getString("contentId")) ?: continue
                    val queueId = saved.getString("queueId")
                    val uri = saved.getString("uri")
                    if (uri.isBlank()) continue
                    val title = saved.optString("title")
                    val artist = saved.optString("artist")
                    songsByQueueItemId[queueId] = SongEntity(
                        title = title,
                        artistName = artist,
                        albumName = saved.optString("album").ifBlank { null },
                        fileUri = uri,
                        sourceUrl = contentId.nativeId.takeIf { contentId.provider == ContentProvider.YOUTUBE },
                        sourceType = if (contentId.provider == ContentProvider.YOUTUBE) "youtube" else "local"
                    )
                    add(MediaItem.Builder().setMediaId(queueId).setUri(uri).setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title).setArtist(artist)
                            .setAlbumTitle(saved.optString("album"))
                            .setExtras(Bundle().apply { putString(CONTENT_ID, contentId.encode()) })
                            .build()
                    ).build())
                }
            }
            if (restored.isNotEmpty()) {
                val savedIndex = sessionPreferences.getInt("index", 0).coerceIn(0, restored.lastIndex)
                val savedPosition = sessionPreferences.getLong("position", 0L).coerceAtLeast(0L)
                c.setMediaItems(restored, savedIndex, savedPosition)
                c.prepare()
                playbackContext = PlaybackContext("restored", restored.mapNotNull(::contentIdOf))
            }
        }.onFailure { Log.w(TAG, "Discarding invalid playback snapshot", it) }
    }

    private fun emptyQueue() = QueueSnapshot(emptyList(), -1, 0, PlaybackContext("empty", emptyList()), 0)

    fun release() {
        stopPositionTicker()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    companion object {
        private const val CONTENT_ID = "content_id"
        private const val CONTROLLER_RETRY_DELAY_MS = 1_000L
    }
}
