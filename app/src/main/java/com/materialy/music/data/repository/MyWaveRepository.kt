package com.materialy.music.data.repository

import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.data.db.dao.OnlineSongDao
import com.materialy.music.data.db.dao.SongDao
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType
import com.materialy.music.domain.model.ShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class MyWaveVibe(val title: String, val promptSuffix: String) {
    MY_WAVE("Мой вайб", "микс треков"),
    ENERGY("Бодрое", "энергичные треки драйв"),
    CALM("Спокойное", "спокойная музыка чилл"),
    DISCOVERY("Открытия", "новые похожие исполнители")
}

data class MyWaveState(
    val vibe: MyWaveVibe = MyWaveVibe.MY_WAVE,
    val description: String = "Персональный поток под ваше настроение",
    val tracks: List<SongEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null
)

@Singleton
class MyWaveRepository @Inject constructor(
    private val extractor: InnertubeExtractor,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val songDao: SongDao,
    private val onlineSongDao: OnlineSongDao
) {
    private val _waveState = MutableStateFlow(MyWaveState())
    val waveState: StateFlow<MyWaveState> = _waveState.asStateFlow()

    suspend fun loadWave(vibe: MyWaveVibe = _waveState.value.vibe): List<SongEntity> = withContext(Dispatchers.IO) {
        _waveState.value = _waveState.value.copy(vibe = vibe, isGenerating = true, error = null)
        try {
            // 1. Collect user affinities
            val recentSearches = searchHistoryRepo.getRecentQueries(limit = 6)
            val favLocal = runCatching { songDao.getFavorites() }.getOrDefault(emptyList())
            val favOnline = runCatching { onlineSongDao.getAll().filter { it.isFavorite } }.getOrDefault(emptyList())

            val favArtists = (favLocal.map { it.artistName } + favOnline.map { it.artistName })
                .filter { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) }
                .distinct()
                .take(6)

            val recentPlayed = runCatching { songDao.observeRecentlyPlayed(10).first() }.getOrDefault(emptyList())
            val recentArtists = recentPlayed.map { it.artistName }.filter { it.isNotBlank() }.distinct().take(4)

            // 2. Build personalized search seeds based on user actions
            val searchSeeds = mutableListOf<String>()
            searchSeeds.addAll(recentSearches.take(3))
            searchSeeds.addAll(favArtists.take(3))
            searchSeeds.addAll(recentArtists.take(2))

            val distinctSeeds = searchSeeds.distinct()
            val description = if (distinctSeeds.isNotEmpty()) {
                val seedSample = distinctSeeds.take(2).joinToString(", ")
                when (vibe) {
                    MyWaveVibe.MY_WAVE -> "На основе ваших интересов: $seedSample"
                    MyWaveVibe.ENERGY -> "Бодрый поток по вашим вкусам: $seedSample"
                    MyWaveVibe.CALM -> "Спокойный поток по вашим вкусам: $seedSample"
                    MyWaveVibe.DISCOVERY -> "Новые треки и артисты в стиле $seedSample"
                }
            } else {
                when (vibe) {
                    MyWaveVibe.MY_WAVE -> "Бесконечный персональный поток музыки"
                    MyWaveVibe.ENERGY -> "Энергичный поток для активности и тренировок"
                    MyWaveVibe.CALM -> "Уютный чилловый поток для отдыха"
                    MyWaveVibe.DISCOVERY -> "Свежие открытия и новые имена"
                }
            }

            // 3. Construct queries for Innertube
            val queriesToFetch = if (distinctSeeds.isNotEmpty()) {
                distinctSeeds.map { seed -> "$seed ${vibe.promptSuffix}" }.take(3)
            } else {
                when (vibe) {
                    MyWaveVibe.MY_WAVE -> listOf("Хиты инди поп рок 2024", "Лучшая музыка радио микс")
                    MyWaveVibe.ENERGY -> listOf("Энергичный рок поп драйв", "Phonk workout energetic music")
                    MyWaveVibe.CALM -> listOf("Lo-Fi acoustic chill beats", "Спокойная красивая музыка")
                    MyWaveVibe.DISCOVERY -> listOf("Новая интересная музыка открытия", "Underrated indie artists songs")
                }
            }

            // 4. Fetch and deduplicate tracks
            val allItems = mutableListOf<SongEntity>()
            val seenIds = mutableSetOf<String>()

            for (query in queriesToFetch) {
                val results = runCatching { extractor.search(query, limit = 12) }.getOrDefault(emptyList())
                for (item in results) {
                    if (seenIds.add(item.id)) {
                        allItems.add(
                            SongEntity(
                                songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, item.id).legacySongId(),
                                title = item.title,
                                artistName = item.uploader,
                                albumName = "Моя Волна",
                                durationMs = item.duration * 1000L,
                                artworkPath = item.thumbnail,
                                fileUri = item.url,
                                sourceUrl = item.url,
                                sourceType = "online"
                            )
                        )
                    }
                }
            }

            // If empty, fallback to rich catalog
            if (allItems.isEmpty()) {
                val fallback = extractor.search("Лучшие песни радио микс", limit = 15)
                for (item in fallback) {
                    if (seenIds.add(item.id)) {
                        allItems.add(
                            SongEntity(
                                songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, item.id).legacySongId(),
                                title = item.title,
                                artistName = item.uploader,
                                albumName = "Моя Волна",
                                durationMs = item.duration * 1000L,
                                artworkPath = item.thumbnail,
                                fileUri = item.url,
                                sourceUrl = item.url,
                                sourceType = "online"
                            )
                        )
                    }
                }
            }

            val shuffledTracks = allItems.shuffled()
            _waveState.value = MyWaveState(
                vibe = vibe,
                description = description,
                tracks = shuffledTracks,
                isGenerating = false,
                error = null
            )
            shuffledTracks
        } catch (e: Exception) {
            _waveState.value = _waveState.value.copy(
                isGenerating = false,
                error = "Не удалось обновить Мою Волну. Проверьте интернет."
            )
            emptyList()
        }
    }
}
