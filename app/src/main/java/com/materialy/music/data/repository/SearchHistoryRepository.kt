package com.materialy.music.data.repository

import android.content.Context
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SearchHistoryEntry(
    val query: String,
    val displayTitle: String = query
)

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: Lazy<InnertubeExtractor>
) {
    private val prefs = context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _recentEntries = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val recentEntries = _recentEntries.asStateFlow()

    val recentQueries: Flow<List<String>> = _recentEntries.map { list ->
        list.map { it.query }
    }

    init {
        loadQueries()
    }

    private fun loadQueries() {
        val raw = prefs.getString(KEY_QUERIES, null) ?: return
        val list = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item = array.opt(i)
                when (item) {
                    is JSONObject -> {
                        val q = item.optString("query")
                        val dt = item.optString("displayTitle", q)
                        if (q.isNotBlank()) {
                            val title = if (dt.isNotBlank()) dt else formatFallbackTitle(q)
                            SearchHistoryEntry(q, title)
                        } else null
                    }
                    is String -> {
                        if (item.isNotBlank()) {
                            val title = formatFallbackTitle(item)
                            SearchHistoryEntry(item, title)
                        } else null
                    }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())

        _recentEntries.value = list

        // Resolve any YouTube links that still show raw URL or fallback
        list.forEach { entry ->
            val videoId = InnertubeExtractor.extractVideoId(entry.query)
            if (videoId != null && (entry.displayTitle == entry.query || entry.displayTitle.startsWith("http") || entry.displayTitle.startsWith("YouTube трек"))) {
                resolveTitleAsync(entry.query, videoId)
            }
        }
    }

    private fun formatFallbackTitle(query: String): String {
        val videoId = InnertubeExtractor.extractVideoId(query)
        return if (videoId != null) "YouTube трек ($videoId)" else query
    }

    fun recordSearch(query: String, explicitTitle: String? = null) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val videoId = InnertubeExtractor.extractVideoId(trimmed)
        val initialDisplay = when {
            !explicitTitle.isNullOrBlank() -> explicitTitle
            videoId != null -> formatFallbackTitle(trimmed)
            else -> trimmed
        }

        val current = _recentEntries.value.toMutableList()
        current.removeAll { it.query.equals(trimmed, ignoreCase = true) }
        current.add(0, SearchHistoryEntry(query = trimmed, displayTitle = initialDisplay))

        val trimmedList = current.take(MAX_QUERIES)
        _recentEntries.value = trimmedList
        persistQueries(trimmedList)

        if (videoId != null && explicitTitle.isNullOrBlank()) {
            resolveTitleAsync(trimmed, videoId)
        }
    }

    fun updateDisplayTitle(query: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return

        val current = _recentEntries.value.toMutableList()
        val index = current.indexOfFirst { it.query.equals(query.trim(), ignoreCase = true) }
        if (index >= 0) {
            val old = current[index]
            if (old.displayTitle != cleanTitle) {
                current[index] = old.copy(displayTitle = cleanTitle)
                _recentEntries.value = current
                persistQueries(current)
            }
        }
    }

    private fun resolveTitleAsync(query: String, videoId: String) {
        scope.launch {
            try {
                val oembed = extractor.get().fetchOEmbed(videoId)
                if (oembed != null && oembed.first.isNotBlank() && oembed.first != "YouTube Audio") {
                    updateDisplayTitle(query, oembed.first)
                }
            } catch (_: Exception) {}
        }
    }

    fun removeSearch(query: String) {
        val current = _recentEntries.value.toMutableList()
        current.removeAll { it.query.equals(query, ignoreCase = true) }
        _recentEntries.value = current
        persistQueries(current)
    }

    fun clearAll() {
        _recentEntries.value = emptyList()
        prefs.edit().remove(KEY_QUERIES).apply()
    }

    fun getRecentQueries(limit: Int = 10): List<String> {
        return _recentEntries.value.take(limit).map { it.query }
    }

    fun getRecentEntries(limit: Int = 10): List<SearchHistoryEntry> {
        return _recentEntries.value.take(limit)
    }

    private fun persistQueries(entries: List<SearchHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("query", entry.query)
            obj.put("displayTitle", entry.displayTitle)
            array.put(obj)
        }
        prefs.edit().putString(KEY_QUERIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_QUERIES = "recent_queries"
        private const val MAX_QUERIES = 25
    }
}
