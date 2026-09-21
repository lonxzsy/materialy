package com.materialy.music.domain.repository

import com.materialy.music.domain.model.*
import kotlinx.coroutines.flow.Flow

interface YouTubeCatalogRepository {
    suspend fun search(query: String, types: Set<ContentType>, cursor: String?): Page<SearchItem>
    suspend fun getAlbum(id: ContentId): AlbumDetails
    suspend fun getArtist(id: ContentId): ArtistDetails
    suspend fun getPlaylist(id: ContentId): PlaylistDetails
    suspend fun resolvePlayback(id: ContentId, quality: AudioQuality): PlayableSource
}

data class DownloadContext(val ids: List<ContentId>)
data class DownloadRecord(val id: ContentId, val state: DownloadState, val progress: Float)
enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, FAILED, COMPLETED }

interface OfflineRepository {
    fun observeDownloads(): Flow<List<DownloadRecord>>
    suspend fun enqueue(context: DownloadContext, quality: AudioQuality)
    suspend fun pause(id: ContentId)
    suspend fun retry(id: ContentId)
    suspend fun remove(id: ContentId)
}
