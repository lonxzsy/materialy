package com.materialy.music.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.WAKE_MODE_NETWORK
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.materialy.music.data.db.AppDatabase
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.domain.model.ContentId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var audioEffectsManager: AudioEffectsManager

    @Inject
    lateinit var cachedDataSourceFactory: DataSource.Factory

    @Inject
    lateinit var appDatabase: AppDatabase

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cachedDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(WAKE_MODE_NETWORK)
            .build()

        audioEffectsManager.attachPlayer(player)

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        serviceScope.cancel()
        audioEffectsManager.releaseEffects()
        mediaLibrarySession?.release()
        player.release()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Materialy Music")
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val result = when (parentId) {
                        ROOT_ID -> {
                            val items = ImmutableList.of(
                                buildFolderItem(CATEGORY_RECENT, "Последние треки"),
                                buildFolderItem(CATEGORY_FAVORITES, "Избранное"),
                                buildFolderItem(CATEGORY_ALL_SONGS, "Все треки")
                            )
                            LibraryResult.ofItemList(items, params)
                        }
                        CATEGORY_RECENT -> {
                            val recentItems = getPersistedSessionItems()
                            LibraryResult.ofItemList(ImmutableList.copyOf(recentItems), params)
                        }
                        CATEGORY_FAVORITES -> {
                            val favs = appDatabase.songDao().getFavorites()
                            val items = favs.map(::toMediaItem)
                            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                        }
                        CATEGORY_ALL_SONGS -> {
                            val songs = appDatabase.songDao().getAll()
                            val items = songs.map(::toMediaItem)
                            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
                        }
                        else -> {
                            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        }
                    }
                    future.set(result)
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val idLong = mediaId.toLongOrNull()
                    val song = idLong?.let { appDatabase.songDao().getById(it) }
                    if (song != null) {
                        future.set(LibraryResult.ofItem(toMediaItem(song), null))
                    } else {
                        val sessionItem = getPersistedSessionItems().firstOrNull { it.mediaId == mediaId }
                        if (sessionItem != null) {
                            future.set(LibraryResult.ofItem(sessionItem, null))
                        } else {
                            future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                        }
                    }
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val items = getPersistedSessionItems()
                    if (items.isNotEmpty()) {
                        val prefs = getSharedPreferences("playback_session", Context.MODE_PRIVATE)
                        val savedIndex = prefs.getInt("index", 0).coerceIn(0, items.lastIndex)
                        val savedPosition = prefs.getLong("position", 0L).coerceAtLeast(0L)
                        future.set(MediaSession.MediaItemsWithStartPosition(items, savedIndex, savedPosition))
                    } else {
                        future.setException(UnsupportedOperationException("No saved session available"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    private fun buildFolderItem(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    private fun toMediaItem(song: SongEntity): MediaItem {
        val uri = song.fileUri.ifBlank { song.sourceUrl.orEmpty() }
        val contentId = if (!song.sourceUrl.isNullOrBlank()) {
            ContentId(com.materialy.music.domain.model.ContentProvider.YOUTUBE, com.materialy.music.domain.model.ContentType.TRACK, song.sourceUrl)
        } else {
            ContentId(com.materialy.music.domain.model.ContentProvider.LOCAL, com.materialy.music.domain.model.ContentType.TRACK, song.songId.toString())
        }
        return MediaItem.Builder()
            .setMediaId(song.songId.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(Bundle().apply { putString(CONTENT_ID_KEY, contentId.encode()) })
                    .build()
            )
            .build()
    }

    private fun getPersistedSessionItems(): List<MediaItem> {
        val prefs = getSharedPreferences("playback_session", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uri = obj.optString("uri")
                    if (uri.isBlank()) continue
                    val queueId = obj.optString("queueId", "")
                    val contentIdStr = obj.optString("contentId", "")
                    val title = obj.optString("title")
                    val artist = obj.optString("artist")
                    val album = obj.optString("album")
                    add(
                        MediaItem.Builder()
                            .setMediaId(queueId.ifBlank { i.toString() })
                            .setUri(uri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(title)
                                    .setArtist(artist)
                                    .setAlbumTitle(album)
                                    .setIsPlayable(true)
                                    .setIsBrowsable(false)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                    .setExtras(Bundle().apply { putString(CONTENT_ID_KEY, contentIdStr) })
                                    .build()
                            )
                            .build()
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val CATEGORY_RECENT = "root_recent"
        private const val CATEGORY_FAVORITES = "root_favorites"
        private const val CATEGORY_ALL_SONGS = "root_songs"
        private const val CONTENT_ID_KEY = "content_id"
    }
}
