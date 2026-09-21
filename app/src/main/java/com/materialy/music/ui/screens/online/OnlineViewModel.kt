package com.materialy.music.ui.screens.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialy.music.core.util.AutoMix
import com.materialy.music.data.db.entity.OnlineSongEntity
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.download.SearchResultItem
import com.materialy.music.data.repository.OnlineRepository
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.components.ToastManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OnlineUiState {
    data object Idle : OnlineUiState
    data class Loading(val message: String) : OnlineUiState
    data class SearchResults(val query: String, val results: List<SearchResultItem>) : OnlineUiState
    data class Error(val message: String, val retryUrl: String? = null) : OnlineUiState
}

sealed interface BackendStatus {
    data object Checking : BackendStatus
    data class Connected(val ytDlpVersion: String, val activeJobs: Int) : BackendStatus
    data class Disconnected(val error: String) : BackendStatus
}

@HiltViewModel
class OnlineViewModel @Inject constructor(
    private val repo: OnlineRepository,
    val player: PlayerManager
) : ViewModel() {

    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080")
    val serverUrl: StateFlow<String> = _serverUrl

    val isStandaloneMode = repo.isStandaloneModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _backendStatus = MutableStateFlow<BackendStatus>(BackendStatus.Checking)
    val backendStatus: StateFlow<BackendStatus> = _backendStatus

    private val _uiState = MutableStateFlow<OnlineUiState>(OnlineUiState.Idle)
    val uiState: StateFlow<OnlineUiState> = _uiState

    val onlineSongs: StateFlow<List<OnlineSongEntity>> = repo.observeOnlineSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPlaying = player.isPlaying
    val currentSong = player.currentSong
    val currentPositionMs = player.currentPositionMs
    val durationMs = player.durationMs
    val bassEnergy = player.bassEnergy

    init {
        viewModelScope.launch {
            repo.serverUrlFlow.collect { url ->
                _serverUrl.value = url
                checkBackendConnection(url)
            }
        }
    }

    fun setStandaloneMode(enabled: Boolean) = viewModelScope.launch {
        repo.setStandaloneMode(enabled)
        val msg = if (enabled) "Встроенный бэкенд на телефоне активен" else "Режим внешнего ПК-сервера"
        ToastManager.info(msg)
    }

    fun saveServerUrl(url: String) = viewModelScope.launch {
        val clean = repo.normalizeUrl(url)
        repo.saveServerUrl(clean)
        _serverUrl.value = clean
        checkBackendConnection(clean)
    }

    fun checkBackendConnection(url: String = _serverUrl.value) = viewModelScope.launch {
        _backendStatus.value = BackendStatus.Checking
        try {
            val res = repo.checkHealth(url)
            val version = res.yt_dlp ?: "ok"
            _backendStatus.value = BackendStatus.Connected(ytDlpVersion = version, activeJobs = res.jobs)
            repo.checkBackendHealth(url)
        } catch (e: Exception) {
            val err = e.localizedMessage ?: e.message ?: "Сервер недоступен"
            _backendStatus.value = BackendStatus.Disconnected(error = err)
            repo.checkBackendHealth(url)
        }
    }

    fun handleInput(input: String) {
        val clean = input.trim()
        if (clean.isBlank()) return

        val isUrl = clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ||
                clean.contains("youtu.be", ignoreCase = true) ||
                clean.contains("youtube.com", ignoreCase = true) ||
                clean.contains("soundcloud.com", ignoreCase = true)

        if (isUrl) {
            resolveAndAddUrl(clean)
        } else {
            searchOnline(clean)
        }
    }

    fun resolveAndAddUrl(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return

        if (_backendStatus.value is BackendStatus.Disconnected) {
            ToastManager.error("Для стриминга требуется рабочий бэкенд. Проверьте подключение к серверу.")
            _uiState.value = OnlineUiState.Error("Бэкенд недоступен. Убедитесь, что сервер запущен и URL настроен верно.", cleanUrl)
            return
        }

        val isPlaylist = cleanUrl.contains("list=", ignoreCase = true) ||
                cleanUrl.contains("/playlist", ignoreCase = true) ||
                cleanUrl.contains("/sets/", ignoreCase = true)

        val loadingMsg = if (isPlaylist) "Загружаем плейлист и треки с сервера..." else "Получаем информацию об онлайн треке..."
        _uiState.value = OnlineUiState.Loading(loadingMsg)

        viewModelScope.launch {
            try {
                val res = repo.resolveOnline(_serverUrl.value, cleanUrl)
                if (res.items.isEmpty()) {
                    _uiState.value = OnlineUiState.Error("Сервер не нашел аудиопотоков по этой ссылке.", cleanUrl)
                    ToastManager.error("Не удалось найти аудио по этой ссылке")
                    return@launch
                }

                if (res.isPlaylist && res.items.size > 1) {
                    repo.addTracks(res.items, res.playlistTitle)
                    ToastManager.success("Плейлист «${res.playlistTitle ?: "Онлайн"}» добавлен (${res.itemCount} треков)!")
                } else {
                    val single = res.items.first()
                    repo.addTrack(single, res.playlistTitle)
                    ToastManager.success("Трек «${single.title}» добавлен в онлайн список!")
                }

                _uiState.value = OnlineUiState.Idle
            } catch (e: Exception) {
                val raw = e.localizedMessage ?: e.message ?: "Ошибка получения данных"
                val friendly = when {
                    raw.contains("404") -> "Сервер вернул 404. Проверьте адрес бэкенда."
                    raw.contains("500") || raw.contains("bot", ignoreCase = true) ->
                        "Ошибка извлечения аудиопотока.\nДля YouTube может потребоваться cookies.txt в папке сервера."
                    else -> "Ошибка сети или сервера: $raw"
                }
                _uiState.value = OnlineUiState.Error(friendly, cleanUrl)
                ToastManager.error("Ошибка добавления ссылки: $friendly")
            }
        }
    }

    private fun searchOnline(query: String) {
        if (_backendStatus.value is BackendStatus.Disconnected) {
            ToastManager.error("Для поиска требуется рабочий бэкенд.")
            _uiState.value = OnlineUiState.Error("Бэкенд недоступен. Проверьте адрес сервера.", query)
            return
        }

        _uiState.value = OnlineUiState.Loading("Ищем онлайн треки по запросу «$query»...")
        viewModelScope.launch {
            try {
                val res = repo.search(_serverUrl.value, query)
                if (res.results.isNotEmpty()) {
                    _uiState.value = OnlineUiState.SearchResults(query, res.results)
                } else {
                    _uiState.value = OnlineUiState.Error("Ничего не найдено по запросу «$query».", query)
                }
            } catch (e: Exception) {
                val err = e.localizedMessage ?: e.message ?: "Ошибка поиска"
                _uiState.value = OnlineUiState.Error("Ошибка поиска: $err", query)
            }
        }
    }

    fun playTrack(track: OnlineSongEntity) {
        val allTracks = onlineSongs.value
        if (allTracks.isEmpty()) return

        if (_backendStatus.value is BackendStatus.Disconnected) {
            ToastManager.error("Сервер оффлайн. Воспроизведение онлайн-треков невозможно.")
            return
        }

        viewModelScope.launch {
            repo.updateLastPlayed(track.onlineId)
            val songEntities = allTracks.map { it.toSongEntity(_serverUrl.value) }
            val index = allTracks.indexOfFirst { it.onlineId == track.onlineId }.coerceAtLeast(0)
            player.playSongs(songEntities, index)
            ToastManager.playback("Онлайн стриминг: ${track.title}")
        }
    }

    fun playAll(startIndex: Int = 0) {
        val allTracks = onlineSongs.value
        if (allTracks.isEmpty()) return

        if (_backendStatus.value is BackendStatus.Disconnected) {
            ToastManager.error("Сервер оффлайн. Воспроизведение онлайн-треков невозможно.")
            return
        }

        val songEntities = allTracks.map { it.toSongEntity(_serverUrl.value) }
        val safeIndex = startIndex.coerceIn(0, (songEntities.size - 1).coerceAtLeast(0))
        player.playSongs(songEntities, safeIndex)
        ToastManager.playback("Воспроизведение онлайн медиатеки")
    }

    fun shufflePlay() {
        val allTracks = onlineSongs.value
        if (allTracks.isEmpty()) return

        if (_backendStatus.value is BackendStatus.Disconnected) {
            ToastManager.error("Сервер оффлайн. Воспроизведение онлайн-треков невозможно.")
            return
        }

        val songEntities = allTracks.map { it.toSongEntity(_serverUrl.value) }
        val shuffled = AutoMix.smartShuffle(songEntities)
        player.playSongs(shuffled, 0)
        ToastManager.info("Онлайн очередь перемешана")
    }

    fun deleteTrack(track: OnlineSongEntity) = viewModelScope.launch {
        repo.deleteTrack(track.onlineId)
        ToastManager.info("Трек «${track.title}» удален из истории онлайн")
    }

    fun clearAll() = viewModelScope.launch {
        repo.clearAll()
        ToastManager.info("История онлайн треков очищена")
    }

    fun toggleFavorite(track: OnlineSongEntity) = viewModelScope.launch {
        repo.toggleFavorite(track.onlineId, !track.isFavorite)
        val msg = if (!track.isFavorite) "Добавлено в избранное" else "Удалено из избранного"
        ToastManager.success(msg)
    }

    fun resetState() {
        _uiState.value = OnlineUiState.Idle
    }

    fun next() = player.next()
    fun prev() = player.previous()
    fun togglePlay() = player.togglePlayPause()
}
