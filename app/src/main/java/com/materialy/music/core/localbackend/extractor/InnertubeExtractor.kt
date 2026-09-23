package com.materialy.music.core.localbackend.extractor

import com.materialy.music.data.download.FormatInfo
import com.materialy.music.data.download.InfoResponse
import com.materialy.music.data.download.OnlineTrackItem
import com.materialy.music.data.download.ResolvedOnlineResponse
import com.materialy.music.data.download.SearchResultItem
import com.materialy.music.domain.model.HomeFeedData
import com.materialy.music.domain.model.HomeShelf
import com.materialy.music.domain.model.ShelfItem
import com.materialy.music.domain.model.ShelfType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnertubeExtractor @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private var cachedVisitorData: String? = null
    private var lastVisitorFetchTime: Long = 0
    private val visitorMutex = Mutex()

    /**
     * Get or refresh YouTube visitorData token
     */
    private suspend fun getVisitorData(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedVisitorData != null && (now - lastVisitorFetchTime < 3600_000)) {
            return@withContext cachedVisitorData
        }

        visitorMutex.withLock {
            if (cachedVisitorData != null && (now - lastVisitorFetchTime < 3600_000)) {
                return@withLock cachedVisitorData
            }

            try {
                val req = Request.Builder()
                    .url("https://www.youtube.com")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()

                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: ""

                val matcher1 = Pattern.compile("\"VISITOR_DATA\":\"([^\"]+)\"").matcher(html)
                val matcher2 = Pattern.compile("\"visitorData\":\"([^\"]+)\"").matcher(html)

                val token = when {
                    matcher1.find() -> matcher1.group(1)
                    matcher2.find() -> matcher2.group(1)
                    else -> null
                }

                if (token != null) {
                    cachedVisitorData = token
                    lastVisitorFetchTime = System.currentTimeMillis()
                }
                token
            } catch (e: Exception) {
                e.printStackTrace()
                cachedVisitorData
            }
        }
    }

    /**
     * Search YouTube tracks by query using Web Innertube API
     */
    suspend fun search(query: String, limit: Int = 12): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        try {
            val visitor = getVisitorData()
            val bodyJson = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB",
                            "clientVersion": "2.20240101.00.00",
                            "hl": "ru",
                            "gl": "RU"${if (visitor != null) ",\n\"visitorData\": \"$visitor\"" else ""}
                        }
                    },
                    "query": "${escapeJson(cleanQuery)}"
                }
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://www.youtube.com")

            if (visitor != null) {
                reqBuilder.addHeader("X-Goog-Visitor-Id", visitor)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val rawBody = response.body?.string() ?: return@withContext emptyList()

            val root = json.parseToJsonElement(rawBody).jsonObject
            val results = mutableListOf<SearchResultItem>()

            findVideoRenderers(root, results, limit)
            results.distinctBy { it.id }.take(limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get real-time search suggestions with rapid auto-complete
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        try {
            val url = "https://suggestqueries-clients6.youtube.com/complete/search?client=firefox&ds=yt&q=${java.net.URLEncoder.encode(clean, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val rootArray = json.parseToJsonElement(body).jsonArray
                if (rootArray.size > 1) {
                    val suggestionsArray = rootArray[1].jsonArray
                    return@withContext suggestionsArray.mapNotNull { it.jsonPrimitive.contentOrNull }
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    /**
     * Get radio / related songs for a given videoId using YouTube Music's WEB_REMIX next endpoint.
     * This queries the recommendation graph for songs of the exact same style, mood, and genre.
     */
    suspend fun getSongRadio(videoId: String, limit: Int = 30): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val cleanId = extractVideoId(videoId) ?: videoId.trim()
        if (cleanId.length != 11) return@withContext emptyList()

        try {
            val visitor = getVisitorData()
            val bodyJson = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240101.00.00",
                            "hl": "ru",
                            "gl": "RU"${if (visitor != null) ",\n\"visitorData\": \"$visitor\"" else ""}
                        }
                    },
                    "videoId": "$cleanId",
                    "playlistId": "RDAMVM$cleanId",
                    "isAudioOnly": true
                }
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next?prettyPrint=false")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Content-Type", "application/json")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")

            if (visitor != null) {
                reqBuilder.addHeader("X-Goog-Visitor-Id", visitor)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val rawBody = response.body?.string() ?: return@withContext emptyList()

            val root = json.parseToJsonElement(rawBody).jsonObject
            val results = mutableListOf<SearchResultItem>()
            findPlaylistPanelVideoRenderers(root, results, limit)
            results.distinctBy { it.id }.take(limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun findPlaylistPanelVideoRenderers(element: JsonElement, results: MutableList<SearchResultItem>, limit: Int) {
        if (results.size >= limit) return

        when (element) {
            is JsonObject -> {
                if (element.containsKey("playlistPanelVideoRenderer")) {
                    val r = element["playlistPanelVideoRenderer"]?.jsonObject
                    if (r != null) {
                        val videoId = r["videoId"]?.jsonPrimitive?.contentOrNull
                        val title = extractText(r["title"])
                        var artist = extractText(r["shortBylineText"])
                        if (artist.isNullOrBlank()) {
                            val longByline = r["longBylineText"]?.jsonObject?.get("runs")?.jsonArray
                            val firstRun = longByline?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            if (!firstRun.isNullOrBlank()) {
                                artist = firstRun
                            }
                        }
                        if (artist.isNullOrBlank()) {
                            artist = "YouTube"
                        }
                        val lengthStr = extractText(r["lengthText"])
                        val duration = parseDurationString(lengthStr)
                        val thumbs = r["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                        val thumbnail = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                        if (videoId != null && !title.isNullOrBlank()) {
                            results.add(
                                SearchResultItem(
                                    id = videoId,
                                    title = title,
                                    uploader = artist,
                                    duration = duration,
                                    durationFormatted = lengthStr ?: "${duration / 60}:${String.format("%02d", duration % 60)}",
                                    thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    url = "https://www.youtube.com/watch?v=$videoId"
                                )
                            )
                        }
                    }
                } else {
                    for ((_, child) in element) {
                        findPlaylistPanelVideoRenderers(child, results, limit)
                        if (results.size >= limit) return
                    }
                }
            }
            is JsonArray -> {
                for (child in element) {
                    findPlaylistPanelVideoRenderers(child, results, limit)
                    if (results.size >= limit) return
                }
            }
            else -> {}
        }
    }

    /**
     * Get track info, available audio formats and stream URLs for video
     */
    suspend fun getInfo(urlOrId: String): InfoResponse = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId)
            ?: throw IllegalArgumentException("Не удалось определить ID видео: $urlOrId")

        // 1. Fetch player payload using VISIONOS client (verified unthrottled direct stream URLs)
        var playerData = fetchVisionOsPayload(videoId)

        if (playerData == null || !hasAdaptiveAudio(playerData)) {
            // Fallback 1: ANDROID_VR client
            playerData = fetchAndroidVrPayload(videoId)
        }

        var title = "YouTube Audio"
        var author = "YouTube"
        var duration = 0L
        var thumbnail: String? = null

        val videoDetails = playerData?.get("videoDetails")?.jsonObject
        if (videoDetails != null) {
            title = videoDetails["title"]?.jsonPrimitive?.contentOrNull ?: title
            author = videoDetails["author"]?.jsonPrimitive?.contentOrNull ?: author
            duration = videoDetails["lengthSeconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val thumbs = videoDetails["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
            thumbnail = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
        } else {
            // Fallback to oembed for guaranteed title and author
            val oembed = fetchOEmbed(videoId)
            if (oembed != null) {
                title = oembed.first
                author = oembed.second
            }
        }

        // Extract formats
        val formats = mutableListOf<FormatInfo>()
        val streamingData = playerData?.get("streamingData")?.jsonObject
        val adaptiveFormats = streamingData?.get("adaptiveFormats")?.jsonArray ?: JsonArray(emptyList())

        for (fmtElem in adaptiveFormats) {
            val fmtObj = fmtElem.jsonObject
            val mimeType = fmtObj["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
            if (!mimeType.startsWith("audio/")) continue

            val itag = fmtObj["itag"]?.jsonPrimitive?.contentOrNull ?: continue
            val bitrate = fmtObj["bitrate"]?.jsonPrimitive?.intOrNull?.let { it / 1000 } ?: fmtObj["averageBitrate"]?.jsonPrimitive?.intOrNull?.let { it / 1000 } ?: 128
            val contentLength = fmtObj["contentLength"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val url = fmtObj["url"]?.jsonPrimitive?.contentOrNull

            val ext = if (mimeType.contains("opus")) "opus" else if (mimeType.contains("mp4") || mimeType.contains("aac")) "m4a" else "webm"
            val codec = if (mimeType.contains("opus")) "Opus" else if (mimeType.contains("mp4a")) "AAC" else "Audio"

            val isRec = itag == "251" || (ext == "opus" && bitrate >= 130)
            val tier = when {
                bitrate >= 140 -> "ultra"
                bitrate >= 110 -> "high"
                else -> "standard"
            }

            val sizeStr = contentLength?.let { formatBytes(it) } ?: "~${(duration * bitrate * 125) / (1024 * 1024)} MB"
            val label = "$codec HQ ${bitrate}k (${ext.uppercase()})"

            formats.add(
                FormatInfo(
                    formatId = itag,
                    ext = ext,
                    acodec = codec,
                    vcodec = "none",
                    abr = bitrate,
                    vbr = null,
                    note = "$codec $bitrate kbps",
                    filesize = contentLength,
                    filesizeFormatted = sizeStr,
                    qualityLabel = label,
                    qualityTier = tier,
                    isRecommended = isRec
                )
            )
        }

        // If no formats were found in streamingData, construct default playable format options
        if (formats.isEmpty()) {
            formats.add(
                FormatInfo(
                    formatId = "251",
                    ext = "opus",
                    acodec = "Opus",
                    vcodec = "none",
                    abr = 160,
                    vbr = null,
                    note = "Opus 160 kbps",
                    filesize = null,
                    filesizeFormatted = "~${(duration * 160 * 125) / (1024 * 1024)} MB",
                    qualityLabel = "Opus HQ 160k (OPUS)",
                    qualityTier = "ultra",
                    isRecommended = true
                )
            )
            formats.add(
                FormatInfo(
                    formatId = "140",
                    ext = "m4a",
                    acodec = "AAC",
                    vcodec = "none",
                    abr = 128,
                    vbr = null,
                    note = "AAC 128 kbps",
                    filesize = null,
                    filesizeFormatted = "~${(duration * 128 * 125) / (1024 * 1024)} MB",
                    qualityLabel = "AAC HQ 128k (M4A)",
                    qualityTier = "high",
                    isRecommended = false
                )
            )
        }

        // Ensure at least one recommended format exists
        if (formats.none { it.isRecommended } && formats.isNotEmpty()) {
            val best = formats.maxByOrNull { it.abr ?: 0 } ?: formats.first()
            val idx = formats.indexOf(best)
            if (idx != -1) {
                formats[idx] = best.copy(isRecommended = true)
            }
        }

        // Sort: Recommended first, then highest bitrate
        formats.sortWith(compareByDescending<FormatInfo> { it.isRecommended }.thenByDescending { it.abr ?: 0 })

        InfoResponse(
            id = videoId,
            title = title,
            uploader = author,
            duration = duration,
            thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            extractor = "youtube:on_device",
            formats = formats,
            description = ""
        )
    }

    /**
     * Resolve direct stream audio URL for playing online or downloading
     */
    suspend fun resolveDirectStreamUrl(urlOrId: String, preferredFormat: String? = null): String = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId) ?: urlOrId

        // 1. Try VISIONOS
        val visionData = fetchVisionOsPayload(videoId)
        var streamUrl = extractBestStreamUrl(visionData, preferredFormat)

        // 2. Try ANDROID_VR
        if (streamUrl == null) {
            val vrData = fetchAndroidVrPayload(videoId)
            streamUrl = extractBestStreamUrl(vrData, preferredFormat)
        }

        streamUrl ?: throw IllegalStateException("Не удалось получить прямую аудио-ссылку для $videoId")
    }

    /**
     * Resolve online track or playlist metadata
     */
    suspend fun resolveOnline(url: String): ResolvedOnlineResponse = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
        if (videoId != null) {
            val info = getInfo(videoId)
            val streamUrl = try { resolveDirectStreamUrl(videoId) } catch (_: Exception) { null }
            val item = OnlineTrackItem(
                id = info.id,
                title = info.title,
                uploader = info.uploader,
                duration = info.duration,
                durationFormatted = "${info.duration / 60}:${String.format("%02d", info.duration % 60)}",
                thumbnail = info.thumbnail,
                url = "https://www.youtube.com/watch?v=${info.id}",
                streamUrl = streamUrl
            )
            return@withContext ResolvedOnlineResponse(
                isPlaylist = false,
                itemCount = 1,
                items = listOf(item)
            )
        }

        // Fallback: search query or keyword
        val results = search(url, 10)
        val items = results.map { res ->
            OnlineTrackItem(
                id = res.id,
                title = res.title,
                uploader = res.uploader,
                duration = res.duration,
                durationFormatted = res.durationFormatted,
                thumbnail = res.thumbnail,
                url = res.url,
                streamUrl = null
            )
        }
        ResolvedOnlineResponse(
            isPlaylist = false,
            itemCount = items.size,
            items = items
        )
    }

    private suspend fun fetchVisionOsPayload(videoId: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val visitor = getVisitorData()
            val bodyJson = """
                {
                    "context": {
                        "client": {
                            "clientName": "VISIONOS",
                            "clientVersion": "1.02",
                            "deviceMake": "Apple",
                            "deviceModel": "RealityDevice17,1",
                            "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                            "osName": "visionOS",
                            "osVersion": "26.5.23O471",
                            "hl": "ru",
                            "gl": "RU",
                            "timeZone": "UTC",
                            "utcOffsetMinutes": 0${if (visitor != null) ",\n\"visitorData\": \"$visitor\"" else ""}
                        }
                    },
                    "videoId": "$videoId",
                    "playbackContext": {
                        "contentPlaybackContext": {
                            "html5Preference": "HTML5_PREF_WANTS",
                            "signatureTimestamp": 20684
                        }
                    },
                    "contentCheckOk": true,
                    "racyCheckOk": true
                }
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Youtube-Client-Name", "101")
                .addHeader("X-Youtube-Client-Version", "1.02")
                .addHeader("Origin", "https://www.youtube.com")

            if (visitor != null) {
                reqBuilder.addHeader("X-Goog-Visitor-Id", visitor)
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val raw = response.body?.string() ?: return@withContext null
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchAndroidVrPayload(videoId: String): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val bodyJson = """
                {
                    "context": {
                        "client": {
                            "clientName": "ANDROID_VR",
                            "clientVersion": "1.60.19",
                            "hl": "ru",
                            "gl": "RU",
                            "deviceMake": "Oculus",
                            "deviceModel": "Quest 3",
                            "androidSdkVersion": 32
                        }
                    },
                    "videoId": "$videoId"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12; Quest 3) AppleWebKit/537.36")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val raw = response.body?.string() ?: return@withContext null
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchOEmbed(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
                .build()
            val resp = client.newCall(req).execute()
            val raw = resp.body?.string() ?: return@withContext null
            val obj = json.parseToJsonElement(raw).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "YouTube Audio"
            val author = obj["author_name"]?.jsonPrimitive?.contentOrNull ?: "YouTube"
            Pair(title, author)
        } catch (_: Exception) {
            null
        }
    }

    private fun hasAdaptiveAudio(playerData: JsonObject): Boolean {
        val adaptive = playerData["streamingData"]?.jsonObject?.get("adaptiveFormats")?.jsonArray ?: return false
        return adaptive.any {
            val mime = it.jsonObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: ""
            val hasUrl = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
            mime.startsWith("audio/") && hasUrl
        }
    }

    private fun extractBestStreamUrl(playerData: JsonObject?, preferredFormat: String?): String? {
        val streamingData = playerData?.get("streamingData")?.jsonObject ?: return null
        val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray ?: return null

        val audioFormats = adaptiveFormats.mapNotNull { it.jsonObject }
            .filter { (it["mimeType"]?.jsonPrimitive?.contentOrNull ?: "").startsWith("audio/") }
            .filter { it["url"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }

        if (audioFormats.isEmpty()) return null

        val matched = if (preferredFormat != null) {
            audioFormats.firstOrNull { it["itag"]?.jsonPrimitive?.contentOrNull == preferredFormat }
                ?: audioFormats.firstOrNull { (it["mimeType"]?.jsonPrimitive?.contentOrNull ?: "").contains(preferredFormat) }
        } else null

        val best = matched ?: audioFormats.firstOrNull { it["itag"]?.jsonPrimitive?.contentOrNull == "251" }
            ?: audioFormats.firstOrNull { it["itag"]?.jsonPrimitive?.contentOrNull == "140" }
            ?: audioFormats.maxByOrNull { it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0 }
            ?: audioFormats.firstOrNull()

        return best?.get("url")?.jsonPrimitive?.contentOrNull
    }

    private fun findVideoRenderers(element: JsonElement, results: MutableList<SearchResultItem>, limit: Int) {
        if (results.size >= limit) return

        when (element) {
            is JsonObject -> {
                if (element.containsKey("videoRenderer")) {
                    val vr = element["videoRenderer"]?.jsonObject
                    if (vr != null) {
                        val videoId = vr["videoId"]?.jsonPrimitive?.contentOrNull
                        val title = extractText(vr["title"])
                        val uploader = extractText(vr["ownerText"]) ?: extractText(vr["shortBylineText"]) ?: "YouTube"
                        val lengthStr = extractText(vr["lengthText"])
                        val duration = parseDurationString(lengthStr)
                        val thumbs = vr["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                        val thumbnail = thumbs?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                        if (videoId != null && title != null) {
                            results.add(
                                SearchResultItem(
                                    id = videoId,
                                    title = title,
                                    uploader = uploader,
                                    duration = duration,
                                    durationFormatted = lengthStr ?: "${duration / 60}:${String.format("%02d", duration % 60)}",
                                    thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    url = "https://www.youtube.com/watch?v=$videoId"
                                )
                            )
                        }
                    }
                } else {
                    for ((_, child) in element) {
                        findVideoRenderers(child, results, limit)
                        if (results.size >= limit) return
                    }
                }
            }
            is JsonArray -> {
                for (child in element) {
                    findVideoRenderers(child, results, limit)
                    if (results.size >= limit) return
                }
            }
            else -> {}
        }
    }

    private fun extractText(jsonElement: JsonElement?): String? {
        if (jsonElement == null) return null
        val obj = jsonElement.jsonObject
        obj["simpleText"]?.jsonPrimitive?.contentOrNull?.let { return it }
        val runs = obj["runs"]?.jsonArray
        if (runs != null && runs.isNotEmpty()) {
            return runs.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
        }
        return null
    }

    private fun parseDurationString(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        val parts = timeStr.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    companion object {
        fun extractVideoId(url: String): String? {
            val clean = url.trim()
            if (clean.length == 11 && !clean.contains("/") && !clean.contains("?")) return clean
            val ytRegex = Regex("""(?:v=|youtu\.be/|embed/|shorts/|watch\?v=|\?v=)([a-zA-Z0-9_-]{11})""")
            val match = ytRegex.find(clean)
            return match?.groupValues?.get(1)
        }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format("%.1f %s", value, units[digitGroups])
    }


    /**
     * Fetch home feed curated shelves
     */
    suspend fun fetchHomeFeed(): HomeFeedData = withContext(Dispatchers.IO) {
        val shelves = mutableListOf<HomeShelf>()
        try {
            // 1. Trending / Charts
            val trendingItems = search("Популярная музыка 2024 хиты", limit = 10).map {
                ShelfItem(
                    id = it.id,
                    title = it.title,
                    subtitle = it.uploader,
                    thumbnail = it.thumbnail,
                    type = ShelfItem.ItemType.TRACK,
                    directUrl = it.url
                )
            }
            if (trendingItems.isNotEmpty()) {
                shelves.add(
                    HomeShelf(
                        shelfId = "trending",
                        title = "Тренды и чарты",
                        subtitle = "Самые популярные треки прямо сейчас",
                        shelfType = ShelfType.CHARTS_ROW,
                        items = trendingItems
                    )
                )
            }

            // 2. New Releases
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val newItems = search("Новинки музыки $currentYear", limit = 10).map {
                ShelfItem(
                    id = it.id,
                    title = it.title,
                    subtitle = it.uploader,
                    thumbnail = it.thumbnail,
                    type = ShelfItem.ItemType.TRACK,
                    directUrl = it.url
                )
            }
            if (newItems.isNotEmpty()) {
                shelves.add(
                    HomeShelf(
                        shelfId = "new_releases",
                        title = "Новые релизы",
                        subtitle = "Свежие треки этой недели",
                        shelfType = ShelfType.HORIZONTAL_CAROUSEL,
                        items = newItems
                    )
                )
            }

            // 3. Relax / Chill / Lo-Fi
            val relaxItems = search("Relax Chill Lo-Fi Beats", limit = 10).map {
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
                        shelfId = "relax",
                        title = "Релакс и фокус",
                        subtitle = "Спокойная музыка для работы и отдыха",
                        shelfType = ShelfType.HORIZONTAL_CAROUSEL,
                        items = relaxItems
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        HomeFeedData(shelves = shelves)
    }
}
