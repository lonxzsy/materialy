package com.materialy.music.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: Flow<List<String>> = _recentQueries.asStateFlow()

    init {
        loadQueries()
    }

    private fun loadQueries() {
        val raw = prefs.getString(KEY_QUERIES, null) ?: return
        val list = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        _recentQueries.value = list
    }

    fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _recentQueries.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val trimmedList = current.take(MAX_QUERIES)
        _recentQueries.value = trimmedList
        persistQueries(trimmedList)
    }

    fun removeSearch(query: String) {
        val current = _recentQueries.value.toMutableList()
        current.remove(query)
        _recentQueries.value = current
        persistQueries(current)
    }

    fun clearAll() {
        _recentQueries.value = emptyList()
        prefs.edit().remove(KEY_QUERIES).apply()
    }

    fun getRecentQueries(limit: Int = 10): List<String> {
        return _recentQueries.value.take(limit)
    }

    private fun persistQueries(queries: List<String>) {
        val array = JSONArray()
        queries.forEach { array.put(it) }
        prefs.edit().putString(KEY_QUERIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_QUERIES = "recent_queries"
        private const val MAX_QUERIES = 25
    }
}
