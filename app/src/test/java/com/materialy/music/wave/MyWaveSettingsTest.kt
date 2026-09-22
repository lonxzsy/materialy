package com.materialy.music.wave

import com.materialy.music.data.repository.MyWaveVibe
import com.materialy.music.ui.screens.wave.MyWaveSettings
import com.materialy.music.ui.screens.wave.WaveCharacter
import com.materialy.music.ui.screens.wave.WaveLanguage
import com.materialy.music.ui.screens.wave.WaveMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MyWaveSettingsTest {

    @Test
    fun defaultSettings_hasBalancedDefaults() {
        val settings = MyWaveSettings()
        assertEquals(WaveCharacter.BALANCED, settings.character)
        assertEquals(WaveLanguage.ANY, settings.language)
        assertEquals(WaveMood.ALL, settings.mood)
    }

    @Test
    fun customSettings_preservesAllAttributes() {
        val settings = MyWaveSettings(
            character = WaveCharacter.DISCOVERY_ONLY,
            language = WaveLanguage.RUSSIAN,
            mood = WaveMood.CALM
        )
        assertEquals(WaveCharacter.DISCOVERY_ONLY, settings.character)
        assertEquals(WaveLanguage.RUSSIAN, settings.language)
        assertEquals(WaveMood.CALM, settings.mood)
    }

    @Test
    fun allVibes_haveNonEmptyLabels() {
        MyWaveVibe.entries.forEach { vibe ->
            assertNotNull(vibe.title)
            assertNotNull(vibe.promptSuffix)
        }
    }

    @Test
    fun titleNormalization_stripsOfficialVideoAndJunk() {
        val raw1 = "Never Gonna Give You Up (Official Music Video)"
        val raw2 = "Take On Me [2017 Remastered] (Audio)"
        val raw3 = "Blinding Lights feat. Rosalia [Lyric Video]"

        assertEquals("never gonna give you up", com.materialy.music.data.repository.MyWaveRepository.normalizeTitle(raw1))
        assertEquals("take on me", com.materialy.music.data.repository.MyWaveRepository.normalizeTitle(raw2))
        assertEquals("blinding lights", com.materialy.music.data.repository.MyWaveRepository.normalizeTitle(raw3))
    }

    @Test
    fun artistNormalization_stripsTopicAndVevo() {
        val art1 = "Rick Astley - Topic"
        val art2 = "TheWeekndVEVO"
        val art3 = "Yves Tumor"

        assertEquals("rick astley", com.materialy.music.data.repository.MyWaveRepository.normalizeArtist(art1))
        assertEquals("theweeknd", com.materialy.music.data.repository.MyWaveRepository.normalizeArtist(art2))
        assertEquals("yves tumor", com.materialy.music.data.repository.MyWaveRepository.normalizeArtist(art3))
    }

    @Test
    fun isDuplicateTitle_detectsDuplicatesAndSubstrings() {
        val existing = setOf("limerence", "take on me", "never gonna give you up")

        org.junit.Assert.assertTrue(com.materialy.music.data.repository.MyWaveRepository.isDuplicateTitle("limerence", existing))
        org.junit.Assert.assertTrue(com.materialy.music.data.repository.MyWaveRepository.isDuplicateTitle("take on me live", existing))
        org.junit.Assert.assertFalse(com.materialy.music.data.repository.MyWaveRepository.isDuplicateTitle("strawberry privilege", existing))
    }

    @Test
    fun extractVideoId_handlesUrlsAndRawIds() {
        assertEquals("dQw4w9WgXcQ", com.materialy.music.data.repository.MyWaveRepository.extractVideoId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", com.materialy.music.data.repository.MyWaveRepository.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", com.materialy.music.data.repository.MyWaveRepository.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }
}
