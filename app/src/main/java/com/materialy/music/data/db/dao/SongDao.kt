package com.materialy.music.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialy.music.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY titleSort ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY titleSort ASC")
    suspend fun getAll(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE songId = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun getBySourceUrl(sourceUrl: String): SongEntity?

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%' OR albumName LIKE '%' || :query || '%' ORDER BY playCount DESC")
    fun search(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC")
    suspend fun getFavorites(): List<SongEntity>

    @Query("SELECT * FROM songs ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY titleSort ASC")
    fun observeByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artistName = :artistName ORDER BY titleSort ASC")
    fun observeByArtist(artistName: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE albumName = :albumName ORDER BY trackNumber ASC, titleSort ASC")
    fun observeByAlbum(albumName: String): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT artistName FROM songs WHERE artistName IS NOT NULL AND artistName != '' ORDER BY artistName ASC")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT DISTINCT albumName FROM songs WHERE albumName IS NOT NULL AND albumName != '' ORDER BY albumName ASC")
    fun observeAlbums(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Update
    suspend fun update(song: SongEntity)

    @Query("DELETE FROM songs WHERE songId = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE songId = :id")
    suspend fun incrementPlayCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET isFavorite = :fav WHERE songId = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>
}
