package com.materialy.music.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.materialy.music.data.db.entity.Playlist
import com.materialy.music.data.db.entity.PlaylistSongCrossRef
import com.materialy.music.data.db.entity.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :id")
    fun observeWithSongs(id: Long): Flow<PlaylistWithSongs?>

    @Transaction
    @Query("SELECT * FROM playlists WHERE playlistId = :id")
    suspend fun getWithSongs(id: Long): PlaylistWithSongs?

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAllWithSongs(): Flow<List<PlaylistWithSongs>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updateName(playlistId: Long, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE playlistId = :playlistId")
    suspend fun updateUpdatedAt(playlistId: Long, updatedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("SELECT songId FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY position, addedAt, songId")
    suspend fun orderedSongIds(playlistId: Long): List<Long>

    @Query("UPDATE playlist_song_cross_ref SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updatePosition(playlistId: Long, songId: Long, position: Int)

    @Transaction
    suspend fun removeSongAndCompact(playlistId: Long, songId: Long) {
        removeSong(playlistId, songId)
        orderedSongIds(playlistId).forEachIndexed { index, id -> updatePosition(playlistId, id, index) }
        updateUpdatedAt(playlistId)
    }

    @Query("SELECT songs.* FROM songs INNER JOIN playlist_song_cross_ref AS refs ON songs.songId = refs.songId WHERE refs.playlistId = :playlistId ORDER BY refs.position, refs.addedAt, refs.songId")
    fun observeSongsInOrder(playlistId: Long): Flow<List<com.materialy.music.data.db.entity.SongEntity>>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Delete
    suspend fun delete(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deleteById(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?
}
