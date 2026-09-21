package com.materialy.music.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType
import com.materialy.music.data.download.SearchResultItem
import com.materialy.music.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.materialy.music.data.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

data class SearchCategory(
    val title: String,
    val colorHex: Long,
    val query: String
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val extractor: InnertubeExtractor,
    private val searchHistoryRepo: SearchHistoryRepository,
    val player: PlayerManager
) : ViewModel() {

    val recentSearches: StateFlow<List<String>> = searchHistoryRepo.recentQueries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun removeRecentSearch(q: String) = searchHistoryRepo.removeSearch(q)
    fun clearRecentSearches() = searchHistoryRepo.clearAll()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val results: StateFlow<List<SearchResultItem>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    val categories = listOf(
        SearchCategory("Поп", 0xFFE91E63, "Поп музыка 2024"),
        SearchCategory("Хип-хоп & Рэп", 0xFFFF5722, "Хип хоп рэп"),
        SearchCategory("Рок & Альтернатива", 0xFF673AB7, "Рок музыка"),
        SearchCategory("Lo-Fi & Chill", 0xFF009688, "Lo-Fi Beats"),
        SearchCategory("Электроника & EDM", 0xFF00BCD4, "EDM Dance Music"),
        SearchCategory("Фонк", 0xFF9C27B0, "Phonk drift"),
        SearchCategory("Тренды TikTok", 0xFFFF4081, "Тренды Тикток музыка"),
        SearchCategory("Классика & Саундтреки", 0xFF795548, "OST Soundtracks")
    )

    private var suggestJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        suggestJob?.cancel()

        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            _results.value = emptyList()
            _errorMessage.value = null
            _hasSearched.value = false
            return
        }

        suggestJob = viewModelScope.launch {
            delay(250) // Debounce
            runCatching { extractor.getSearchSuggestions(newQuery) }
                .onSuccess { _suggestions.value = it }
        }
    }

    fun performSearch(queryToSearch: String? = null) {
        val q = (queryToSearch ?: _query.value).trim()
        if (q.isBlank()) return
        _query.value = q
        _suggestions.value = emptyList()
        _errorMessage.value = null
        _hasSearched.value = true
        searchHistoryRepo.recordSearch(q)

        viewModelScope.launch {
            _isSearching.value = true
            try {
                val res = extractor.search(q, limit = 25)
                _results.value = res
            } catch (e: Exception) {
                _results.value = emptyList()
                _errorMessage.value = "Не удалось выполнить поиск. Проверьте подключение и повторите попытку."
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun playResult(item: SearchResultItem) {
        val song = SongEntity(
            songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, item.id).legacySongId(),
            title = item.title,
            artistName = item.uploader,
            albumName = "Онлайн поиск",
            durationMs = item.duration * 1000L,
            artworkPath = item.thumbnail,
            fileUri = item.url,
            sourceType = "online"
        )
        player.playSongs(listOf(song), 0)
    }
}
