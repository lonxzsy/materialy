package com.materialy.music.data.repository

import com.materialy.music.data.db.dao.OnlineSongDao
import com.materialy.music.data.db.dao.PlaylistDao
import com.materialy.music.data.db.dao.SongDao
import com.materialy.music.data.db.entity.Playlist
import com.materialy.music.data.db.entity.PlaylistSongCrossRef
import com.materialy.music.data.db.entity.PlaylistWithSongs
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.download.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import com.materialy.music.domain.model.ContentId

@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val onlineSongDao: OnlineSongDao,
    private val downloadRepo: DownloadRepository
) {
    fun observeLocalSongs(): Flow<List<SongEntity>> = songDao.observeAll()

    fun observeSongs(): Flow<List<SongEntity>> = combine(
        songDao.observeAll(),
        onlineSongDao.observeAll(),
        downloadRepo.serverUrlFlow
    ) { localSongs, onlineSongs, serverUrl ->
        val mappedOnline = onlineSongs.map { it.toSongEntity(serverUrl) }
        localSongs + mappedOnline
    }

    fun search(query: String): Flow<List<SongEntity>> = combine(
        songDao.search(query),
        onlineSongDao.observeAll(),
        downloadRepo.serverUrlFlow
    ) { localResults, onlineSongs, serverUrl ->
        val mappedOnline = onlineSongs
            .filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artistName.contains(query, ignoreCase = true) ||
                        (it.playlistName?.contains(query, ignoreCase = true) == true)
            }
            .map { it.toSongEntity(serverUrl) }
        localResults + mappedOnline
    }

    fun observeFavorites(): Flow<List<SongEntity>> = combine(
        songDao.observeFavorites(),
        onlineSongDao.observeFavorites(),
        downloadRepo.serverUrlFlow
    ) { localFavs, onlineFavs, serverUrl ->
        val mappedOnline = onlineFavs.map { it.toSongEntity(serverUrl) }
        localFavs + mappedOnline
    }

    fun observeCount(): Flow<Int> = combine(
        songDao.observeCount(),
        onlineSongDao.observeCount()
    ) { localCount, onlineCount -> localCount + onlineCount }

    suspend fun getSong(id: Long): SongEntity? {
        if (ContentId.isLegacyProviderId(id)) {
            val online = onlineSongDao.getAll().firstOrNull { entity ->
                entity.toSongEntity(downloadRepo.localHttpServer.baseUrl).songId == id
            } ?: return null
            val serverUrl = downloadRepo.serverUrlFlow.first()
            return online.toSongEntity(serverUrl)
        }
        return songDao.getById(id)
    }

    suspend fun insertSong(song: SongEntity) = songDao.insert(song)

    suspend fun toggleFavorite(song: SongEntity, fav: Boolean): SongEntity {
        val existing = if (song.songId > 0) songDao.getById(song.songId) else null
        val byUrl = if (existing == null && song.fileUri.isNotBlank()) songDao.getBySourceUrl(song.fileUri) else null
        val target = existing ?: byUrl

        val finalSong = if (target != null) {
            val updated = target.copy(isFavorite = fav)
            songDao.update(updated)
            updated
        } else {
            val newSong = song.copy(songId = 0, isFavorite = fav, sourceUrl = song.fileUri, sourceType = "online")
            val newId = songDao.insert(newSong)
            newSong.copy(songId = newId)
        }

        if (ContentId.isLegacyProviderId(song.songId)) {
            val onlineId = song.sourceUrl?.let { onlineSongDao.getBySourceUrl(it)?.onlineId }
            if (onlineId != null) try { onlineSongDao.setFavorite(onlineId, fav) } catch (_: Exception) {}
        }
        return finalSong
    }

    suspend fun toggleFavorite(id: Long, fav: Boolean) {
        val song = getSong(id)
        if (song != null) {
            toggleFavorite(song, fav)
        } else {
            songDao.setFavorite(id, fav)
        }
    }

    suspend fun incrementPlayCount(id: Long) {
        if (ContentId.isLegacyProviderId(id)) {
            val onlineId = onlineSongDao.getAll().firstOrNull {
                it.toSongEntity(downloadRepo.localHttpServer.baseUrl).songId == id
            }?.onlineId
            if (onlineId != null) onlineSongDao.updateLastPlayed(onlineId)
        } else {
            songDao.incrementPlayCount(id)
        }
    }

    suspend fun deleteSong(id: Long) {
        if (ContentId.isLegacyProviderId(id)) {
            val onlineId = onlineSongDao.getAll().firstOrNull {
                it.toSongEntity(downloadRepo.localHttpServer.baseUrl).songId == id
            }?.onlineId
            if (onlineId != null) onlineSongDao.deleteById(onlineId)
        } else {
            songDao.deleteById(id)
        }
    }

    fun observePlaylists(): Flow<List<Playlist>> = playlistDao.observeAll()
    fun observePlaylistsWithSongs(): Flow<List<PlaylistWithSongs>> = playlistDao.observeAllWithSongs()
    fun observePlaylistWithSongs(id: Long): Flow<PlaylistWithSongs?> = combine(
        playlistDao.observeWithSongs(id),
        playlistDao.observeSongsInOrder(id)
    ) { playlist, orderedSongs -> playlist?.copy(songs = orderedSongs) }

    suspend fun createPlaylist(name: String, isAuto: Boolean = false): Long =
        playlistDao.insert(Playlist(name = name.trim(), isAuto = isAuto))

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isNotBlank()) {
            playlistDao.updateName(playlistId, newName.trim())
        }
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.clearPlaylist(playlistId)
        playlistDao.deleteById(playlistId)
    }

    suspend fun addToPlaylist(playlistId: Long, song: SongEntity) {
        var effectiveSongId = song.songId
        if (ContentId.isLegacyProviderId(effectiveSongId)) {
            val existing = song.sourceUrl?.let { songDao.getBySourceUrl(it) }
            if (existing != null) {
                effectiveSongId = existing.songId
            } else {
                effectiveSongId = songDao.insert(song.copy(songId = 0))
            }
        }
        val pos = (playlistDao.maxPosition(playlistId) ?: -1) + 1
        playlistDao.insertCrossRef(PlaylistSongCrossRef(playlistId, effectiveSongId, pos))
        playlistDao.updateUpdatedAt(playlistId)
    }

    suspend fun addToPlaylist(playlistId: Long, songId: Long) {
        val song = getSong(songId)
        if (song != null) {
            addToPlaylist(playlistId, song)
        } else {
            val pos = (playlistDao.maxPosition(playlistId) ?: -1) + 1
            playlistDao.insertCrossRef(PlaylistSongCrossRef(playlistId, songId, pos))
            playlistDao.updateUpdatedAt(playlistId)
        }
    }

    suspend fun addMultipleToPlaylist(playlistId: Long, songs: List<SongEntity>) {
        songs.forEach { addToPlaylist(playlistId, it) }
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongAndCompact(playlistId, songId)
    }

    fun observeSongsByArtist(artistName: String): Flow<List<SongEntity>> =
        songDao.observeByArtist(artistName)

    fun observeSongsByAlbum(albumName: String): Flow<List<SongEntity>> =
        songDao.observeByAlbum(albumName)

    fun observeArtists(): Flow<List<String>> =
        songDao.observeArtists()

    fun observeAlbums(): Flow<List<String>> =
        songDao.observeAlbums()
}
