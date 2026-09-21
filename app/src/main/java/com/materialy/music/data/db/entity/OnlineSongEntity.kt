package com.materialy.music.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType

@Entity(
    tableName = "online_songs",
    indices = [Index("sourceUrl", unique = true), Index("title"), Index("addedAt")]
)
data class OnlineSongEntity(
    @PrimaryKey(autoGenerate = true) val onlineId: Long = 0,
    val title: String,
    val artistName: String = "Unknown Artist",
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    val sourceUrl: String,
    val directStreamUrl: String? = null,
    val playlistName: String? = null,
    val sourceType: String = "online",
    val addedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null
) {
    fun toSongEntity(serverBaseUrl: String): SongEntity {
        val normalizedServer = serverBaseUrl.trim().trimEnd('/')
        val encodedUrl = URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8.name())
        val streamEndpoint = "$normalizedServer/stream?url=$encodedUrl"
        val playableUri = if (!directStreamUrl.isNullOrBlank() && directStreamUrl.startsWith("http")) {
            directStreamUrl
        } else {
            streamEndpoint
        }

        return SongEntity(
            songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, sourceUrl).legacySongId(),
            title = title,
            titleSort = title.lowercase(),
            artistName = artistName,
            albumName = playlistName ?: "Онлайн Стриминг",
            durationMs = durationMs,
            mimeType = "audio/stream",
            fileUri = playableUri,
            relativePath = "online_stream",
            artworkPath = artworkUrl,
            sourceUrl = sourceUrl,
            sourceType = sourceType,
            isFavorite = isFavorite,
            dateAdded = addedAt,
            lastPlayedAt = lastPlayedAt
        )
    }
}
