package com.materialy.music.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [Index("albumId"), Index("artistId"), Index("title"), Index("genre")],
    foreignKeys = [
        ForeignKey(entity = Album::class, parentColumns = ["albumId"], childColumns = ["albumId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Artist::class, parentColumns = ["artistId"], childColumns = ["artistId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val songId: Long = 0,
    val title: String,
    val titleSort: String = title.lowercase(),
    val artistId: Long? = null,
    val artistName: String = "Unknown Artist",
    val albumId: Long? = null,
    val albumName: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long = 0,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val mimeType: String = "audio/mpeg",
    val fileUri: String = "",
    val relativePath: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false,
    val bpm: Float? = null,
    val musicalKey: String? = null,
    val energy: Float? = null,
    val loudnessDb: Float? = null,
    val artworkPath: String? = null,
    val artworkThumbPath: String? = null,
    val sourceUrl: String? = null,
    val sourceType: String? = null // youtube / soundcloud / local
)
