package com.materialy.music.data.download

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.materialy.music.core.localbackend.server.LocalBackendService
import com.materialy.music.core.localbackend.server.LocalHttpServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ds by preferencesDataStore("settings")

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val localHttpServer: LocalHttpServer
) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val isStandaloneModeKey = booleanPreferencesKey("is_standalone_mode")

    val customServerUrlFlow: Flow<String> = context.ds.data.map { localHttpServer.baseUrl }
    val isStandaloneModeFlow: Flow<Boolean> = context.ds.data.map { true }

    // Active server URL (resolves to 127.0.0.1:8080 when standalone mode is on)
    val serverUrlFlow: Flow<String> = context.ds.data.map {
        ensureLocalServerStarted()
        localHttpServer.baseUrl
    }

    fun ensureLocalServerStarted() {
        if (!localHttpServer.isServerRunning()) {
            LocalBackendService.start(context)
        }
    }

    suspend fun setStandaloneMode(enabled: Boolean) {
        context.ds.edit { it[isStandaloneModeKey] = true }
        LocalBackendService.start(context)
    }

    fun normalizeUrl(input: String): String {
        var clean = input.trim()
        if (clean.isBlank()) return localHttpServer.baseUrl
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            clean = if (clean.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?.*"""))) {
                "http://$clean"
            } else {
                "https://$clean"
            }
        }
        return clean.trimEnd('/')
    }

    suspend fun saveServerUrl(url: String) {
        context.ds.edit { it[serverUrlKey] = localHttpServer.baseUrl }
    }

    fun createApi(baseUrl: String): DownloadApi {
        val normalized = normalizeUrl(baseUrl) + "/"
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val retro = Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retro.create(DownloadApi::class.java)
    }
}
