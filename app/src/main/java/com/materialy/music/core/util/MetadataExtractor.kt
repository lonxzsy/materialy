package com.materialy.music.core.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.materialy.music.data.db.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ExtractedMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val genre: String?,
    val year: Int?,
    val bitrate: Int?,
    val artworkBytes: ByteArray?
)

object MetadataExtractor {
    suspend fun extract(context: Context, uri: Uri, file: File? = null): ExtractedMetadata =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                try {
                    retriever.setDataSource(context, uri)
                } catch (e: Exception) {
                    if (file != null) retriever.setDataSource(file.absolutePath)
                    else throw e
                }
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
                val art = try { retriever.embeddedPicture } catch (_: Exception) { null }
                ExtractedMetadata(title, artist, album, duration, genre, year, bitrate, art)
            } catch (e: Exception) {
                ExtractedMetadata(null, null, null, null, null, null, null, null)
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

    fun toSongEntity(meta: ExtractedMetadata, uri: String, fallbackName: String): SongEntity {
        return SongEntity(
            title = meta.title?.takeIf { it.isNotBlank() } ?: fallbackName,
            artistName = meta.artist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            albumName = meta.album,
            durationMs = meta.durationMs ?: 0L,
            genre = meta.genre,
            year = meta.year,
            bitrate = meta.bitrate,
            fileUri = uri,
            mimeType = "audio/mpeg"
        )
    }
}
