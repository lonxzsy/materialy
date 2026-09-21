package com.materialy.music.core.localbackend.downloader

import android.content.Context
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.data.download.DownloadRequest
import com.materialy.music.data.download.JobStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extractor: InnertubeExtractor
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jobs = ConcurrentHashMap<String, LocalJobState>()

    data class LocalJobState(
        val jobId: String,
        var status: String = "queued", // queued, downloading, completed, failed
        var progress: Float = 0f,
        var speed: String? = null,
        var eta: String? = null,
        var filename: String? = null,
        var localFile: File? = null,
        var filesize: Long? = null,
        var error: String? = null,
        var job: Job? = null
    )

    private val downloadsDir: File by lazy {
        File(context.cacheDir, "local_backend_downloads").apply { mkdirs() }
    }

    /**
     * Start an on-device high-speed download job
     */
    fun startDownload(req: DownloadRequest): String {
        val jobId = UUID.randomUUID().toString()
        val jobState = LocalJobState(jobId = jobId, status = "queued", progress = 0.05f)
        jobs[jobId] = jobState

        val coroutineJob = engineScope.launch {
            try {
                jobState.status = "downloading"
                jobState.progress = 0.10f

                // 1. Resolve direct stream URL and track metadata
                val info = extractor.getInfo(req.url)
                val targetExt = when (req.audioFormat.lowercase()) {
                    "m4a", "aac", "mp4" -> "m4a"
                    "mp3" -> "mp3"
                    else -> "opus"
                }

                val streamUrl = extractor.resolveDirectStreamUrl(req.url, req.formatId)
                jobState.progress = 0.15f

                // Clean filename
                val safeTitle = sanitizeFilename(info.title)
                val safeArtist = sanitizeFilename(info.uploader)
                val outFilename = "$safeArtist - $safeTitle.$targetExt"
                val destinationFile = File(downloadsDir, "$jobId.$targetExt")
                if (destinationFile.exists()) destinationFile.delete()

                // 2. Determine file length from format info or HEAD request
                val selectedFmt = info.formats.firstOrNull { it.formatId == req.formatId } ?: info.formats.firstOrNull()
                var totalLength = selectedFmt?.filesize ?: 0L

                if (totalLength <= 0L) {
                    try {
                        val headReq = Request.Builder()
                            .url(streamUrl)
                            .head()
                            .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                            .build()
                        val headResp = httpClient.newCall(headReq).execute()
                        val cl = headResp.header("Content-Length")?.toLongOrNull() ?: 0L
                        if (cl > 0) totalLength = cl
                        headResp.close()
                    } catch (_: Exception) {}
                }

                jobState.filesize = if (totalLength > 0) totalLength else null

                val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

                // 3. Fast unthrottled chunked Range downloading (512 KB chunks)
                if (totalLength > 0) {
                    val raf = RandomAccessFile(destinationFile, "rw")
                    val chunkSize = 512 * 1024L // 512 KB chunks
                    var startByte = 0L
                    var downloadedBytes = 0L
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = 0L

                    while (startByte < totalLength) {
                        val endByte = minOf(startByte + chunkSize - 1, totalLength - 1)
                        val rangeHeader = "bytes=$startByte-$endByte"

                        val chunkReq = Request.Builder()
                            .url(streamUrl)
                            .addHeader("User-Agent", userAgent)
                            .addHeader("Range", rangeHeader)
                            .addHeader("Accept", "*/*")
                            .addHeader("Accept-Encoding", "identity")
                            .addHeader("Connection", "keep-alive")
                            .build()

                        val chunkResp = httpClient.newCall(chunkReq).execute()
                        if (!chunkResp.isSuccessful && chunkResp.code != 206) {
                            chunkResp.close()
                            throw IllegalStateException("Ошибка загрузки фрагмента: HTTP ${chunkResp.code}")
                        }

                        val chunkBody = chunkResp.body ?: throw IllegalStateException("Пустой ответ от сервера")
                        val bytes = chunkBody.bytes()
                        raf.seek(startByte)
                        raf.write(bytes)
                        chunkResp.close()

                        downloadedBytes += bytes.size
                        startByte = endByte + 1

                        val now = System.currentTimeMillis()
                        val durationSec = (now - lastTime) / 1000.0
                        if (durationSec >= 0.3 || startByte >= totalLength) {
                            val bytesInPeriod = downloadedBytes - lastBytes
                            val bytesPerSec = if (durationSec > 0) (bytesInPeriod / durationSec).toLong() else 0L
                            jobState.speed = formatSpeed(bytesPerSec)

                            val rawProg = 0.15f + (downloadedBytes.toFloat() / totalLength.toFloat()) * 0.80f
                            jobState.progress = rawProg.coerceIn(0.15f, 0.98f)

                            val remBytes = totalLength - downloadedBytes
                            if (bytesPerSec > 0) {
                                val etaSec = remBytes / bytesPerSec
                                jobState.eta = String.format("%02d:%02d", etaSec / 60, etaSec % 60)
                            }

                            lastTime = now
                            lastBytes = downloadedBytes
                        }
                    }

                    raf.close()
                } else {
                    // Sequential fallback with larger 128KB buffer
                    val downloadRequest = Request.Builder()
                        .url(streamUrl)
                        .addHeader("User-Agent", userAgent)
                        .addHeader("Accept", "*/*")
                        .build()

                    val response = httpClient.newCall(downloadRequest).execute()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Ошибка загрузки потока: HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IllegalStateException("Пустой поток данных")
                    val cl = body.contentLength()
                    if (cl > 0) totalLength = cl
                    jobState.filesize = totalLength

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(destinationFile)
                    val buffer = ByteArray(128 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 300) {
                            val durationSec = (now - lastTime) / 1000.0
                            val bytesInPeriod = totalBytesRead - lastBytes
                            val bytesPerSec = if (durationSec > 0) (bytesInPeriod / durationSec).toLong() else 0L
                            jobState.speed = formatSpeed(bytesPerSec)

                            if (totalLength > 0) {
                                val rawProgress = 0.15f + (totalBytesRead.toFloat() / totalLength.toFloat()) * 0.80f
                                jobState.progress = rawProgress.coerceIn(0.15f, 0.98f)

                                val remainingBytes = totalLength - totalBytesRead
                                if (bytesPerSec > 0) {
                                    val etaSeconds = remainingBytes / bytesPerSec
                                    jobState.eta = String.format("%02d:%02d", etaSeconds / 60, etaSeconds % 60)
                                }
                            } else {
                                jobState.progress = (jobState.progress + 0.05f).coerceAtMost(0.92f)
                            }

                            lastTime = now
                            lastBytes = totalBytesRead
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()
                }

                jobState.progress = 1.0f
                jobState.status = "completed"
                jobState.filename = outFilename
                jobState.localFile = destinationFile
                jobState.speed = null
                jobState.eta = null
            } catch (e: Exception) {
                jobState.status = "failed"
                jobState.error = e.localizedMessage ?: e.message ?: "Ошибка загрузки на устройстве"
                jobState.progress = 0f
            }
        }

        jobState.job = coroutineJob
        return jobId
    }

    /**
     * Get job status for API
     */
    fun getStatus(jobId: String): JobStatus {
        val state = jobs[jobId] ?: return JobStatus(jobId = jobId, status = "failed", error = "Задача не найдена")
        return JobStatus(
            jobId = state.jobId,
            status = state.status,
            progress = state.progress,
            speed = state.speed,
            eta = state.eta,
            filename = state.filename,
            filesize = state.filesize,
            error = state.error
        )
    }

    /**
     * Get the downloaded file for jobId
     */
    fun getDownloadedFile(jobId: String): File? {
        return jobs[jobId]?.localFile?.takeIf { it.exists() }
    }

    private fun sanitizeFilename(input: String): String {
        return input.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val mb = bytesPerSec / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format("%.1f MB/s", mb)
        } else {
            String.format("%d KB/s", bytesPerSec / 1024)
        }
    }
}
