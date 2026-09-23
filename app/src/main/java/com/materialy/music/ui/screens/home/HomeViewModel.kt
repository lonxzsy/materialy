package com.materialy.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.repository.MyWaveRepository
import com.materialy.music.data.repository.MyWaveState
import com.materialy.music.data.repository.MyWaveVibe
import com.materialy.music.data.repository.SearchHistoryRepository
import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType
import com.materialy.music.domain.model.HomeFeedData
import com.materialy.music.domain.model.HomeShelf
import com.materialy.music.domain.model.ShelfItem
import com.materialy.music.domain.model.ShelfType
import com.materialy.music.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeCategory(
    val id: String,
    val title: String,
    val query: String
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractor: InnertubeExtractor,
    private val myWaveRepo: MyWaveRepository,
    private val searchHistoryRepo: SearchHistoryRepository,
    val player: PlayerManager
) : ViewModel() {

    val waveState: StateFlow<MyWaveState> = myWaveRepo.waveState

    val recentSearches: StateFlow<List<com.materialy.music.data.repository.SearchHistoryEntry>> = searchHistoryRepo.recentEntries

    val isPlaying: StateFlow<Boolean> = player.isPlaying
    val currentSong: StateFlow<SongEntity?> = player.currentSong
    val bassEnergy: StateFlow<Float> = player.bassEnergy

    private val _feedData = MutableStateFlow<HomeFeedData?>(null)
    val feedData: StateFlow<HomeFeedData?> = _feedData.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow("all")
    val selectedCategoryId: StateFlow<String> = _selectedCategoryId.asStateFlow()

    val categories = listOf(
        HomeCategory("all", "Все", ""),
        HomeCategory("energy", "Энергия", "Энергичная музыка"),
        HomeCategory("relax", "Релакс", "Релакс музыка Lo-Fi"),
        HomeCategory("travel", "В дорогу", "Музыка в дорогу"),
        HomeCategory("focus", "Фокус", "Музыка для концентрации"),
        HomeCategory("party", "Вечеринка", "Клубная музыка")
    )

    init {
        loadFeed()
        viewModelScope.launch {
            myWaveRepo.loadWave()
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val shelves = mutableListOf<HomeShelf>()

                // 1. Personalized Shelf: Based on user's recent searches
                val userSearches = searchHistoryRepo.getRecentQueries(limit = 4)
                if (userSearches.isNotEmpty()) {
                    val primarySearch = userSearches.first()
                    val recommendedItems = extractor.search("$primarySearch похожие треки", limit = 10).map {
                        ShelfItem(
                            id = it.id,
                            title = it.title,
                            subtitle = it.uploader,
                            thumbnail = it.thumbnail,
                            type = ShelfItem.ItemType.TRACK,
                            directUrl = it.url
                        )
                    }
                    if (recommendedItems.isNotEmpty()) {
                        shelves.add(
                            HomeShelf(
                                shelfId = "recommended_from_search",
                                title = "На основе поиска «$primarySearch»",
                                subtitle = "Похожие треки и исполнители",
                                shelfType = ShelfType.HORIZONTAL_CAROUSEL,
                                items = recommendedItems
                            )
                        )
                    }

                    if (userSearches.size > 1) {
                        val secondarySearch = userSearches[1]
                        val secondItems = extractor.search("$secondarySearch радио микс", limit = 10).map {
                            ShelfItem(
                                id = it.id,
                                title = it.title,
                                subtitle = it.uploader,
                                thumbnail = it.thumbnail,
                                type = ShelfItem.ItemType.TRACK,
                                directUrl = it.url
                            )
                        }
                        if (secondItems.isNotEmpty()) {
                            shelves.add(
                                HomeShelf(
                                    shelfId = "recommended_secondary",
                                    title = "В стиле «$secondarySearch»",
                                    subtitle = "Персональная подборка",
                                    shelfType = ShelfType.HORIZONTAL_CAROUSEL,
                                    items = secondItems
                                )
                            )
                        }
                    }
                }

                // 2. Discoveries / New Music for the user
                val discoveryQuery = if (userSearches.isNotEmpty()) {
                    "${userSearches.first()} новинки открытия"
                } else {
                    "Интересная новая музыка открытия"
                }
                val discoveryItems = extractor.search(discoveryQuery, limit = 10).map {
                    ShelfItem(
                        id = it.id,
                        title = it.title,
                        subtitle = it.uploader,
                        thumbnail = it.thumbnail,
                        type = ShelfItem.ItemType.TRACK,
                        directUrl = it.url
                    )
                }
                if (discoveryItems.isNotEmpty()) {
                    shelves.add(
                        HomeShelf(
                            shelfId = "discoveries",
                            title = "Новые открытия",
                            subtitle = "Свежая музыка, которая может вам понравиться",
                            shelfType = ShelfType.HORIZONTAL_CAROUSEL,
                            items = discoveryItems
                        )
                    )
                }

                // 3. Relax & Ambient Flow
                val relaxItems = extractor.search("Relaxing Atmospheric Ambient Beats", limit = 10).map {
                    ShelfItem(
                        id = it.id,
                        title = it.title,
                        subtitle = it.uploader,
                        thumbnail = it.thumbnail,
                        type = ShelfItem.ItemType.TRACK,
                        directUrl = it.url
                    )
                }
                if (relaxItems.isNotEmpty()) {
                    shelves.add(
                        HomeShelf(
                            shelfId = "relax_flow",
                            title = "Атмосфера и релакс",
                            subtitle = "Спокойные композиции для отдыха и работы",
                            shelfType = ShelfType.HORIZONTAL_CAROUSEL,
                            items = relaxItems
                        )
                    )
                }

                _feedData.value = HomeFeedData(shelves = shelves)
            } catch (e: Exception) {
                _errorMessage.value = "Не удалось загрузить рекомендации. Проверьте подключение к интернету."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectCategory(category: HomeCategory) {
        _selectedCategoryId.value = category.id
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                if (category.id == "all") {
                    loadFeed()
                } else {
                    val results = extractor.search(category.query, limit = 12).map {
                        ShelfItem(
                            id = it.id,
                            title = it.title,
                            subtitle = it.uploader,
                            thumbnail = it.thumbnail,
                            type = ShelfItem.ItemType.TRACK,
                            directUrl = it.url
                        )
                    }
                    val shelf = HomeShelf(
                        shelfId = "category_${category.id}",
                        title = category.title,
                        subtitle = "Подборка треков по настроению",
                        items = results
                    )
                    _feedData.value = HomeFeedData(shelves = listOf(shelf))
                }
            } catch (e: Exception) {
                _errorMessage.value = "Не удалось открыть подборку. Попробуйте ещё раз."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePlayWave() {
        val currentWave = waveState.value
        val isCurrentlyPlaying = isPlaying.value
        val currentTrack = currentSong.value

        // If current song is already from "Моя Волна", toggle play/pause
        if (currentTrack?.albumName == "Моя Волна" && isCurrentlyPlaying) {
            player.togglePlayPause()
            return
        }

        if (currentTrack?.albumName == "Моя Волна" && !isCurrentlyPlaying) {
            player.togglePlayPause()
            return
        }

        // Otherwise, start or resume Wave playback
        viewModelScope.launch {
            val tracks = if (currentWave.tracks.isNotEmpty()) {
                currentWave.tracks
            } else {
                myWaveRepo.loadWave()
            }
            if (tracks.isNotEmpty()) {
                player.playSongs(tracks, 0)
            }
        }
    }

    fun selectVibe(vibe: MyWaveVibe) {
        viewModelScope.launch {
            val tracks = myWaveRepo.loadWave(vibe)
            val currentTrack = currentSong.value
            // If already playing Wave, switch to new vibe immediately
            if (currentTrack?.albumName == "Моя Волна" && tracks.isNotEmpty()) {
                player.playSongs(tracks, 0)
            }
        }
    }

    fun skipNextWave() {
        player.next()
    }

    fun playTrack(item: ShelfItem) {
        val songEntity = SongEntity(
            songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, item.id).legacySongId(),
            title = item.title,
            artistName = item.subtitle,
            albumName = "Рекомендации",
            durationMs = 0L,
            artworkPath = item.thumbnail,
            fileUri = item.directUrl ?: "https://www.youtube.com/watch?v=${item.id}",
            sourceType = "online"
        )
        player.playSongs(listOf(songEntity), 0)
    }

    fun playSearchQuery(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val videoId = InnertubeExtractor.extractVideoId(query)
                val results = if (videoId != null) {
                    extractor.search(query, limit = 10)
                } else {
                    extractor.search("$query радио микс", limit = 15)
                }.map {
                    SongEntity(
                        songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, it.id).legacySongId(),
                        title = it.title,
                        artistName = it.uploader,
                        albumName = if (videoId != null) "YouTube" else "Поиск: $query",
                        durationMs = it.duration * 1000L,
                        artworkPath = it.thumbnail,
                        fileUri = it.url,
                        sourceUrl = it.url,
                        sourceType = "online"
                    )
                }
                if (results.isNotEmpty()) {
                    if (videoId != null) {
                        searchHistoryRepo.updateDisplayTitle(query, results[0].title)
                    }
                    player.playSongs(results, 0)
                }
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }
}
