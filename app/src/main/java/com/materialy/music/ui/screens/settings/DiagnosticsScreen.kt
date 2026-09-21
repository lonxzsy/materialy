package com.materialy.music.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialy.music.core.localbackend.BackendCoordinator
import com.materialy.music.core.localbackend.BackendState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val coordinator: BackendCoordinator) : ViewModel() {
    val state = coordinator.state
    fun retry() = coordinator.retry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Диагностика") }, navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Встроенные музыкальные функции", style = MaterialTheme.typography.titleLarge)
            Text(when (val value = state) {
                BackendState.Starting -> "Запуск…"
                BackendState.Ready -> "Готово"
                is BackendState.Degraded -> "Восстановление, попытка ${value.attempt}"
                BackendState.Unavailable -> "Недоступно"
            }, style = MaterialTheme.typography.bodyLarge)
            if (state == BackendState.Unavailable || state is BackendState.Degraded) {
                Button(onClick = viewModel::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Повторить") }
            }
            Text(
                "Обычная работа приложения не требует адреса сервера или выбора режима.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
