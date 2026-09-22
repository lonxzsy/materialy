package com.materialy.music.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DirectStreamResolver: Resolves streaming CDN URLs on-the-fly using InnertubeExtractor.
 * Eliminates the need for a local HTTP proxy server (LocalHttpServer) or PC server.
 */
@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DirectStreamResolver @Inject constructor(
    private val extractor: InnertubeExtractor
) : ResolvingDataSource.Resolver {

    private val resolvedUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()

    private data class CachedStreamUrl(
        val directUrl: String,
        val expiresAtMs: Long
    )

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val originalUri = dataSpec.uri.toString()

        // ResolvingDataSource sits in the HTTP branch of DefaultDataSource. It must never
        // reinterpret arbitrary HTTP sources (including the embedded backend) as YouTube.
        // Doing so made local backend streams and other valid URLs fail through Innertube.
        if (!SourceUriPolicy.requiresYouTubeResolution(originalUri)) {
            return dataSpec
        }

        val now = System.currentTimeMillis()
        val cached = resolvedUrlCache[originalUri]
        if (cached != null && cached.expiresAtMs > now) {
            return dataSpec.buildUpon()
                .setUri(Uri.parse(cached.directUrl))
                .build()
        }

        return try {
            val resolvedUrl = runBlocking {
                extractor.resolveDirectStreamUrl(originalUri)
            }
            if (resolvedUrl.isBlank()) throw IOException("YouTube returned an empty audio stream URL")
            // Cache resolved direct stream URL for 4 hours
            resolvedUrlCache[originalUri] = CachedStreamUrl(
                directUrl = resolvedUrl,
                expiresAtMs = now + 4 * 3600 * 1000L
            )
            dataSpec.buildUpon()
                .setUri(Uri.parse(resolvedUrl))
                .build()
        } catch (e: Exception) {
            // Returning the original watch page makes ExoPlayer attempt to decode HTML and
            // hides the real failure behind a misleading parser error.
            throw IOException("Unable to resolve the YouTube audio stream", e)
        }
    }

    suspend fun resolveStreamAsync(originalUri: String): String? {
        if (!SourceUriPolicy.requiresYouTubeResolution(originalUri)) return originalUri
        val now = System.currentTimeMillis()
        val cached = resolvedUrlCache[originalUri]
        if (cached != null && cached.expiresAtMs > now) {
            return cached.directUrl
        }
        return try {
            val resolved = extractor.resolveDirectStreamUrl(originalUri)
            if (resolved.isNotBlank()) {
                resolvedUrlCache[originalUri] = CachedStreamUrl(resolved, now + 4 * 3600 * 1000L)
                resolved
            } else null
        } catch (_: Exception) {
            null
        }
    }
}

internal object SourceUriPolicy {
    private val youtubeHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be"
    )

    fun requiresYouTubeResolution(raw: String): Boolean {
        if (raw.length == 11 && raw.none { it == '/' || it == '?' }) return true
        // java.net.URI keeps this classifier a platform-independent pure function, so the
        // exact playback routing can be covered by local unit tests (android.net.Uri is a
        // stub in host-side tests).
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host.endsWith(".googlevideo.com") || host.endsWith(".sndcdn.com")) return false
        return host in youtubeHosts || host.endsWith(".youtube.com")
    }

    fun isRemoteTrack(sourceType: String?, sourceUrl: String?): Boolean {
        if (sourceType.equals("download", ignoreCase = true) ||
            sourceType.equals("local", ignoreCase = true)
        ) return false
        if (sourceType.equals("online", ignoreCase = true) ||
            sourceType.equals("playlist", ignoreCase = true) ||
            sourceType.equals("youtube", ignoreCase = true)
        ) return true
        return sourceUrl?.let(::requiresYouTubeResolution) == true
    }

    fun selectInput(
        sourceType: String?,
        sourceUrl: String?,
        storedUri: String,
        resolvedLocalUri: String
    ): String? {
        val raw = if (isRemoteTrack(sourceType, sourceUrl)) {
            sourceUrl?.takeIf(String::isNotBlank) ?: storedUri
        } else {
            resolvedLocalUri
        }
        return raw.trim().takeIf(String::isNotBlank)
    }
}
