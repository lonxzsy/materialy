package com.materialy.music.ui.screens.online

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.materialy.music.data.db.entity.OnlineSongEntity
import com.materialy.music.data.download.SearchResultItem
import com.materialy.music.ui.components.MiniPlayer
import com.materialy.music.ui.components.ToastManager
import com.materialy.music.ui.components.bounceClick
import com.materialy.music.ui.components.bouncy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(
    onBack: () -> Unit,
    onPlayer: () -> Unit,
    viewModel: OnlineViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isStandaloneMode by viewModel.isStandaloneMode.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val onlineSongs by viewModel.onlineSongs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val currentPos by viewModel.currentPositionMs.collectAsState()
    val duration by viewModel.durationMs.collectAsState()
    val bassEnergy by viewModel.bassEnergy.collectAsState()
    val context = LocalContext.current

    var inputUrl by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<OnlineSongEntity?>(null) }
    var selectedFilter by remember { mutableStateOf("all") } // "all" or "fav"

    val isBackendOnline = backendStatus is BackendStatus.Connected

    val filteredList = remember(onlineSongs, selectedFilter) {
        when (selectedFilter) {
            "fav" -> onlineSongs.filter { it.isFavorite }
            else -> onlineSongs
        }
    }

    val totalDurationSeconds = remember(onlineSongs) {
        onlineSongs.sumOf { it.durationMs } / 1000
    }
    val totalHours = totalDurationSeconds / 3600
    val totalMins = (totalDurationSeconds % 3600) / 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top Header with Navigation & Server Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .bouncy()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Онлайн стриминг",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Text(
                            text = if (isStandaloneMode) "LOCAL" else "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = if (isStandaloneMode) "Встроенный бэкенд на телефоне (без ПК)" else "Слушайте треки и плейлисты без скачивания",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStandaloneMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Backend Status Indicator & Settings Button
            BackendStatusPill(
                status = backendStatus,
                onClick = { showSettings = !showSettings }
            )
        }

        // Server Settings Section (Animated)
        AnimatedVisibility(visible = showSettings) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Standalone Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Встроенный бэкенд на телефоне",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Поиск и онлайн-стриминг прямо с телефона без ПК (порт 8080)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isStandaloneMode,
                            onCheckedChange = { viewModel.setStandaloneMode(it) },
                            modifier = Modifier.bouncy()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isStandaloneMode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Локальный сервер активен на телефоне (127.0.0.1:8080)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { viewModel.saveServerUrl(it) },
                            placeholder = { Text("Диагностический адрес") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.checkBackendConnection() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.bouncy()
                        ) {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Проверить бэкенд")
                        }

                        when (val st = backendStatus) {
                            is BackendStatus.Checking -> {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                            is BackendStatus.Connected -> {
                                Text(
                                    text = "Онлайн (v${st.ytDlpVersion})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            is BackendStatus.Disconnected -> {
                                Text(
                                    text = "Недоступен",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // Backend Offline Warning Card (if disconnected)
        if (!isBackendOnline && backendStatus !is BackendStatus.Checking) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Требуется включенный бэкенд",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Онлайн-стриминг музыки работает через ваш сервер. Запустите ./start_server.sh на ПК или укажите адрес в настройках.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { viewModel.checkBackendConnection() },
                        modifier = Modifier.bouncy()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Повторить", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Input Card (Add link or playlist)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("Ссылка на трек / плейлист YouTube или SoundCloud...") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (inputUrl.startsWith("http", ignoreCase = true)) Icons.Filled.Link else Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputUrl.isNotEmpty()) {
                                IconButton(onClick = { inputUrl = "" }, modifier = Modifier.bouncy()) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                                }
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val text = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (text.isNotBlank()) {
                                            inputUrl = text
                                            viewModel.handleInput(text)
                                        }
                                    }
                                },
                                modifier = Modifier.bouncy()
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = "Вставить из буфера")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        viewModel.handleInput(inputUrl)
                        inputUrl = ""
                    },
                    enabled = inputUrl.isNotBlank() && isBackendOnline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .bouncy(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                ) {
                    val isLoading = uiState is OnlineUiState.Loading
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Обработка...", fontWeight = FontWeight.Bold)
                    } else {
                        val isPlaylist = inputUrl.contains("list=", ignoreCase = true) ||
                                inputUrl.contains("/playlist", ignoreCase = true) ||
                                inputUrl.contains("/sets/", ignoreCase = true)
                        val icon = if (isPlaylist) Icons.AutoMirrored.Filled.PlaylistAdd else Icons.Filled.Add
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlaylist) "Добавить весь плейлист" else "Добавить в Онлайн",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active State Transitions (Loading / Search Results / Error / History List)
        AnimatedContent(
            targetState = uiState,
            contentKey = { state ->
                when (state) {
                    is OnlineUiState.Idle -> "idle"
                    is OnlineUiState.Loading -> "loading"
                    is OnlineUiState.SearchResults -> "search_results"
                    is OnlineUiState.Error -> "error"
                }
            },
            transitionSpec = {
                (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.97f, animationSpec = tween(280)))
                    .togetherWith(fadeOut(animationSpec = tween(180)))
            },
            label = "onlineStateTransition",
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { targetState ->
            when (targetState) {
                is OnlineUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(28.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(46.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = targetState.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                is OnlineUiState.SearchResults -> {
                    SearchResultsView(
                        results = targetState.results,
                        onAdd = { item ->
                            viewModel.resolveAndAddUrl(item.url)
                            viewModel.resetState()
                        },
                        onBack = { viewModel.resetState() }
                    )
                }

                is OnlineUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Не удалось загрузить",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = targetState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetState() },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.bouncy()
                                    ) {
                                        Text("Назад к списку")
                                    }
                                    if (!targetState.retryUrl.isNullOrBlank()) {
                                        Button(
                                            onClick = { viewModel.handleInput(targetState.retryUrl) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.bouncy()
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Повторить")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is OnlineUiState.Idle -> {
                    if (onlineSongs.isEmpty()) {
                        EmptyOnlineState(onPaste = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    inputUrl = text
                                    viewModel.handleInput(text)
                                }
                            }
                        })
                    } else {
                        OnlineHistoryList(
                            songs = filteredList,
                            totalCount = onlineSongs.size,
                            totalHours = totalHours,
                            totalMins = totalMins,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            isAvailable = isBackendOnline,
                            selectedFilter = selectedFilter,
                            onFilterChange = { selectedFilter = it },
                            onPlayAll = {
                                viewModel.playAll(0)
                                onPlayer()
                            },
                            onShuffle = {
                                viewModel.shufflePlay()
                                onPlayer()
                            },
                            onClearAll = { showClearConfirm = true },
                            onTrackClick = { track ->
                                viewModel.playTrack(track)
                                onPlayer()
                            },
                            onTrackFavorite = { track -> viewModel.toggleFavorite(track) },
                            onTrackDelete = { track -> songToDelete = track }
                        )
                    }
                }
            }
        }
    }

    // Delete Single Track Dialog
    songToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Удалить из онлайн?", fontWeight = FontWeight.Bold) },
            text = { Text("Удалить трек «${track.title}» из сохраненной онлайн истории?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrack(track)
                        songToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }, modifier = Modifier.bouncy()) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Clear All Online History Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Очистить онлайн историю?", fontWeight = FontWeight.Bold) },
            text = { Text("Вы уверены, что хотите удалить все сохраненные онлайн треки и плейлисты из истории?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Очистить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }, modifier = Modifier.bouncy()) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun BackendStatusPill(
    status: BackendStatus,
    onClick: () -> Unit
) {
    val (bgColor, icon, label) = when (status) {
        is BackendStatus.Checking -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Filled.Refresh,
            "Проверка..."
        )
        is BackendStatus.Connected -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.15f),
            Icons.Filled.CloudDone,
            "Онлайн"
        )
        is BackendStatus.Disconnected -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            Icons.Filled.CloudOff,
            "Оффлайн"
        )
    }

    val tintColor = when (status) {
        is BackendStatus.Connected -> Color(0xFF4CAF50)
        is BackendStatus.Disconnected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.4f)),
        modifier = Modifier.bouncy(scaleDown = 0.92f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tintColor
            )
        }
    }
}

@Composable
private fun EmptyOnlineState(onPaste: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Podcasts,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Онлайн стриминг музыки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Вставьте ссылку на отдельный трек или целый плейлист (YouTube, SoundCloud). Музыка сразу добавится в список и сохранится в истории для быстрого стриминга без скачивания.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onPaste,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.bouncy()
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Вставить ссылку")
            }
        }
    }
}

@Composable
private fun OnlineHistoryList(
    songs: List<OnlineSongEntity>,
    totalCount: Int,
    totalHours: Long,
    totalMins: Long,
    currentSong: com.materialy.music.data.db.entity.SongEntity?,
    isPlaying: Boolean,
    isAvailable: Boolean = true,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onClearAll: () -> Unit,
    onTrackClick: (OnlineSongEntity) -> Unit,
    onTrackFavorite: (OnlineSongEntity) -> Unit,
    onTrackDelete: (OnlineSongEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Row & Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val timeStr = if (totalHours > 0) "${totalHours} ч ${totalMins} мин" else "${totalMins} мин"
                Text(
                    text = "История онлайн ($totalCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Общее время: $timeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onClearAll,
                    modifier = Modifier.bouncy()
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Очистить историю",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Buttons Row (Play All & Shuffle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .bouncy(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Слушать всё", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .bouncy(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Перемешать", fontWeight = FontWeight.SemiBold)
            }
        }

        // Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "all",
                onClick = { onFilterChange("all") },
                label = { Text("Все ($totalCount)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.bouncy()
            )
            FilterChip(
                selected = selectedFilter == "fav",
                onClick = { onFilterChange("fav") },
                leadingIcon = {
                    Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("Избранные") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.bouncy()
            )
        }

        // Online Track Items
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(top = 4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 150.dp)
        ) {
            items(songs, key = { it.onlineId }) { item ->
                val isCurrent = currentSong?.sourceUrl == item.sourceUrl
                Box(modifier = Modifier.animateItem()) {
                    OnlineSongItem(
                        item = item,
                        isSelected = isCurrent,
                        isPlaying = isPlaying && isCurrent,
                        isAvailable = isAvailable,
                        onClick = {
                            if (!isAvailable) {
                                ToastManager.error("Трек недоступен: сервер оффлайн")
                            } else {
                                onTrackClick(item)
                            }
                        },
                        onFavorite = {
                            if (isAvailable) {
                                onTrackFavorite(item)
                            }
                        },
                        onDelete = { onTrackDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineSongItem(
    item: OnlineSongEntity,
    isSelected: Boolean,
    isPlaying: Boolean,
    isAvailable: Boolean = true,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val heartScale = remember { androidx.compose.animation.core.Animatable(1f) }

    val favTint by animateColorAsState(
        targetValue = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        else if (item.isFavorite) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        label = "onlineFavTint"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (!isAvailable) Color.Transparent
        else if (isSelected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        label = "onlineBorderColor"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = if (!isAvailable) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
        else if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        label = "onlineContainerColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .bounceClick(scaleDown = if (isAvailable) 0.96f else 0.99f) { onClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, animatedBorderColor),
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork / Thumbnail
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = "Обложка трека",
                        contentScale = ContentScale.Crop,
                        colorFilter = if (!isAvailable) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
                        alpha = if (!isAvailable) 0.35f else 1f,
                        modifier = Modifier.size(52.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (!isAvailable) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            else if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (!isAvailable) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CloudOff,
                            contentDescription = "Недоступен (сервер оффлайн)",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isSelected && isPlaying && isAvailable) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected && isAvailable) FontWeight.Bold else FontWeight.Medium,
                    color = if (!isAvailable) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val hasDistinctPl = !item.playlistName.isNullOrBlank() &&
                        item.playlistName != "Онлайн Стриминг" &&
                        item.playlistName != "Materialy Online" &&
                        item.playlistName != item.artistName &&
                        item.playlistName != item.title

                val subtitle = if (hasDistinctPl) "${item.artistName} • ${item.playlistName}" else item.artistName
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAvailable) Icons.Filled.CloudQueue else Icons.Filled.CloudOff,
                        contentDescription = if (isAvailable) "Онлайн" else "Оффлайн",
                        tint = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    if (item.durationMs > 0) {
                        val mins = (item.durationMs / 1000) / 60
                        val secs = (item.durationMs / 1000) % 60
                        Text(
                            text = "${mins}:${String.format("%02d", secs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isAvailable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    if (!isAvailable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• Недоступен",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }

            // Favorite Button (disabled when offline)
            IconButton(
                onClick = {
                    if (isAvailable) {
                        scope.launch {
                            heartScale.animateTo(1.35f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh))
                            heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                        onFavorite()
                    }
                },
                enabled = isAvailable,
                modifier = Modifier
                    .size(38.dp)
                    .scale(if (isAvailable) heartScale.value else 1f)
            ) {
                Icon(
                    imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Избранное",
                    tint = favTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete Button (ALWAYS ENABLED)
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(38.dp)
                    .bouncy()
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultsView(
    results: List<SearchResultItem>,
    onAdd: (SearchResultItem) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.bouncy()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к онлайн")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Результаты поиска (${results.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .bounceClick(scaleDown = 0.96f) { onAdd(item) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!item.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = item.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${item.uploader} • ${item.durationFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { onAdd(item) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.bouncy()
                        ) {
                            Text("Добавить")
                        }
                    }
                }
            }
        }
    }
}
