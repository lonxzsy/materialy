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
}
