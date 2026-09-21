package com.materialy.music.updater

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)
