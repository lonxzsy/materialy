package com.materialy.music.core.localbackend

import android.content.Context
import com.materialy.music.core.localbackend.server.LocalBackendService
import com.materialy.music.core.localbackend.server.LocalHttpServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackendState {
    data object Starting : BackendState
    data object Ready : BackendState
    data class Degraded(val attempt: Int) : BackendState
    data object Unavailable : BackendState
}

@Singleton
class BackendCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val server: LocalHttpServer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<BackendState>(BackendState.Starting)
    val state: StateFlow<BackendState> = _state.asStateFlow()
    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var failures = 0
            while (true) {
                _state.value = if (failures == 0) BackendState.Starting else BackendState.Degraded(failures)
                runCatching { LocalBackendService.start(context) }
                var checks = 0
                while (!server.isServerRunning() && checks++ < 10) delay(100)
                if (server.isServerRunning()) {
                    failures = 0
                    _state.value = BackendState.Ready
                    delay(5_000)
                } else {
                    failures++
                    _state.value = if (failures >= 3) BackendState.Unavailable else BackendState.Degraded(failures)
                    delay(500L * (1L shl failures.coerceAtMost(4)))
                }
            }
        }
    }

    fun retry() {
        monitorJob?.cancel()
        monitorJob = null
        _state.value = BackendState.Starting
        start()
    }
}
