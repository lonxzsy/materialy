package com.materialy.music.data.repository

import com.materialy.music.data.db.dao.OnlineSongDao
import com.materialy.music.data.db.entity.OnlineSongEntity
import com.materialy.music.data.download.DownloadRepository
import com.materialy.music.data.download.HealthResponse
import com.materialy.music.data.download.OnlineTrackItem
import com.materialy.music.data.download.ResolvedOnlineResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineRepository @Inject constructor(
    private val onlineSongDao: OnlineSongDao,
    private val downloadRepo: DownloadRepository
) {
    private val _isBackendOnline = MutableStateFlow(false)
    val isBackendOnline: StateFlow<Boolean> = _isBackendOnline

    val serverUrlFlow: Flow<String> = downloadRepo.serverUrlFlow
    val isStandaloneModeFlow: Flow<Boolean> = downloadRepo.isStandaloneModeFlow

    suspend fun setStandaloneMode(enabled: Boolean) = downloadRepo.setStandaloneMode(enabled)

    fun normalizeUrl(input: String): String = downloadRepo.normalizeUrl(input)

    suspend fun saveServerUrl(url: String) = downloadRepo.saveServerUrl(url)

    suspend fun checkHealth(baseUrl: String): HealthResponse {
        val api = downloadRepo.createApi(baseUrl)
        return api.health()
    }

    suspend fun checkBackendHealth(targetUrl: String? = null): Boolean {
        return try {
            val url = targetUrl ?: serverUrlFlow.first()
            val res = checkHealth(url)
            val isOk = res.status == "ok" || res.yt_dlp != null
            _isBackendOnline.value = isOk
            isOk
        } catch (e: Exception) {
            _isBackendOnline.value = false
            false
        }
    }

    suspend fun resolveOnline(baseUrl: String, url: String): ResolvedOnlineResponse {
        val api = downloadRepo.createApi(baseUrl)
        return api.resolveOnline(url)
    }

    suspend fun search(baseUrl: String, query: String) = downloadRepo.createApi(baseUrl).search(query)

    fun observeOnlineSongs(): Flow<List<OnlineSongEntity>> = onlineSongDao.observeAll()

    fun observeFavorites(): Flow<List<OnlineSongEntity>> = onlineSongDao.observeFavorites()

    suspend fun addTrack(item: OnlineTrackItem, playlistName: String? = null): Long {
        val entity = OnlineSongEntity(
            title = item.title,
            artistName = item.uploader,
            durationMs = item.duration * 1000L,
            artworkUrl = item.thumbnail,
            sourceUrl = item.url,
            directStreamUrl = item.streamUrl,
            playlistName = playlistName,
            sourceType = if (playlistName != null) "playlist" else "online",
            addedAt = System.currentTimeMillis()
        )
        return onlineSongDao.insert(entity)
    }

    suspend fun addTracks(items: List<OnlineTrackItem>, playlistName: String? = null) {
        val entities = items.map { item ->
            OnlineSongEntity(
                title = item.title,
                artistName = item.uploader,
                durationMs = item.duration * 1000L,
                artworkUrl = item.thumbnail,
                sourceUrl = item.url,
                directStreamUrl = item.streamUrl,
                playlistName = playlistName,
                sourceType = if (playlistName != null) "playlist" else "online",
                addedAt = System.currentTimeMillis()
            )
        }
        onlineSongDao.insertAll(entities)
    }

    suspend fun deleteTrack(id: Long) = onlineSongDao.deleteById(id)

    suspend fun clearAll() = onlineSongDao.deleteAll()

    suspend fun toggleFavorite(id: Long, fav: Boolean) = onlineSongDao.setFavorite(id, fav)

    suspend fun updateLastPlayed(id: Long) = onlineSongDao.updateLastPlayed(id)
}
