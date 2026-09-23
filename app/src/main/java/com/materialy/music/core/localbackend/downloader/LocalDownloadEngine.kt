package com.materialy.music.core.localbackend.downloader

import android.content.Context
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.core.util.FastTrackDownloader
import com.materialy.music.data.download.DownloadRequest
import com.materialy.music.data.download.JobStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
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

                val stream = extractor.resolveStream(req.url, req.formatId)
                jobState.progress = 0.15f

                // Clean filename
                val safeTitle = sanitizeFilename(info.title)
                val safeArtist = sanitizeFilename(info.uploader)
                val destinationFile = File(downloadsDir, "$jobId.$targetExt")
                if (destinationFile.exists()) destinationFile.delete()

                val result = FastTrackDownloader.download(
                    client = httpClient,
                    url = stream.url,
                    destinationFile = destinationFile,
                    knownTotalLength = stream.contentLength,
                    knownMimeType = stream.mimeType
                ) { progressInfo ->
                    jobState.speed = progressInfo.speedFormatted
                    jobState.eta = progressInfo.etaFormatted
                    val rawProg = 0.15f + (progressInfo.percent.toFloat() / 100f) * 0.80f
                    jobState.progress = rawProg.coerceIn(0.15f, 0.98f)
                    jobState.filesize = progressInfo.totalBytes
                }

                val finalExt = result.suggestedExt
                val finalFile = if (finalExt != targetExt) {
                    val renamed = File(downloadsDir, "$jobId.$finalExt")
                    if (renamed.exists()) renamed.delete()
                    if (destinationFile.renameTo(renamed)) renamed else destinationFile
                } else {
                    destinationFile
                }
                val outFilename = "$safeArtist - $safeTitle.$finalExt"

                jobState.progress = 1.0f
                jobState.status = "completed"
                jobState.filename = outFilename
                jobState.localFile = finalFile
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
