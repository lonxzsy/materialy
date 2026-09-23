package com.materialy.music.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.materialy.music.data.audio.AudioSettingsRepository
import com.materialy.music.data.audio.EqualizerPreset
import com.materialy.music.data.download.DownloadRepository
import com.materialy.music.data.repository.MusicRepository
import com.materialy.music.data.repository.OnlineRepository
import com.materialy.music.ui.components.ToastManager
import com.materialy.music.ui.screens.online.BackendStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepo: DownloadRepository,
    private val onlineRepo: OnlineRepository,
    private val musicRepo: MusicRepository,
    private val audioSettingsRepo: AudioSettingsRepository,
    val updateManager: com.materialy.music.updater.AppUpdateManager
) : ViewModel() {

    val updateState = updateManager.updateState

    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkForUpdates(silent = false)
        }
    }

    fun dismissUpdate() {
        updateManager.dismissUpdate()
    }

    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080")
    val serverUrl: StateFlow<String> = _serverUrl

    val isStandaloneMode = downloadRepo.isStandaloneModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customServerUrl = downloadRepo.customServerUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "http://127.0.0.1:8080")

    private val _backendStatus = MutableStateFlow<BackendStatus>(BackendStatus.Checking)
    val backendStatus: StateFlow<BackendStatus> = _backendStatus

    val localSongs = musicRepo.observeLocalSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineSongs = onlineRepo.observeOnlineSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isClearingCache = MutableStateFlow(false)
    val isClearingCache: StateFlow<Boolean> = _isClearingCache

    // Equalizer & Audio Enhancement
    val equalizerEnabled = audioSettingsRepo.equalizerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val selectedPresetName = audioSettingsRepo.selectedPresetNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Обычный (Flat)")

    val bandLevels = audioSettingsRepo.bandLevelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(0, 0, 0, 0, 0))

    val bassBoost = audioSettingsRepo.bassBoostFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val virtualizer = audioSettingsRepo.virtualizerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val customPresets = audioSettingsRepo.customPresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val builtInPresets = AudioSettingsRepository.BUILT_IN_PRESETS

    // Smooth Audio (Fade & Transitions)
    val smoothAudioEnabled = audioSettingsRepo.smoothAudioEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val smoothAudioDurationMs = audioSettingsRepo.smoothAudioDurationMsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000L)

    // Absolute Volume (Hardware Gain Boost)
    val absoluteVolumeEnabled = audioSettingsRepo.absoluteVolumeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val absoluteVolumeBoostDb = audioSettingsRepo.absoluteVolumeBoostDbFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6)

    init {
        viewModelScope.launch {
            downloadRepo.serverUrlFlow.collect { url ->
                _serverUrl.value = url
                checkBackend(url)
            }
        }
    }

    fun setStandaloneMode(enabled: Boolean) = viewModelScope.launch {
        downloadRepo.setStandaloneMode(enabled)
        val msg = if (enabled) "Встроенный автономный сервер активирован" else "Режим внешнего ПК сервера активирован"
        ToastManager.info(msg)
    }

    fun saveServerUrl(url: String) = viewModelScope.launch {
        val clean = downloadRepo.normalizeUrl(url)
        downloadRepo.saveServerUrl(clean)
        _serverUrl.value = clean
        checkBackend(clean)
    }

    fun checkBackend(url: String = _serverUrl.value) = viewModelScope.launch {
        _backendStatus.value = BackendStatus.Checking
        try {
            val res = onlineRepo.checkHealth(url)
            val version = res.yt_dlp ?: "ok"
            _backendStatus.value = BackendStatus.Connected(ytDlpVersion = version, activeJobs = res.jobs)
        } catch (e: Exception) {
            val err = e.localizedMessage ?: e.message ?: "Сервер недоступен"
            _backendStatus.value = BackendStatus.Disconnected(error = err)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) = viewModelScope.launch {
        audioSettingsRepo.setEqualizerEnabled(enabled)
        val msg = if (enabled) "Эквалайзер включен" else "Эквалайзер отключен"
        ToastManager.info(msg)
    }

    fun setPreset(preset: EqualizerPreset) = viewModelScope.launch {
        audioSettingsRepo.setPreset(preset)
        ToastManager.playback("Пресет: ${preset.name}")
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) = viewModelScope.launch {
        audioSettingsRepo.setBandLevel(bandIndex, levelDb)
    }

    fun setBassBoost(strength: Int) = viewModelScope.launch {
        audioSettingsRepo.setBassBoost(strength)
    }

    fun setVirtualizer(strength: Int) = viewModelScope.launch {
        audioSettingsRepo.setVirtualizer(strength)
    }

    fun saveCustomPreset(name: String, levels: List<Int>) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        audioSettingsRepo.saveCustomPreset(name.trim(), levels)
        ToastManager.success("Пресет «$name» сохранён")
    }

    fun deleteCustomPreset(name: String) = viewModelScope.launch {
        audioSettingsRepo.deleteCustomPreset(name)
        ToastManager.info("Пресет «$name» удалён")
    }

    fun setSmoothAudioEnabled(enabled: Boolean) = viewModelScope.launch {
        audioSettingsRepo.setSmoothAudioEnabled(enabled)
        val msg = if (enabled) "Плавное звучание включено" else "Плавное звучание отключено"
        ToastManager.info(msg)
    }

    fun setSmoothAudioDurationMs(durationMs: Long) = viewModelScope.launch {
        audioSettingsRepo.setSmoothAudioDurationMs(durationMs)
    }

    fun setAbsoluteVolumeEnabled(enabled: Boolean) = viewModelScope.launch {
        audioSettingsRepo.setAbsoluteVolumeEnabled(enabled)
        val msg = if (enabled) "Абсолютная громкость включена" else "Абсолютная громкость отключена"
        ToastManager.info(msg)
    }

    fun setAbsoluteVolumeBoostDb(boostDb: Int) = viewModelScope.launch {
        audioSettingsRepo.setAbsoluteVolumeBoostDb(boostDb)
    }

    fun clearOnlineHistory() = viewModelScope.launch {
        onlineRepo.clearAll()
        ToastManager.info("История онлайн-треков очищена")
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache() = viewModelScope.launch {
        _isClearingCache.value = true
        withContext(Dispatchers.IO) {
            try {
                val imageLoader = ImageLoader(context)
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            } catch (_: Exception) {}
        }
        _isClearingCache.value = false
        ToastManager.success("Кэш обложек успешно очищен")
    }
}
