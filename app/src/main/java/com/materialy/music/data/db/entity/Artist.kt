package com.materialy.music.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey(autoGenerate = true) val artistId: Long = 0,
    val name: String,
    val sortName: String = name.lowercase(),
    val artworkPath: String? = null
)
