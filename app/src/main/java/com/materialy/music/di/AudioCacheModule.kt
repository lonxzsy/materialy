package com.materialy.music.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.materialy.music.playback.DirectStreamResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AudioCacheModule {

    private const val MAX_AUDIO_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L // 1 GB LRU Cache

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): StandaloneDatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideMediaCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider
    ): SimpleCache {
        val cacheDir = File(context.cacheDir, "media3_audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_AUDIO_CACHE_SIZE_BYTES)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    fun provideCachedDataSourceFactory(
        @ApplicationContext context: Context,
        cache: SimpleCache,
        streamResolver: DirectStreamResolver
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)

        // Resolve direct stream URL if it's an online track
        val resolvingHttpFactory = ResolvingDataSource.Factory(httpFactory, streamResolver)

        // Cache remote HTTP/HTTPS streams in 1 GB LRU Cache
        val cachedHttpFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingHttpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // DefaultDataSource routes http/https to cachedHttpFactory,
        // while local file:// and content:// URIs are served directly via FileDataSource/ContentDataSource!
        return DefaultDataSource.Factory(context, cachedHttpFactory)
    }
}
