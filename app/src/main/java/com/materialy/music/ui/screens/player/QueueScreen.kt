package com.materialy.music.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.materialy.music.playback.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(val player: PlayerManager) : ViewModel() {
    val queue = player.queue
    fun move(from: Int, to: Int) = viewModelScope.launch { player.move(from, to) }
    fun remove(id: String) = viewModelScope.launch { player.remove(id) }
    fun clear() = viewModelScope.launch { player.clearManualQueue() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val snapshot by viewModel.queue.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Очередь") },
            navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            } },
            actions = { TextButton(onClick = viewModel::clear, enabled = snapshot.items.size > 1) { Text("Убрать остальные") } }
        )
    }) { padding ->
        if (snapshot.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Очередь пуста", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выберите трек на Главной, в Поиске или Медиатеке.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 150.dp)
        ) {
            itemsIndexed(snapshot.items, key = { _, item -> item.queueItemId }, contentType = { _, _ -> "queue-track" }) { index, item ->
                ListItem(
                    modifier = Modifier.semantics {
                        stateDescription = if (index == snapshot.currentIndex) "Сейчас играет" else "Номер ${index + 1} в очереди"
                    },
                    headlineContent = { Text(item.title, maxLines = 1) },
                    supportingContent = { Text(if (index == snapshot.currentIndex) "Сейчас играет • ${item.artist}" else item.artist, maxLines = 1) },
                    colors = ListItemDefaults.colors(
                        containerColor = if (index == snapshot.currentIndex) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Переместить выше")
                            }
                            IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = index < snapshot.items.lastIndex, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Переместить ниже")
                            }
                            IconButton(onClick = { viewModel.remove(item.queueItemId) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить из очереди")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
