package com.materialy.music.core.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

data class DownloadProgressInfo(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedFormatted: String,
    val etaFormatted: String
)

data class DownloadResult(
    val bytesWritten: Long,
    val mimeType: String,
    val suggestedExt: String
)

object FastTrackDownloader {
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val CHUNK_SIZE = 1024L * 1024L // 1 MB chunks for optimal burst throughput
    private const val MAX_CONCURRENT_CHUNKS = 4

    suspend fun download(
        client: OkHttpClient,
        url: String,
        destinationFile: File,
        knownTotalLength: Long = 0L,
        knownMimeType: String? = null,
        onProgress: (DownloadProgressInfo) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (destinationFile.exists()) destinationFile.delete()

        var totalLength = knownTotalLength
        var resolvedMime = knownMimeType ?: "audio/mp4"

        // 1. Probe total length and mimeType via Range probe if not already known
        if (totalLength <= 0L) {
            val probeInfo = probeLengthAndMime(client, url)
            if (probeInfo.first > 0L) {
                totalLength = probeInfo.first
            }
            if (!probeInfo.second.isNullOrBlank()) {
                resolvedMime = probeInfo.second!!
            }
        }

        val startTime = System.currentTimeMillis()
        var lastEmitTime = 0L

        fun emitProgress(downloaded: Long, total: Long) {
            val now = System.currentTimeMillis()
            if (now - lastEmitTime < 250L && downloaded < total) return
            lastEmitTime = now

            val elapsedSec = (now - startTime) / 1000.0
            val speedBps = if (elapsedSec > 0) (downloaded / elapsedSec).toLong() else 0L
            val speedStr = formatSpeed(speedBps)

            val remBytes = (total - downloaded).coerceAtLeast(0L)
            val etaSec = if (speedBps > 0) remBytes / speedBps else 0L
            val etaStr = String.format("%02d:%02d", etaSec / 60, etaSec % 60)

            val pct = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99) else 50
            onProgress(DownloadProgressInfo(pct, downloaded, total, speedStr, etaStr))
        }

        // 2. High-speed multi-threaded Range chunk download if total length is known
        val bytesWritten = if (totalLength > 0L) {
            try {
                downloadParallelChunks(client, url, destinationFile, totalLength) { done, total ->
                    emitProgress(done, total)
                }
            } catch (_: Exception) {
                // If parallel chunking fails for any reason, smoothly fall back to sequential stream
                destinationFile.delete()
                downloadSequential(client, url, destinationFile) { done, total ->
                    emitProgress(done, total)
                }
            }
        } else {
            downloadSequential(client, url, destinationFile) { done, total ->
                emitProgress(done, total)
            }
        }

        val suggestedExt = when {
            resolvedMime.contains("opus") || resolvedMime.contains("webm") -> "opus"
            resolvedMime.contains("mp4") || resolvedMime.contains("m4a") || resolvedMime.contains("aac") -> "m4a"
            resolvedMime.contains("mpeg") || resolvedMime.contains("mp3") -> "mp3"
            else -> "m4a"
        }

        // Final 100% progress
        val totalDurationSec = (System.currentTimeMillis() - startTime) / 1000.0
        val finalSpeed = if (totalDurationSec > 0) (bytesWritten / totalDurationSec).toLong() else 0L
        onProgress(DownloadProgressInfo(100, bytesWritten, bytesWritten, formatSpeed(finalSpeed), "00:00"))

        DownloadResult(
            bytesWritten = bytesWritten,
            mimeType = resolvedMime,
            suggestedExt = suggestedExt
        )
    }

    private fun probeLengthAndMime(client: OkHttpClient, url: String): Pair<Long, String?> {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", DEFAULT_USER_AGENT)
                .addHeader("Range", "bytes=0-1")
                .build()
            val resp = client.newCall(req).execute()
            val mime = resp.header("Content-Type")
            val contentRange = resp.header("Content-Range")
            resp.close()

            if (contentRange != null && contentRange.contains("/")) {
                val total = contentRange.substringAfterLast("/").trim().toLongOrNull() ?: 0L
                if (total > 0L) return Pair(total, mime)
            }
        } catch (_: Exception) {}
        return Pair(0L, null)
    }

    private suspend fun downloadParallelChunks(
        client: OkHttpClient,
        url: String,
        destinationFile: File,
        totalLength: Long,
        onBytesUpdated: (downloaded: Long, total: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val raf = RandomAccessFile(destinationFile, "rw")
        raf.setLength(totalLength)
        val channel = raf.channel

        val chunks = mutableListOf<Pair<Long, Long>>()
        var current = 0L
        while (current < totalLength) {
            val end = minOf(current + CHUNK_SIZE - 1, totalLength - 1)
            chunks.add(current to end)
            current = end + 1
        }

        val downloadedBytes = AtomicLong(0L)
        val semaphore = Semaphore(MAX_CONCURRENT_CHUNKS)

        try {
            coroutineScope {
                val tasks = chunks.map { (startByte, endByte) ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            var attempts = 0
                            var success = false
                            var lastErr: Exception? = null

                            while (attempts < 3 && !success) {
                                attempts++
                                try {
                                    val req = Request.Builder()
                                        .url(url)
                                        .addHeader("User-Agent", DEFAULT_USER_AGENT)
                                        .addHeader("Range", "bytes=$startByte-$endByte")
                                        .addHeader("Accept", "*/*")
                                        .addHeader("Accept-Encoding", "identity")
                                        .addHeader("Connection", "keep-alive")
                                        .build()

                                    val resp = client.newCall(req).execute()
                                    if (resp.code != 206 && resp.code != 200) {
                                        resp.close()
                                        throw IOException("HTTP ${resp.code} on chunk $startByte-$endByte")
                                    }

                                    val body = resp.body ?: throw IOException("Empty body")
                                    val bytes = body.bytes()
                                    resp.close()

                                    // Concurrent thread-safe write into channel at distinct chunk offset
                                    val buffer = ByteBuffer.wrap(bytes)
                                    channel.write(buffer, startByte)

                                    val done = downloadedBytes.addAndGet(bytes.size.toLong())
                                    onBytesUpdated(done, totalLength)
                                    success = true
                                } catch (e: Exception) {
                                    lastErr = e
                                    delay(100L * attempts)
                                }
                            }

                            if (!success) {
                                throw lastErr ?: IOException("Failed to download chunk $startByte-$endByte after 3 attempts")
                            }
                        }
                    }
                }
                tasks.awaitAll()
            }
        } finally {
            try { channel.close() } catch (_: Exception) {}
            try { raf.close() } catch (_: Exception) {}
        }

        downloadedBytes.get()
    }

    private fun downloadSequential(
        client: OkHttpClient,
        url: String,
        destinationFile: File,
        onBytesUpdated: (downloaded: Long, total: Long) -> Unit
    ): Long {
        val req = Request.Builder()
            .url(url)
            .addHeader("User-Agent", DEFAULT_USER_AGENT)
            .addHeader("Accept", "*/*")
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            throw IOException("HTTP error ${resp.code}")
        }

        val body = resp.body ?: throw IOException("Empty response body")
        val total = body.contentLength().coerceAtLeast(0L)

        var totalRead = 0L
        FileOutputStream(destinationFile).use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(128 * 1024) // 128 KB buffer
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    totalRead += read
                    onBytesUpdated(totalRead, if (total > 0) total else totalRead)
                }
            }
        }
        return totalRead
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format("%d KB/s", bytesPerSec / 1024)
            else -> "$bytesPerSec B/s"
        }
    }
}
