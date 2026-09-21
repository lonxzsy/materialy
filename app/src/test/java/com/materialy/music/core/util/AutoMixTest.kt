package com.materialy.music.core.util

import com.materialy.music.data.db.entity.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMixTest {
    private fun song(id: Long, artist: String) = SongEntity(songId = id, title = "song$id", artistName = artist)

    @Test
    fun smartShuffle_keepsEverySongExactlyOnce() {
        val input = listOf(song(1, "A"), song(2, "A"), song(3, "A"), song(4, "B"), song(5, "C"))
        val output = AutoMix.smartShuffle(input)
        assertEquals(input.map { it.songId }.toSet(), output.map { it.songId }.toSet())
        assertEquals(input.size, output.size)
    }

    @Test
    fun smartShuffle_avoidsAnArtistRepeatWhenAlternativeExists() {
        val input = listOf(song(1, "A"), song(2, "A"), song(3, "B"), song(4, "C"))
        val output = AutoMix.smartShuffle(input)
        output.zipWithNext().forEach { (a, b) ->
            if (a.artistName == b.artistName) {
                assertTrue(output.dropWhile { it != a }.drop(1).none { it.artistName != a.artistName })
            }
        }
    }
}
