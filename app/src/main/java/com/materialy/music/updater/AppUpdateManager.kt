package com.materialy.music.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    data class Downloading(val progress: Int, val info: AppUpdateInfo) : UpdateState
    data class ReadyToInstall(val apkFile: File, val info: AppUpdateInfo) : UpdateState
    data object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    // GitHub repository raw manifest URL
    private val manifestUrl = "https://raw.githubusercontent.com/lonxzsy/materialy/main/version.json"

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(packageInfo)
        } catch (_: Exception) {
            1L
        }
    }

    suspend fun checkForUpdates(silent: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        if (!silent) {
            _updateState.value = UpdateState.Checking
        }
        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (!silent) {
                        _updateState.value = UpdateState.Error("Сервер вернул ошибку: ${response.code}")
                    }
                    return@withContext null
                }

                val body = response.body?.string() ?: run {
                    if (!silent) _updateState.value = UpdateState.Error("Пустой ответ от сервера")
                    return@withContext null
                }

                val remoteUpdate = json.decodeFromString<AppUpdateInfo>(body)
                val currentVersionCode = getCurrentVersionCode()

                if (remoteUpdate.versionCode > currentVersionCode) {
                    _updateState.value = UpdateState.Available(remoteUpdate)
                    return@withContext remoteUpdate
                } else {
                    if (!silent) {
                        _updateState.value = UpdateState.UpToDate
                    }
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            if (!silent) {
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Не удалось проверить обновления")
            }
            null
        }
    }

    suspend fun downloadAndPrepareApk(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Downloading(progress = 0, info = info)
        try {
            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _updateState.value = UpdateState.Error("Ошибка скачивания: HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: run {
                    _updateState.value = UpdateState.Error("Пустое тело файла APK")
                    return@withContext null
                }

                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()

                val apkFile = File(updateDir, "update-${info.versionName}.apk")
                if (apkFile.exists()) apkFile.delete()

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var lastProgress = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    _updateState.value = UpdateState.Downloading(progress, info)
                                    onProgress(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                _updateState.value = UpdateState.ReadyToInstall(apkFile, info)
                return@withContext apkFile
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Ошибка скачивания обновления")
            null
        }
    }

    fun promptInstall(activityContext: Context, apkFile: File) {
        try {
            // Check unknown sources installation permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activityContext.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activityContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activityContext.startActivity(permissionIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activityContext,
                "${activityContext.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Не удалось запустить установщик: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }
}
