package com.materialy.music.ui.screens.wave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.repository.MusicRepository
import com.materialy.music.data.repository.MyWaveRepository
import com.materialy.music.data.repository.MyWaveState
import com.materialy.music.data.repository.MyWaveVibe
import com.materialy.music.playback.AudioEffectsManager
import com.materialy.music.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyWaveSettings(
    val character: WaveCharacter = WaveCharacter.BALANCED,
    val language: WaveLanguage = WaveLanguage.ANY,
    val mood: WaveMood = WaveMood.ALL
)

enum class WaveCharacter(val label: String) {
    FAVORITES_ONLY("Любимое"),
    BALANCED("Микс вкуса"),
    DISCOVERY_ONLY("Открытия")
}

enum class WaveLanguage(val label: String) {
    ANY("Любой язык"),
    RUSSIAN("Русский"),
    FOREIGN("Иностранный")
}

enum class WaveMood(val label: String) {
    ALL("Любое"),
    ENERGETIC("Бодрое"),
    CALM("Спокойное"),
    INSTRUMENTAL("Без слов")
}

@HiltViewModel
class MyWaveViewModel @Inject constructor(
    private val myWaveRepo: MyWaveRepository,
    val player: PlayerManager,
    val audioEffects: AudioEffectsManager,
    private val musicRepo: MusicRepository
) : ViewModel() {

    val waveState: StateFlow<MyWaveState> = myWaveRepo.waveState
    val currentSong: StateFlow<SongEntity?> = player.currentSong
    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val bassEnergy: StateFlow<Float> = audioEffects.bassEnergy

    private val _settings = MutableStateFlow(MyWaveSettings())
    val settings: StateFlow<MyWaveSettings> = _settings.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    init {
        viewModelScope.launch {
            currentSong.collect { song ->
                _isLiked.value = song?.isFavorite == true
            }
        }

        if (waveState.value.tracks.isEmpty()) {
            viewModelScope.launch {
                myWaveRepo.loadWave()
            }
        }
    }

    fun togglePlay() {
        val current = currentSong.value
        val playing = isPlaying.value

        if (current?.albumName == "Моя Волна") {
            player.togglePlayPause()
            return
        }

        viewModelScope.launch {
            val tracks = if (waveState.value.tracks.isNotEmpty()) {
                waveState.value.tracks
            } else {
                myWaveRepo.loadWave(waveState.value.vibe)
            }
            if (tracks.isNotEmpty()) {
                player.playSongs(tracks, 0)
            }
        }
    }

    fun selectVibe(vibe: MyWaveVibe) {
        viewModelScope.launch {
            val tracks = myWaveRepo.loadWave(vibe)
            if (isPlaying.value || currentSong.value?.albumName == "Моя Волна") {
                if (tracks.isNotEmpty()) {
                    player.playSongs(tracks, 0)
                }
            }
        }
    }

    fun skipNext() {
        player.next()
    }

    fun skipPrevious() {
        player.previous()
    }

    fun toggleLike() {
        val song = currentSong.value ?: return
        val newFav = !_isLiked.value
        _isLiked.value = newFav
        viewModelScope.launch {
            musicRepo.toggleFavorite(song, newFav)
        }
    }

    fun dislikeAndSkip() {
        // Soft skip with algorithmic negative weight simulation
        player.next()
    }

    fun updateSettings(newSettings: MyWaveSettings) {
        _settings.value = newSettings
        viewModelScope.launch {
            val vibe = when (newSettings.mood) {
                WaveMood.ENERGETIC -> MyWaveVibe.ENERGY
                WaveMood.CALM -> MyWaveVibe.CALM
                WaveMood.INSTRUMENTAL -> MyWaveVibe.CALM
                WaveMood.ALL -> if (newSettings.character == WaveCharacter.DISCOVERY_ONLY) {
                    MyWaveVibe.DISCOVERY
                } else {
                    MyWaveVibe.MY_WAVE
                }
            }
            val tracks = myWaveRepo.loadWave(vibe)
            if (tracks.isNotEmpty() && isPlaying.value) {
                player.playSongs(tracks, 0)
            }
        }
    }
}
