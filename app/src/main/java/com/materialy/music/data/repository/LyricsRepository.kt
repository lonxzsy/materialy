package com.materialy.music.data.repository

import com.materialy.music.data.lyrics.LrcLine
import com.materialy.music.data.lyrics.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // In-memory cache for loaded lyrics
    private val memoryCache = ConcurrentHashMap<String, List<LrcLine>>()

    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int = 0
    ): List<LrcLine> = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSongTitle(title)
        val cleanArtist = cleanArtistName(artist)
        val cacheKey = "${cleanTitle.lowercase()}_${cleanArtist.lowercase()}"

        memoryCache[cacheKey]?.let { return@withContext it }

        // 1. Try exact match from LRCLIB
        val exactLines = fetchExact(cleanTitle, cleanArtist, durationSeconds)
        if (exactLines.isNotEmpty()) {
            memoryCache[cacheKey] = exactLines
            return@withContext exactLines
        }

        // 2. Try search match
        val searchLines = searchLyrics(cleanTitle, cleanArtist)
        if (searchLines.isNotEmpty()) {
            memoryCache[cacheKey] = searchLines
            return@withContext searchLines
        }

        emptyList()
    }

    private fun fetchExact(title: String, artist: String, durationSeconds: Int): List<LrcLine> {
        try {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder() ?: return emptyList()
            urlBuilder.addQueryParameter("track_name", title)
            urlBuilder.addQueryParameter("artist_name", artist)
            if (durationSeconds > 0) {
                urlBuilder.addQueryParameter("duration", durationSeconds.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("User-Agent", "MaterialyMusic/1.0 (https://github.com/materialy)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val parsedJson = json.parseToJsonElement(body).jsonObject
            val syncedLyrics = parsedJson["syncedLyrics"]?.jsonPrimitive?.contentOrNull

            if (!syncedLyrics.isNullOrBlank()) {
                return LrcParser.parse(syncedLyrics)
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun searchLyrics(title: String, artist: String): List<LrcLine> {
        try {
            val query = "$title $artist"
            val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(query, "UTF-8")}"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MaterialyMusic/1.0 (https://github.com/materialy)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val array = json.parseToJsonElement(body).jsonArray

            for (item in array) {
                val syncedLyrics = item.jsonObject["syncedLyrics"]?.jsonPrimitive?.contentOrNull
                if (!syncedLyrics.isNullOrBlank()) {
                    return LrcParser.parse(syncedLyrics)
                }
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun cleanSongTitle(raw: String): String {
        return raw.replace(Regex("""\(.*?\)|\\[.*?\\]"""), "")
            .replace(Regex("""(?i)(official\s*(music\s*)?video|audio|lyric\s*video|remastered|hd|4k)"""), "")
            .replace(Regex("""[-–—|/].*$"""), "")
            .trim()
    }

    private fun cleanArtistName(raw: String): String {
        return raw.replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""(?i)\s*vevo$"""), "")
            .trim()
    }
}
