package com.materialy.music.di

import android.content.Context
import androidx.room.Room
import com.materialy.music.data.db.AppDatabase
import com.materialy.music.data.db.Migrations
import com.materialy.music.data.download.DownloadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "materialy.db")
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides fun provideSongDao(db: AppDatabase) = db.songDao()
    @Provides fun providePlaylistDao(db: AppDatabase) = db.playlistDao()
    @Provides fun provideOnlineSongDao(db: AppDatabase) = db.onlineSongDao()
}
