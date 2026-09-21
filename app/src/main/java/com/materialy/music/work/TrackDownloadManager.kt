package com.materialy.music.work

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.lifecycle.Observer
import com.materialy.music.data.db.AppDatabase
import com.materialy.music.data.db.Migrations
import com.materialy.music.ui.components.ToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class TrackDownloadState { QUEUED, DOWNLOADING, FAILED, COMPLETED }

data class TrackDownload(
    val workId: UUID,
    val title: String,
    val artist: String,
    val sourceUrl: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val extension: String,
    val state: TrackDownloadState,
    val progress: Int,
    val message: String?,
    val fileUri: String?,
    val songId: Long?
)

/** WorkManager-backed download registry that survives navigation and process death. */
object TrackDownloadManager {
    const val WORK_TAG = "materialy_track_download"
    private const val PREFS = "track_download_registry"
    private const val RECORD_PREFIX = "record_"
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registryRevision = MutableStateFlow(0L)

    fun download(
        context: Context,
        title: String,
        artist: String,
        sourceUrl: String,
        fileUrl: String = sourceUrl,
        thumbnailUrl: String? = null,
        durationSec: Long = 0L,
        ext: String = "m4a"
    ): UUID {
        val appContext = context.applicationContext
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(WORK_TAG)
            .setInputData(
                workDataOf(
                    "file_url" to fileUrl,
                    "source_url" to sourceUrl,
                    "title" to title,
                    "artist" to artist,
                    "thumbnail_url" to (thumbnailUrl ?: ""),
                    "ext" to ext,
                    "duration_sec" to durationSec
                )
            )
            .build()

        saveRecord(appContext, request.id, title, artist, sourceUrl, thumbnailUrl, durationSec, ext)
        WorkManager.getInstance(appContext).enqueue(request)
        ToastManager.info("Добавлено в загрузки: $title")
        return request.id
    }

    fun observeAll(context: Context): Flow<List<TrackDownload>> {
        val appContext = context.applicationContext
        return workInfosFlow(WorkManager.getInstance(appContext))
            .combine(registryRevision) { infos, _ ->
                infos.mapNotNull { info -> info.toTrackDownload(appContext) }
                    .sortedByDescending { it.workId.toString() }
            }
    }

    fun retry(context: Context, item: TrackDownload): UUID {
        removeRecord(context.applicationContext, item.workId)
        return download(
            context = context,
            title = item.title,
            artist = item.artist,
            sourceUrl = item.sourceUrl,
            thumbnailUrl = item.thumbnailUrl,
            durationSec = item.durationSec,
            ext = item.extension
        )
    }

    fun cancel(context: Context, item: TrackDownload) {
        WorkManager.getInstance(context.applicationContext).cancelWorkById(item.workId)
        removeRecord(context.applicationContext, item.workId)
        ToastManager.info("Загрузка отменена")
    }

    /** Removes the completed file and its library row; failed work is simply dismissed. */
    fun remove(context: Context, item: TrackDownload) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelWorkById(item.workId)
        removeRecord(appContext, item.workId)

        if (item.state != TrackDownloadState.COMPLETED) return
        maintenanceScope.launch {
            runCatching {
                item.fileUri?.let { uriString ->
                    val uri = Uri.parse(uriString)
                    when (uri.scheme) {
                        "file" -> uri.path?.let(::File)?.takeIf(File::exists)?.delete()
                        "content" -> appContext.contentResolver.delete(uri, null, null)
                    }
                }
                item.songId?.let { id ->
                    val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "materialy.db")
                        .addMigrations(*Migrations.ALL)
                        .build()
                    try {
                        database.songDao().deleteById(id)
                    } finally {
                        database.close()
                    }
                }
            }
        }
        ToastManager.info("Скачанный трек удалён")
    }

    private fun WorkInfo.toTrackDownload(context: Context): TrackDownload? {
        val record = readRecord(context, id) ?: return null
        val downloadState = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TrackDownloadState.QUEUED
            WorkInfo.State.RUNNING -> TrackDownloadState.DOWNLOADING
            WorkInfo.State.SUCCEEDED -> TrackDownloadState.COMPLETED
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> TrackDownloadState.FAILED
        }
        return TrackDownload(
            workId = id,
            title = record.getString("title"),
            artist = record.getString("artist"),
            sourceUrl = record.getString("sourceUrl"),
            thumbnailUrl = record.optString("thumbnailUrl").takeIf(String::isNotBlank),
            durationSec = record.optLong("durationSec"),
            extension = record.optString("extension", "m4a"),
            state = downloadState,
            progress = when (downloadState) {
                TrackDownloadState.QUEUED -> 0
                TrackDownloadState.DOWNLOADING -> progress.getInt("progress", 0).coerceIn(0, 99)
                TrackDownloadState.COMPLETED -> 100
                TrackDownloadState.FAILED -> progress.getInt("progress", 0).coerceIn(0, 99)
            },
            message = if (downloadState == TrackDownloadState.FAILED) {
                outputData.getString("error") ?: "Не удалось скачать трек"
            } else progress.getString("status"),
            fileUri = outputData.getString("file_uri"),
            songId = outputData.getLong("song_id", Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
        )
    }

    private fun saveRecord(context: Context, id: UUID, title: String, artist: String, sourceUrl: String, thumbnailUrl: String?, durationSec: Long, extension: String) {
        val json = JSONObject()
            .put("title", title).put("artist", artist).put("sourceUrl", sourceUrl)
            .put("thumbnailUrl", thumbnailUrl ?: "").put("durationSec", durationSec)
            .put("extension", extension)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(RECORD_PREFIX + id, json.toString()).apply()
        registryRevision.value += 1
    }

    private fun readRecord(context: Context, id: UUID): JSONObject? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(RECORD_PREFIX + id, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun removeRecord(context: Context, id: UUID) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(RECORD_PREFIX + id).apply()
        registryRevision.value += 1
    }

    private fun workInfosFlow(workManager: WorkManager): Flow<List<WorkInfo>> = callbackFlow {
        val liveData = workManager.getWorkInfosByTagLiveData(WORK_TAG)
        val observer = Observer<List<WorkInfo>> { trySend(it.orEmpty()) }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }
}
