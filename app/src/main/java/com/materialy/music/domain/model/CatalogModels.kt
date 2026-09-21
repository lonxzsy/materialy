package com.materialy.music.domain.model

data class Track(val id: ContentId, val title: String, val artist: String, val albumId: ContentId? = null)
data class Album(val id: ContentId, val title: String, val artist: String, val tracks: List<Track> = emptyList())
data class Artist(val id: ContentId, val name: String)
data class Playlist(val id: ContentId, val title: String, val tracks: List<Track> = emptyList())
data class PlayableSource(val id: ContentId, val uri: String, val mimeType: String? = null)
data class LibraryItem(val id: ContentId, val savedAt: Long)

enum class PlaybackEventType { START, COMPLETE, SKIP, LIKE, SAVE, SEARCH_CLICK }
data class PlaybackEvent(val type: PlaybackEventType, val id: ContentId, val occurredAt: Long)

enum class AudioQuality { AUTO, LOW, MEDIUM, HIGH }
data class Page<T>(val items: List<T>, val nextCursor: String? = null)
sealed interface SearchItem { val id: ContentId }
data class AlbumDetails(val album: Album)
data class ArtistDetails(val artist: Artist, val topTracks: List<Track>)
data class PlaylistDetails(val playlist: Playlist)
