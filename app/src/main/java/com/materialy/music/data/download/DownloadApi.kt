package com.materialy.music.data.download

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class InfoRequest(val url: String)

@Serializable
data class InfoResponse(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val thumbnail: String? = null,
    val extractor: String? = null,
    val formats: List<FormatInfo> = emptyList(),
    val description: String? = null
)

@Serializable
data class FormatInfo(
    val formatId: String,
    val ext: String,
    val acodec: String? = null,
    val vcodec: String? = null,
    val abr: Int? = null,
    val vbr: Int? = null,
    val note: String = "",
    val filesize: Long? = null,
    val filesizeFormatted: String? = null,
    val qualityLabel: String = "",
    val qualityTier: String = "standard",
    val isRecommended: Boolean = false
)

@Serializable
data class DownloadRequest(
    val url: String,
    val formatId: String? = null,
    val audioFormat: String = "opus",
    val audioQuality: String = "0",
    val embedMetadata: Boolean = true
)

@Serializable
data class DownloadResponse(
    val jobId: String,
    val status: String,
    val message: String? = null
)

@Serializable
data class JobStatus(
    val jobId: String,
    val status: String, // queued, downloading, completed, failed
    val progress: Float = 0f,
    val speed: String? = null,
    val eta: String? = null,
    val filename: String? = null,
    val filesize: Long? = null,
    val error: String? = null
)

@Serializable
data class SearchResultItem(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val durationFormatted: String = "",
    val thumbnail: String? = null,
    val url: String
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResultItem> = emptyList()
)

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val jobs: Int = 0,
    val yt_dlp: String? = null
)

@Serializable
data class OnlineTrackItem(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val durationFormatted: String = "",
    val thumbnail: String? = null,
    val url: String,
    val streamUrl: String? = null
)

@Serializable
data class ResolvedOnlineResponse(
    val isPlaylist: Boolean = false,
    val playlistTitle: String? = null,
    val playlistUploader: String? = null,
    val itemCount: Int = 0,
    val items: List<OnlineTrackItem> = emptyList()
)

interface DownloadApi {
    @GET("info")
    suspend fun getInfo(@Query("url") url: String): InfoResponse

    @GET("resolve_online")
    suspend fun resolveOnline(@Query("url") url: String): ResolvedOnlineResponse

    @GET("search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 10): SearchResponse

    @POST("download")
    suspend fun startDownload(@Body req: DownloadRequest): DownloadResponse

    @GET("status/{jobId}")
    suspend fun getStatus(@Path("jobId") jobId: String): JobStatus

    @GET("health")
    suspend fun health(): HealthResponse
}
