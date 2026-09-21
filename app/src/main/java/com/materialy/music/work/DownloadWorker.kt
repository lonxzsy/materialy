package com.materialy.music.work

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.materialy.music.core.localbackend.extractor.InnertubeExtractor
import com.materialy.music.core.util.MetadataExtractor
import com.materialy.music.data.db.AppDatabase
import com.materialy.music.data.db.Migrations
import com.materialy.music.data.db.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "materialy.db")
            .addMigrations(*Migrations.ALL)
            .build()
    }
    private val songDao by lazy { db.songDao() }
    private val extractor by lazy { InnertubeExtractor() }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rawFileUrl = inputData.getString("file_url") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: "Unknown Track"
        val artist = inputData.getString("artist") ?: "Unknown Artist"
        val requestedExt = inputData.getString("ext") ?: "m4a"
        val thumbnailUrl = inputData.getString("thumbnail_url")
        val expectedDuration = inputData.getLong("duration_sec", 0L) * 1000L
        val originalSourceUrl = inputData.getString("source_url") ?: rawFileUrl

        try {
            setProgress(workDataOf("progress" to 5, "status" to "Подготовка загрузки..."))

            // Target app-specific music directory (guaranteed read/write without any runtime permissions on all Android versions)
            val musicDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let {
                File(it, "Materialy").apply { mkdirs() }
            } ?: File(applicationContext.filesDir, "Materialy").apply { mkdirs() }

            val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val sanitizedArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

            // 1. Check if the file was already downloaded by LocalDownloadEngine (standalone server)
            var directFile: File? = null
            if (rawFileUrl.contains("/file/")) {
                val jobId = rawFileUrl.substringAfterLast("/file/").substringAfterLast("/")
                val cacheDir = File(applicationContext.cacheDir, "local_backend_downloads")
                val localFile = cacheDir.listFiles()?.firstOrNull { it.name.startsWith(jobId) && it.length() > 0 }
                if (localFile != null && localFile.exists()) {
                    directFile = localFile
                }
            }

            // 2. Resolve actual audio stream URL if not a direct local file or CDN URL
            val downloadUrl: String = if (directFile == null) {
                if (rawFileUrl.contains("googlevideo.com") || rawFileUrl.contains("sndcdn.com") || rawFileUrl.startsWith("http://127.0.0.1")) {
                    rawFileUrl
                } else {
                    setProgress(workDataOf("progress" to 10, "status" to "Получение прямой аудио-ссылки..."))
                    try {
                        extractor.resolveDirectStreamUrl(rawFileUrl)
                    } catch (e: Exception) {
                        return@withContext Result.failure(workDataOf("error" to "Не удалось извлечь аудио: ${e.message}"))
                    }
                }
            } else ""

            // Determine final extension
            var finalExt = when (requestedExt.lowercase().trimStart('.')) {
                "m4a", "aac", "mp4" -> "m4a"
                "mp3" -> "mp3"
                "webm", "opus", "ogg" -> "opus"
                else -> "m4a"
            }

            val targetAudioFile = File(musicDir, "$sanitizedArtist - $sanitizedTitle.$finalExt")
            if (targetAudioFile.exists()) {
                targetAudioFile.delete()
            }

            // 3. Download or copy file
            if (directFile != null && directFile.exists()) {
                setProgress(workDataOf("progress" to 50, "status" to "Сохранение трека..."))
                directFile.copyTo(targetAudioFile, overwrite = true)
            } else {
                setProgress(workDataOf("progress" to 15, "status" to "Скачивание аудио..."))
                val req = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(workDataOf("error" to "Ошибка сервера: HTTP ${resp.code}"))
                }
                val body = resp.body ?: return@withContext Result.failure(workDataOf("error" to "Пустой ответ от сервера"))
                val total = body.contentLength()

                // Check content-type to ensure matching extension
                val contentType = resp.header("Content-Type")?.lowercase() ?: ""
                if (contentType.contains("webm") || contentType.contains("opus")) {
                    finalExt = "opus"
                } else if (contentType.contains("mp4") || contentType.contains("m4a") || contentType.contains("aac")) {
                    finalExt = "m4a"
                } else if (contentType.contains("mpeg") || contentType.contains("mp3")) {
                    finalExt = "mp3"
                }

                FileOutputStream(targetAudioFile).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(32768)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val p = 15 + ((done * 75) / total).toInt().coerceIn(0, 75)
                                setProgress(workDataOf("progress" to p))
                            }
                        }
                    }
                }
            }

            if (!targetAudioFile.exists() || targetAudioFile.length() < 1024L) {
                return@withContext Result.failure(workDataOf("error" to "Загруженный аудиофайл пуст или поврежден"))
            }

            val mimeType = when (finalExt) {
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "opus" -> "audio/opus"
                else -> "audio/mp4"
            }

            // 4. Save artwork locally
            var localArtworkPath: String? = null
            if (!thumbnailUrl.isNullOrBlank()) {
                try {
                    val artDir = File(applicationContext.filesDir, "artwork").apply { mkdirs() }
                    val artFile = File(artDir, "${System.currentTimeMillis()}_thumb.jpg")
                    val thumbReq = Request.Builder().url(thumbnailUrl).build()
                    val thumbResp = client.newCall(thumbReq).execute()
                    if (thumbResp.isSuccessful && thumbResp.body != null) {
                        FileOutputStream(artFile).use { out ->
                            thumbResp.body!!.byteStream().copyTo(out)
                        }
                        localArtworkPath = artFile.absolutePath
                    }
                } catch (_: Exception) {}
            }

            // 5. Extract metadata from downloaded audio file
            val targetUri = Uri.fromFile(targetAudioFile)
            val extractedMeta = try {
                MetadataExtractor.extract(applicationContext, targetUri)
            } catch (_: Exception) { null }

            val finalDuration = if ((extractedMeta?.durationMs ?: 0L) > 0) {
                extractedMeta!!.durationMs!!
            } else if (expectedDuration > 0) {
                expectedDuration
            } else {
                0L
            }

            // 6. Save or update in Room Database
            val existingSong = songDao.getBySourceUrl(originalSourceUrl)
                ?: songDao.getBySourceUrl(rawFileUrl)

            val finalSong = if (existingSong != null) {
                existingSong.copy(
                    fileUri = targetUri.toString(),
                    relativePath = targetAudioFile.absolutePath,
                    sourceType = "download",
                    durationMs = if (finalDuration > 0) finalDuration else existingSong.durationMs,
                    artworkPath = localArtworkPath ?: existingSong.artworkPath,
                    mimeType = mimeType,
                    bitrate = extractedMeta?.bitrate ?: existingSong.bitrate
                )
            } else {
                SongEntity(
                    title = title,
                    artistName = artist,
                    albumName = extractedMeta?.album ?: "Загрузки Materialy",
                    fileUri = targetUri.toString(),
                    relativePath = targetAudioFile.absolutePath,
                    durationMs = finalDuration,
                    bitrate = extractedMeta?.bitrate,
                    mimeType = mimeType,
                    artworkPath = localArtworkPath,
                    sourceUrl = originalSourceUrl,
                    sourceType = "download"
                )
            }

            val savedSongId = songDao.insert(finalSong)

            // 7. Trigger Android MediaScanner so system and other apps index this file
            try {
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(targetAudioFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } catch (_: Exception) {}

            setProgress(workDataOf("progress" to 100, "status" to "Готово"))
            Result.success(
                workDataOf(
                    "file_uri" to targetUri.toString(),
                    "song_id" to savedSongId,
                    "title" to title
                )
            )
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.localizedMessage ?: e.message ?: "Ошибка сохранения трека")))
        }
    }
}
