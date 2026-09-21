package com.materialy.music.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "albums", indices = [Index("artistId")])
data class Album(
    @PrimaryKey(autoGenerate = true) val albumId: Long = 0,
    val name: String,
    val artistId: Long? = null,
    val artistName: String? = null,
    val year: Int? = null,
    val artworkPath: String? = null,
    val songCount: Int = 0
)
