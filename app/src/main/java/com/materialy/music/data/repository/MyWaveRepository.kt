package com.materialy.music.data.repository

import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.data.db.dao.OnlineSongDao
import com.materialy.music.data.db.dao.SongDao
import com.materialy.music.data.db.entity.SongEntity
import com.materialy.music.data.download.SearchResultItem
import com.materialy.music.domain.model.ContentId
import com.materialy.music.domain.model.ContentProvider
import com.materialy.music.domain.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

enum class MyWaveVibe(val title: String, val promptSuffix: String) {
    MY_WAVE("Мой вайб", "radio mix"),
    ENERGY("Бодрое", "workout energy upbeat mix"),
    CALM("Спокойное", "acoustic chill lofi mix"),
    DISCOVERY("Открытия", "similar artists new music")
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

    // Cache resolved YouTube video IDs for local library songs so we don't re-search them repeatedly
    private val seedVideoCache = ConcurrentHashMap<String, String>()

    // Session history: tracks played, skipped or disliked during the current session
    private val sessionExcludedIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val sessionDislikedKeys = Collections.synchronizedSet(mutableSetOf<String>())

    fun registerDislike(song: SongEntity) {
        val cleanId = extractVideoId(song.sourceUrl ?: song.fileUri)
        if (cleanId != null) {
            sessionExcludedIds.add(cleanId)
        }
        val key = normalizeKey(song.artistName, song.title)
        sessionDislikedKeys.add(key)
    }

    fun registerPlayed(song: SongEntity) {
        val cleanId = extractVideoId(song.sourceUrl ?: song.fileUri)
        if (cleanId != null) {
            sessionExcludedIds.add(cleanId)
        }
    }

    suspend fun loadWave(vibe: MyWaveVibe = _waveState.value.vibe): List<SongEntity> = withContext(Dispatchers.IO) {
        _waveState.value = _waveState.value.copy(vibe = vibe, isGenerating = true, error = null)
        try {
            // 1. Gather all library tracks (local + online) to establish the taste profile & exclusion registry
            val localSongs = runCatching { songDao.getAll() }.getOrDefault(emptyList())
            val onlineSongs = runCatching { onlineSongDao.getAll() }.getOrDefault(emptyList())

            // 2. Build strict library exclusion indices
            // CRITICAL RULE: Under no circumstances should tracks already present in the user's library be re-recommended!
            val libraryVideoIds = mutableSetOf<String>()
            val libraryTrackKeys = mutableSetOf<String>()
            val libraryTitlesByArtist = mutableMapOf<String, MutableSet<String>>()
            val libraryArtists = mutableSetOf<String>()

            for (song in localSongs) {
                val normArtist = normalizeArtist(song.artistName)
                val normTitle = normalizeTitle(song.title)
                if (normArtist.isNotBlank() && normArtist != "unknown artist") {
                    libraryArtists.add(normArtist)
                    libraryTitlesByArtist.getOrPut(normArtist) { mutableSetOf() }.add(normTitle)
                }
                libraryTrackKeys.add(normalizeKey(normArtist, normTitle))
                extractVideoId(song.sourceUrl ?: song.fileUri)?.let { libraryVideoIds.add(it) }
            }

            for (online in onlineSongs) {
                val normArtist = normalizeArtist(online.artistName)
                val normTitle = normalizeTitle(online.title)
                if (normArtist.isNotBlank() && normArtist != "unknown artist") {
                    libraryArtists.add(normArtist)
                    libraryTitlesByArtist.getOrPut(normArtist) { mutableSetOf() }.add(normTitle)
                }
                libraryTrackKeys.add(normalizeKey(normArtist, normTitle))
                extractVideoId(online.sourceUrl)?.let { libraryVideoIds.add(it) }
            }

            // 3. Compute weighted affinity scores for all library songs to extract the best anchor seeds
            data class ScoredSeed(
                val title: String,
                val artist: String,
                val genre: String?,
                val sourceUrl: String?,
                val score: Double
            )

            val scoredSeeds = mutableListOf<ScoredSeed>()
            val now = System.currentTimeMillis()

            for (s in localSongs) {
                var score = 2.0
                if (s.isFavorite) score += 18.0
                score += (s.playCount.coerceAtMost(10) * 3.0)
                s.lastPlayedAt?.let { last ->
                    val diffDays = (now - last) / (1000L * 3600 * 24)
                    when {
                        diffDays <= 3 -> score += 14.0
                        diffDays <= 14 -> score += 8.0
                        diffDays <= 30 -> score += 3.0
                    }
                }
                scoredSeeds.add(ScoredSeed(s.title, s.artistName, s.genre, s.sourceUrl, score))
            }

            for (o in onlineSongs) {
                var score = 3.0
                if (o.isFavorite) score += 18.0
                o.lastPlayedAt?.let { last ->
                    val diffDays = (now - last) / (1000L * 3600 * 24)
                    when {
                        diffDays <= 3 -> score += 14.0
                        diffDays <= 14 -> score += 8.0
                        diffDays <= 30 -> score += 3.0
                    }
                }
                scoredSeeds.add(ScoredSeed(o.title, o.artistName, null, o.sourceUrl, score))
            }

            // Sort seeds by affinity score descending
            scoredSeeds.sortByDescending { it.score }
            val topSeeds = scoredSeeds.take(8)

            // Collect top artists and genres
            val topArtists = topSeeds.map { it.artist }.filter { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) }.distinct().take(5)
            val topGenres = topSeeds.mapNotNull { it.genre }.filter { it.isNotBlank() }.distinct().take(3)
            val recentSearches = searchHistoryRepo.getRecentQueries(limit = 4)

            // 4. Resolve anchor seed video IDs for deep YouTube Music Radio queries
            val anchorVideoIds = mutableListOf<String>()
            for (seed in topSeeds.take(5)) {
                val directId = extractVideoId(seed.sourceUrl.orEmpty())
                if (directId != null && directId.length == 11) {
                    anchorVideoIds.add(directId)
                } else {
                    // Resolve via cache or quick search for local songs
                    val cacheKey = "${seed.artist} - ${seed.title}".lowercase()
                    val cachedId = seedVideoCache[cacheKey]
                    if (cachedId != null) {
                        anchorVideoIds.add(cachedId)
                    } else {
                        val searchRes = runCatching {
                            extractor.search("${seed.artist} ${seed.title}", limit = 1)
                        }.getOrDefault(emptyList())
                        val resolved = searchRes.firstOrNull()?.id
                        if (resolved != null && resolved.length == 11) {
                            seedVideoCache[cacheKey] = resolved
                            anchorVideoIds.add(resolved)
                        }
                    }
                }
            }

            // 5. Query contextual radio recommendations and style-coherent tracks
            val candidateItems = mutableListOf<SearchResultItem>()
            val seenCandidateIds = mutableSetOf<String>()

            // Strategy A: Deep YouTube Music Radio for each anchor seed
            // This leverages YouTube Music's neural embeddings to find tracks of the exact same style, tempo, and genre
            for (seedVideoId in anchorVideoIds.distinct()) {
                val radioResults = runCatching {
                    extractor.getSongRadio(seedVideoId, limit = 25)
                }.getOrDefault(emptyList())

                for (item in radioResults) {
                    if (seenCandidateIds.add(item.id)) {
                        candidateItems.add(item)
                    }
                }
            }

            // Strategy B: Vibe-directed discovery & query supplementation
            val contextualQueries = mutableListOf<String>()
            when (vibe) {
                MyWaveVibe.MY_WAVE -> {
                    for (art in topArtists.take(2)) {
                        contextualQueries.add("$art radio")
                    }
                }
                MyWaveVibe.ENERGY -> {
                    for (art in topArtists.take(2)) {
                        contextualQueries.add("$art energetic mix")
                    }
                    if (topGenres.isNotEmpty()) {
                        contextualQueries.add("${topGenres.first()} workout energetic")
                    }
                }
                MyWaveVibe.CALM -> {
                    for (art in topArtists.take(2)) {
                        contextualQueries.add("$art acoustic chill")
                    }
                    if (topGenres.isNotEmpty()) {
                        contextualQueries.add("${topGenres.first()} chill ambient")
                    }
                }
                MyWaveVibe.DISCOVERY -> {
                    for (art in topArtists.take(2)) {
                        contextualQueries.add("$art similar artists")
                    }
                }
            }

            // If user library is very small or new, leverage recent searches
            if (candidateItems.size < 15 && recentSearches.isNotEmpty()) {
                for (query in recentSearches.take(2)) {
                    contextualQueries.add("$query ${vibe.promptSuffix}")
                }
            }

            // If still completely empty (brand new cold start user), use diverse melodic genres
            if (candidateItems.isEmpty() && contextualQueries.isEmpty()) {
                when (vibe) {
                    MyWaveVibe.MY_WAVE -> contextualQueries.addAll(listOf("инди поп хиты radio", "современный рок микс"))
                    MyWaveVibe.ENERGY -> contextualQueries.addAll(listOf("драйвовая музыка workout", "energetic electronic rock"))
                    MyWaveVibe.CALM -> contextualQueries.addAll(listOf("lo-fi ambient acoustic chill", "спокойная красивая музыка"))
                    MyWaveVibe.DISCOVERY -> contextualQueries.addAll(listOf("underrated indie alternative songs", "новые интересные артисты"))
                }
            }

            // Execute contextual queries
            for (query in contextualQueries.take(3)) {
                val searchResults = runCatching {
                    extractor.search(query, limit = 12)
                }.getOrDefault(emptyList())

                for (item in searchResults) {
                    if (seenCandidateIds.add(item.id)) {
                        candidateItems.add(item)
                    }
                }
            }

            // 6. Strict Exclusion and Coherence Filtering
            // Enforces:
            // a) Must NOT exist in the user's library (neither local nor online)
            // b) Must NOT be in the session blacklist (disliked / recently played)
            // c) In DISCOVERY mode, prioritize artists not already present in the library
            val curatedTracks = mutableListOf<SongEntity>()

            for (candidate in candidateItems) {
                val candId = candidate.id
                val candArtist = candidate.uploader
                val candTitle = candidate.title

                // Check 1: ID exclusion
                if (libraryVideoIds.contains(candId) || sessionExcludedIds.contains(candId)) {
                    continue
                }

                // Check 2: Session dislike exclusion
                val candKey = normalizeKey(candArtist, candTitle)
                if (sessionDislikedKeys.contains(candKey)) {
                    continue
                }

                // Check 3: Library title/artist duplicate check
                val normCandArtist = normalizeArtist(candArtist)
                val normCandTitle = normalizeTitle(candTitle)

                if (libraryTrackKeys.contains(normalizeKey(normCandArtist, normCandTitle))) {
                    continue
                }

                // Check 4: Check if any existing song from this artist has a matching title
                val existingTitlesForArtist = libraryTitlesByArtist[normCandArtist]
                if (existingTitlesForArtist != null && isDuplicateTitle(normCandTitle, existingTitlesForArtist)) {
                    continue
                }

                // Passed all exclusions -> Convert to SongEntity
                curatedTracks.add(
                    SongEntity(
                        songId = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, candidate.id).legacySongId(),
                        title = candidate.title,
                        artistName = candidate.uploader,
                        albumName = "Моя Волна",
                        durationMs = candidate.duration * 1000L,
                        artworkPath = candidate.thumbnail,
                        fileUri = candidate.url,
                        sourceUrl = candidate.url,
                        sourceType = "online"
                    )
                )
            }

            // 7. Post-Processing & Mode Optimization
            val finalTracks = when (vibe) {
                MyWaveVibe.DISCOVERY -> {
                    // In Discovery vibe, prioritize tracks from artists NOT already in user's library
                    val (newArtistTracks, existingArtistTracks) = curatedTracks.partition { track ->
                        !libraryArtists.contains(normalizeArtist(track.artistName))
                    }
                    (newArtistTracks.shuffled() + existingArtistTracks.shuffled()).take(40)
                }
                MyWaveVibe.ENERGY -> {
                    // Shuffle while preserving upbeat continuity
                    curatedTracks.shuffled().take(40)
                }
                MyWaveVibe.CALM -> {
                    // Calm stream
                    curatedTracks.shuffled().take(40)
                }
                MyWaveVibe.MY_WAVE -> {
                    // Balanced mix of style-aligned tracks
                    curatedTracks.shuffled().take(40)
                }
            }

            // 8. Human-Readable Context Description
            val description = if (topArtists.isNotEmpty()) {
                val artistSample = topArtists.take(2).joinToString(" и ")
                when (vibe) {
                    MyWaveVibe.MY_WAVE -> "В стиле $artistSample и похожих исполнителей"
                    MyWaveVibe.ENERGY -> "Бодрый поток по вашим вкусам ($artistSample)"
                    MyWaveVibe.CALM -> "Спокойный поток в стиле $artistSample"
                    MyWaveVibe.DISCOVERY -> "Свежие открытия по мотивам $artistSample"
                }
            } else if (recentSearches.isNotEmpty()) {
                val searchSample = recentSearches.first()
                "На основе ваших поисковых интересов: «$searchSample»"
            } else {
                when (vibe) {
                    MyWaveVibe.MY_WAVE -> "Бесконечный персональный поток музыки"
                    MyWaveVibe.ENERGY -> "Энергичный поток для активности и драйва"
                    MyWaveVibe.CALM -> "Уютный чилловый поток для отдыха"
                    MyWaveVibe.DISCOVERY -> "Свежие открытия и новые имена"
                }
            }

            _waveState.value = MyWaveState(
                vibe = vibe,
                description = description,
                tracks = finalTracks,
                isGenerating = false,
                error = null
            )
            finalTracks
        } catch (e: Exception) {
            e.printStackTrace()
            _waveState.value = _waveState.value.copy(
                isGenerating = false,
                error = "Не удалось обновить Мою Волну. Проверьте подключение к интернету."
            )
            emptyList()
        }
    }

    companion object {
        private val JUNK_TITLE_REGEX = Pattern.compile(
            """(?i)[\(\[\{].*?(?:official|audio|video|lyric|remaster|hd|4k|hq|clip|extended|live).*?[\)\]\}]"""
        )
        private val FEAT_REGEX = Pattern.compile(
            """(?i)\b(?:feat|ft|featuring|prod|with)\b.*"""
        )
        private val NON_WORD_REGEX = Pattern.compile("""[^\p{L}\p{N}\s]""")
        private val MULTI_SPACE_REGEX = Pattern.compile("""\s+""")

        fun normalizeTitle(title: String): String {
            var t = title.lowercase()
            t = JUNK_TITLE_REGEX.matcher(t).replaceAll("")
            t = FEAT_REGEX.matcher(t).replaceAll("")
            t = NON_WORD_REGEX.matcher(t).replaceAll("")
            t = MULTI_SPACE_REGEX.matcher(t).replaceAll(" ").trim()
            return t
        }

        fun normalizeArtist(artist: String): String {
            var a = artist.lowercase().trim()
            a = a.replace("- topic", "").replace("vevo", "")
            a = NON_WORD_REGEX.matcher(a).replaceAll("")
            a = MULTI_SPACE_REGEX.matcher(a).replaceAll(" ").trim()
            return a
        }

        fun normalizeKey(artist: String, title: String): String {
            return "${normalizeArtist(artist)}:::${normalizeTitle(title)}"
        }

        fun isDuplicateTitle(candTitle: String, existingTitles: Set<String>): Boolean {
            if (existingTitles.contains(candTitle)) return true
            if (candTitle.length >= 4) {
                for (existing in existingTitles) {
                    if (existing.length >= 4 && (candTitle.contains(existing) || existing.contains(candTitle))) {
                        return true
                    }
                }
            }
            return false
        }

        fun extractVideoId(urlOrId: String): String? {
            val clean = urlOrId.trim()
            if (clean.length == 11 && !clean.contains("/") && !clean.contains("?")) return clean
            val ytRegex = Regex("""(?:v=|youtu\.be/|embed/|shorts/|watch\?v=|\?v=)([a-zA-Z0-9_-]{11})""")
            val match = ytRegex.find(clean)
            return match?.groupValues?.get(1)
        }
    }
}
