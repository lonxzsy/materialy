package com.materialy.music.domain.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkPath: String?,
    val fileUri: String,
    val isFavorite: Boolean = false,
    val genre: String? = null,
    val year: Int? = null,
    val bitrate: Int? = null
)

data class DownloadFormat(
    val formatId: String,
    val ext: String,
    val acodec: String?,
    val abr: Int?, // kbps
    val note: String
)

data class TrackInfo(
    val id: String,
    val title: String,
    val uploader: String,
    val duration: Long,
    val thumbnail: String?,
    val formats: List<DownloadFormat>,
    val description: String? = null
)
