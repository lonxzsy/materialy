package com.materialy.music.playback

import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PlaybackSessionSerializationTest {

    @Test
    fun testDetailRouteUrlEncodingDecoding() {
        val albumName = "Dark Side of the Moon / Deluxe & Live (2023)"
        val encoded = URLEncoder.encode(albumName, StandardCharsets.UTF_8.name())
        val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        assertEquals(albumName, decoded)

        val artistName = "AC/DC & Guns N' Roses"
        val encodedArtist = URLEncoder.encode(artistName, StandardCharsets.UTF_8.name())
        val decodedArtist = URLDecoder.decode(encodedArtist, StandardCharsets.UTF_8.name())
        assertEquals(artistName, decodedArtist)
    }

    @Test
    fun testContentIdEncodingAndLegacyMapping() {
        val youtubeId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, "dQw4w9WgXcQ")
        val encoded = youtubeId.encode()
        val decoded = ContentId.decode(encoded)
        assertNotNull(decoded)
        assertEquals(youtubeId, decoded)

        val localId = ContentId(ContentProvider.LOCAL, ContentType.TRACK, "12345")
        val decodedLocal = ContentId.decode(localId.encode())
        assertNotNull(decodedLocal)
        assertEquals(localId, decodedLocal)
    }
}
