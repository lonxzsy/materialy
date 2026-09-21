package com.materialy.music

import android.app.Application
import com.materialy.music.core.localbackend.BackendCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MusicApp : Application() {

    @Inject
    lateinit var backendCoordinator: BackendCoordinator

    override fun onCreate() {
        super.onCreate()
        backendCoordinator.start()
    }
}
