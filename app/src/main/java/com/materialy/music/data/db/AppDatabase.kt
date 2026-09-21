package com.materialy.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.materialy.music.data.db.dao.OnlineSongDao
import com.materialy.music.data.db.dao.PlaylistDao
import com.materialy.music.data.db.dao.SongDao
import com.materialy.music.data.db.entity.Album
import com.materialy.music.data.db.entity.Artist
import com.materialy.music.data.db.entity.OnlineSongEntity
import com.materialy.music.data.db.entity.Playlist
import com.materialy.music.data.db.entity.PlaylistSongCrossRef
import com.materialy.music.data.db.entity.SongEntity

@Database(
    entities = [SongEntity::class, Album::class, Artist::class, Playlist::class, PlaylistSongCrossRef::class, OnlineSongEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun onlineSongDao(): OnlineSongDao
}
