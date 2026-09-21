package com.materialy.music.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.materialy.music.data.db.entity.OnlineSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineSongDao {
    @Query("SELECT * FROM online_songs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<OnlineSongEntity>>

    @Query("SELECT * FROM online_songs")
    suspend fun getAll(): List<OnlineSongEntity>

    @Query("SELECT * FROM online_songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<OnlineSongEntity>>

    @Query("SELECT * FROM online_songs WHERE onlineId = :id")
    suspend fun getById(id: Long): OnlineSongEntity?

    @Query("SELECT * FROM online_songs WHERE sourceUrl = :url LIMIT 1")
    suspend fun getBySourceUrl(url: String): OnlineSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: OnlineSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<OnlineSongEntity>)

    @Update
    suspend fun update(song: OnlineSongEntity)

    @Query("DELETE FROM online_songs WHERE onlineId = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM online_songs")
    suspend fun deleteAll()

    @Query("UPDATE online_songs SET isFavorite = :fav WHERE onlineId = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE online_songs SET lastPlayedAt = :timestamp WHERE onlineId = :id")
    suspend fun updateLastPlayed(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM online_songs")
    fun observeCount(): Flow<Int>
}
