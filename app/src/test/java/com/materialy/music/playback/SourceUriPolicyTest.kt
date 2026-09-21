package com.materialy.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceUriPolicyTest {
    @Test
    fun `only youtube page urls require extraction`() {
        assertTrue(SourceUriPolicy.requiresYouTubeResolution("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(SourceUriPolicy.requiresYouTubeResolution("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(SourceUriPolicy.requiresYouTubeResolution("dQw4w9WgXcQ"))
    }

    @Test
    fun `local backend and ordinary sources bypass youtube extraction`() {
        assertFalse(SourceUriPolicy.requiresYouTubeResolution("http://127.0.0.1:8765/stream?url=x"))
        assertFalse(SourceUriPolicy.requiresYouTubeResolution("content://media/external/audio/media/42"))
        assertFalse(SourceUriPolicy.requiresYouTubeResolution("file:///storage/emulated/0/Music/song.mp3"))
        assertFalse(SourceUriPolicy.requiresYouTubeResolution("https://example.com/song.mp3"))
    }

    @Test
    fun `direct cdn urls bypass extraction`() {
        assertFalse(SourceUriPolicy.requiresYouTubeResolution("https://rr1---sn.example.googlevideo.com/videoplayback?id=1"))
        assertFalse(SourceUriPolicy.requiresYouTubeResolution("https://cf-media.sndcdn.com/audio.mp3"))
    }

    @Test
    fun `download remains local even when original youtube url is retained`() {
        assertFalse(SourceUriPolicy.isRemoteTrack("download", "https://youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(SourceUriPolicy.isRemoteTrack("online", "https://youtube.com/watch?v=dQw4w9WgXcQ"))

        val selected = SourceUriPolicy.selectInput(
            sourceType = "download",
            sourceUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            storedUri = "file:///downloads/song.m4a",
            resolvedLocalUri = "file:///downloads/song.m4a"
        )
        org.junit.Assert.assertEquals("file:///downloads/song.m4a", selected)
    }

    @Test
    fun `online row ignores stale stored stream and uses stable source url`() {
        val selected = SourceUriPolicy.selectInput(
            sourceType = "online",
            sourceUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            storedUri = "https://expired.googlevideo.com/audio",
            resolvedLocalUri = "https://expired.googlevideo.com/audio"
        )
        org.junit.Assert.assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", selected)
    }
}
