package com.materialy.music.ui.screens.download

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.materialy.music.data.download.DownloadRepository
import com.materialy.music.data.download.FormatInfo
import com.materialy.music.data.download.InfoResponse
import com.materialy.music.data.download.SearchResultItem
import com.materialy.music.playback.PlayerManager
import com.materialy.music.ui.components.MiniPlayer
import com.materialy.music.ui.components.ToastManager
import com.materialy.music.ui.components.bounceClick
import com.materialy.music.ui.components.bouncy
import com.materialy.music.work.TrackDownload
import com.materialy.music.work.TrackDownloadManager
import com.materialy.music.work.TrackDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Loading(val message: String) : DownloadUiState
    data class SearchResults(val query: String, val results: List<SearchResultItem>) : DownloadUiState
    data class InfoReady(val info: InfoResponse, val selectedFormat: FormatInfo?) : DownloadUiState
    data class Downloading(
        val jobId: String,
        val stage: String,
        val progress: Float,
        val speed: String?,
        val eta: String?,
        val title: String,
        val artist: String,
        val thumbnail: String?
    ) : DownloadUiState
    data class Success(val title: String, val artist: String, val filename: String) : DownloadUiState
    data class Error(val message: String, val retryQuery: String? = null) : DownloadUiState
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repo: DownloadRepository,
    val player: PlayerManager
) : ViewModel() {
    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080")
    val serverUrl: StateFlow<String> = _serverUrl

    val isStandaloneMode = repo.isStandaloneModeFlow
    val customServerUrl = repo.customServerUrlFlow

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState

    private val _serverTestStatus = MutableStateFlow<String?>(null)
    val serverTestStatus: StateFlow<String?> = _serverTestStatus

    private val _isTestingServer = MutableStateFlow(false)
    val isTestingServer: StateFlow<Boolean> = _isTestingServer

    private var currentInfo: InfoResponse? = null
    private var selectedFmt: FormatInfo? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            repo.serverUrlFlow.collect { _serverUrl.value = it }
        }
    }

    fun setStandaloneMode(enabled: Boolean) = viewModelScope.launch {
        repo.setStandaloneMode(enabled)
        _serverTestStatus.value = null
        val msg = if (enabled) "Встроенный бэкенд на телефоне активен" else "Режим внешнего ПК-сервера"
        ToastManager.info(msg)
    }

    fun saveUrl(url: String) = viewModelScope.launch {
        val cleanUrl = repo.normalizeUrl(url)
        repo.saveServerUrl(cleanUrl)
        _serverUrl.value = cleanUrl
        _serverTestStatus.value = null
    }

    fun testServerConnection() = viewModelScope.launch {
        _isTestingServer.value = true
        _serverTestStatus.value = null
        try {
            val api = repo.createApi(_serverUrl.value)
            val res = api.health()
            val ytVer = res.yt_dlp ?: "ok"
            _serverTestStatus.value = "Онлайн (yt-dlp v$ytVer)"
            ToastManager.success("Сервер доступен и готов к работе!")
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Не удалось подключиться"
            _serverTestStatus.value = "Ошибка: $err"
            ToastManager.error("Сервер недоступен: $err")
        } finally {
            _isTestingServer.value = false
        }
    }

    fun searchOrFetch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return

        val isUrl = clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ||
                clean.contains("youtu.be", ignoreCase = true) ||
                clean.contains("youtube.com", ignoreCase = true) ||
                clean.contains("soundcloud.com", ignoreCase = true)

        if (isUrl) {
            fetchInfo(clean)
        } else {
            searchByKeyword(clean)
        }
    }

    private fun searchByKeyword(keyword: String) {
        _uiState.value = DownloadUiState.Loading("Ищем треки по запросу «$keyword»...")
        viewModelScope.launch {
            try {
                val api = repo.createApi(_serverUrl.value)
                val res = api.search(keyword, 12)
                if (res.results.isNotEmpty()) {
                    _uiState.value = DownloadUiState.SearchResults(keyword, res.results)
                } else {
                    _uiState.value = DownloadUiState.Error("Ничего не найдено по запросу «$keyword». Попробуйте другое название.", keyword)
                }
            } catch (e: Exception) {
                val rawMsg = e.localizedMessage ?: e.message ?: "Ошибка соединения с сервером"
                val friendlyMsg = when {
                    rawMsg.contains("404") -> "Сервер не нашел поисковый метод. Убедитесь, что сервер обновлен."
                    rawMsg.contains("500") -> "Ошибка сервера при поиске трека. Проверьте логи сервера."
                    else -> "Ошибка сети: $rawMsg"
                }
                _uiState.value = DownloadUiState.Error(friendlyMsg, keyword)
            }
        }
    }

    fun fetchInfo(url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        _uiState.value = DownloadUiState.Loading("Получаем данные о треке и доступных форматах...")
        viewModelScope.launch {
            try {
                val api = repo.createApi(_serverUrl.value)
                val res = api.getInfo(cleanUrl)
                currentInfo = res

                // Pick best audio format
                val bestAudio = res.formats.firstOrNull { it.isRecommended }
                    ?: res.formats.filter { (it.vcodec == "none" || it.vcodec == null) && it.acodec != null && it.acodec != "none" && it.ext != "mhtml" && !it.formatId.startsWith("sb") }
                        .maxByOrNull { it.abr ?: 0 }
                    ?: res.formats.firstOrNull { it.ext != "mhtml" && !it.formatId.startsWith("sb") }
                    ?: res.formats.firstOrNull()

                selectedFmt = bestAudio
                _uiState.value = DownloadUiState.InfoReady(res, bestAudio)
            } catch (e: Exception) {
                val rawMsg = e.localizedMessage ?: e.message ?: "Не удалось подключиться к серверу"
                val friendlyMsg = when {
                    rawMsg.contains("500") || rawMsg.contains("403") || rawMsg.contains("bot", ignoreCase = true) ->
                        "YouTube требует cookies.txt для доступа к этому видео.\nПоложите cookies.txt в папку сервера server/."
                    rawMsg.contains("404") -> "Сервер вернул 404. Проверьте правильность адреса сервера."
                    else -> "Ошибка получения данных: $rawMsg"
                }
                _uiState.value = DownloadUiState.Error(
                    message = friendlyMsg,
                    retryQuery = cleanUrl
                )
            }
        }
    }

    fun selectFormat(fmt: FormatInfo) {
        selectedFmt = fmt
        val info = currentInfo
        if (info != null) {
            _uiState.value = DownloadUiState.InfoReady(info, fmt)
        }
    }

    fun startDownload(context: Context, customFormat: FormatInfo? = null) {
        val info = currentInfo ?: return
        val fmt = customFormat ?: selectedFmt
        val url = info.id.let { if (it.length == 11) "https://www.youtube.com/watch?v=$it" else it }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            try {
                _uiState.value = DownloadUiState.Downloading(
                    jobId = "",
                    stage = "Инициализация загрузки на сервере...",
                    progress = 0.05f,
                    speed = null,
                    eta = null,
                    title = info.title,
                    artist = info.uploader,
                    thumbnail = info.thumbnail
                )

                val api = repo.createApi(_serverUrl.value)
                val targetExt = when (fmt?.ext?.lowercase()) {
                    "m4a", "aac", "mp4" -> "m4a"
                    "mp3" -> "mp3"
                    "opus", "webm", "ogg" -> "opus"
                    else -> "opus"
                }

                val req = com.materialy.music.data.download.DownloadRequest(
                    url = url,
                    formatId = fmt?.formatId,
                    audioFormat = targetExt,
                    audioQuality = "0"
                )

                ToastManager.info("Скачивание начато на сервере...")
                val startResp = api.startDownload(req)
                val jobId = startResp.jobId

                // Poll server status until completed
                var completedFilename: String? = null
                var pollCount = 0
                val maxPolls = 180

                while (pollCount < maxPolls) {
                    delay(1400)
                    pollCount++
                    try {
                        val status = api.getStatus(jobId)
                        if (status.status == "completed" && !status.filename.isNullOrBlank()) {
                            completedFilename = status.filename
                            break
                        } else if (status.status == "failed") {
                            throw Exception(status.error ?: "Ошибка загрузки на сервере")
                        } else {
                            val prog = (status.progress * 0.85f).coerceIn(0.05f, 0.85f)
                            val stageText = if (status.progress > 0.8f) "Конвертация и упаковка метаданных..." else "Скачивание на сервере..."
                            _uiState.value = DownloadUiState.Downloading(
                                jobId = jobId,
                                stage = stageText,
                                progress = prog,
                                speed = status.speed,
                                eta = status.eta,
                                title = info.title,
                                artist = info.uploader,
                                thumbnail = info.thumbnail
                            )
                        }
                    } catch (e: Exception) {
                        if (pollCount > 5 && e !is retrofit2.HttpException) {
                            // Temporary network blip
                        } else if (pollCount > 10) {
                            throw e
                        }
                    }
                }

                if (completedFilename == null) {
                    throw Exception("Превышено время ожидания скачивания на сервере.")
                }

                // Stage 2: Save to device via WorkManager
                _uiState.value = DownloadUiState.Downloading(
                    jobId = jobId,
                    stage = "Сохранение трека в память телефона...",
                    progress = 0.94f,
                    speed = null,
                    eta = null,
                    title = info.title,
                    artist = info.uploader,
                    thumbnail = info.thumbnail
                )

                val fileDownloadUrl = "${_serverUrl.value.trimEnd('/')}/file/$jobId"
                val workManager = WorkManager.getInstance(context)
                val workId = TrackDownloadManager.download(
                    context = context,
                    title = info.title,
                    artist = info.uploader,
                    sourceUrl = url,
                    fileUrl = fileDownloadUrl,
                    thumbnailUrl = info.thumbnail,
                    durationSec = info.duration,
                    ext = targetExt
                )

                // Track work completion
                var workFinished = false
                var saveChecks = 0
                while (!workFinished && saveChecks < 40) {
                    delay(400)
                    saveChecks++
                    val workInfo = workManager.getWorkInfoById(workId).get()
                    if (workInfo != null) {
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            workFinished = true
                        } else if (workInfo.state == WorkInfo.State.FAILED) {
                            val err = workInfo.outputData.getString("error") ?: "Ошибка записи файла"
                            throw Exception(err)
                        }
                    }
                }

                if (!workFinished) {
                    throw Exception("Сохранение трека не завершилось вовремя")
                }

                ToastManager.success("Трек «${info.title}» сохранён в медиатеку!")

                _uiState.value = DownloadUiState.Success(
                    title = info.title,
                    artist = info.uploader,
                    filename = completedFilename
                )
            } catch (e: Exception) {
                val err = e.localizedMessage ?: e.message ?: "Ошибка при скачивании"
                ToastManager.error("Ошибка скачивания: $err")
                _uiState.value = DownloadUiState.Error(
                    message = err,
                    retryQuery = url
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = if (currentInfo != null) {
            DownloadUiState.InfoReady(currentInfo!!, selectedFmt)
        } else {
            DownloadUiState.Idle
        }
    }
}

@Composable
private fun DownloadQueuePanel(
    downloads: List<TrackDownload>,
    onRetry: (TrackDownload) -> Unit,
    onCancel: (TrackDownload) -> Unit,
    onRemove: (TrackDownload) -> Unit
) {
    Column {
        Text(
            text = "Загрузки (${downloads.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            downloads.forEach { item ->
                val stateText = when (item.state) {
                    TrackDownloadState.QUEUED -> "В очереди"
                    TrackDownloadState.DOWNLOADING -> "Скачивание ${item.progress}%"
                    TrackDownloadState.FAILED -> "Ошибка"
                    TrackDownloadState.COMPLETED -> "Скачано"
                }
                OutlinedCard(
                    modifier = Modifier
                        .width(280.dp)
                        .semantics { stateDescription = stateText },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Filled.MusicNote, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.message?.takeIf { item.state == TrackDownloadState.FAILED } ?: stateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.state == TrackDownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.state == TrackDownloadState.DOWNLOADING || item.state == TrackDownloadState.QUEUED) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { item.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                when (item.state) {
                                    TrackDownloadState.FAILED -> onRetry(item)
                                    TrackDownloadState.QUEUED, TrackDownloadState.DOWNLOADING -> onCancel(item)
                                    TrackDownloadState.COMPLETED -> onRemove(item)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            val icon = when (item.state) {
                                TrackDownloadState.FAILED -> Icons.Filled.Refresh
                                TrackDownloadState.QUEUED, TrackDownloadState.DOWNLOADING -> Icons.Filled.Clear
                                TrackDownloadState.COMPLETED -> Icons.Filled.CheckCircle
                            }
                            val label = when (item.state) {
                                TrackDownloadState.FAILED -> "Повторить загрузку"
                                TrackDownloadState.QUEUED, TrackDownloadState.DOWNLOADING -> "Отменить загрузку"
                                TrackDownloadState.COMPLETED -> "Удалить скачанный трек"
                            }
                            Icon(icon, contentDescription = label)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onPlayer: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isStandaloneMode by viewModel.isStandaloneMode.collectAsState(initial = true)
    val customServerUrl by viewModel.customServerUrl.collectAsState(initial = "http://127.0.0.1:8080")
    val uiState by viewModel.uiState.collectAsState()
    val serverTestStatus by viewModel.serverTestStatus.collectAsState()
    val isTestingServer by viewModel.isTestingServer.collectAsState()
    val context = LocalContext.current
    val downloads by remember(context) { TrackDownloadManager.observeAll(context) }
        .collectAsState(initial = emptyList())

    var inputQuery by remember { mutableStateOf("") }
    val showSettings = false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Загрузка музыки",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Загрузки для офлайн-прослушивания",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isStandaloneMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isStandaloneMode) FontWeight.Medium else FontWeight.Normal
                )
            }

            if (false) IconButton(
                onClick = {},
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .bouncy()
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Настройки сервера",
                    tint = if (showSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Settings Dialog/Card with Test Button
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
                    // Standalone Mode Switch
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
                                "Поиск и скачивание прямо с телефона без ПК (порт 8080)",
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
                                    text = "Локальный сервер активен (127.0.0.1:8080)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { viewModel.saveUrl(it) },
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
                            onClick = { viewModel.testServerConnection() },
                            enabled = !isTestingServer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.bouncy()
                        ) {
                            if (isTestingServer) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверка...")
                            } else {
                                Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Тест сервера")
                            }
                        }

                        serverTestStatus?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (status.contains("Онлайн")) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (downloads.isNotEmpty()) {
            DownloadQueuePanel(
                downloads = downloads,
                onRetry = { TrackDownloadManager.retry(context, it) },
                onCancel = { TrackDownloadManager.cancel(context, it) },
                onRemove = { TrackDownloadManager.remove(context, it) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Search Input Card (Clean, modern Surface with harmonious padding)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = { Text("Название трека, артист или ссылка...") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (inputQuery.startsWith("http", ignoreCase = true)) Icons.Filled.Link else Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputQuery.isNotEmpty()) {
                                IconButton(onClick = { inputQuery = "" }, modifier = Modifier.bouncy()) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                                }
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = clipboard.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        inputQuery = text
                                        viewModel.searchOrFetch(text)
                                    }
                                }
                            }, modifier = Modifier.bouncy()) {
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
                    onClick = { viewModel.searchOrFetch(inputQuery) },
                    enabled = inputQuery.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .bouncy(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                ) {
                    val isLoading = uiState is DownloadUiState.Loading
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поиск...", fontWeight = FontWeight.Bold)
                    } else {
                        val isUrl = inputQuery.startsWith("http", ignoreCase = true)
                        Icon(if (isUrl) Icons.Filled.CloudDownload else Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isUrl) "Получить информацию" else "Найти музыку", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Animated State Transitions with stable contentKey to prevent flickering on progress updates
        AnimatedContent(
            targetState = uiState,
            contentKey = { state ->
                when (state) {
                    is DownloadUiState.Idle -> "idle"
                    is DownloadUiState.Loading -> "loading"
                    is DownloadUiState.SearchResults -> "search_results"
                    is DownloadUiState.InfoReady -> "info_ready"
                    is DownloadUiState.Downloading -> "downloading"
                    is DownloadUiState.Success -> "success"
                    is DownloadUiState.Error -> "error"
                }
            },
            transitionSpec = {
                (fadeIn(animationSpec = tween(320)) + scaleIn(initialScale = 0.97f, animationSpec = tween(320)))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "downloadStateTransition",
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) { targetState ->
            when (targetState) {
                is DownloadUiState.Idle -> {
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
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.size(76.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(38.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "Поиск и загрузка музыки",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Введите название песни или вставьте ссылку на YouTube/SoundCloud, выберите качество и трек сохранится в вашу медиатеку.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is DownloadUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
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

                is DownloadUiState.SearchResults -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Результаты поиска (${targetState.results.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(targetState.results, key = { it.id }) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .bounceClick(scaleDown = 0.96f) {
                                            viewModel.fetchInfo(item.url)
                                        },
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

                                        IconButton(
                                            onClick = { viewModel.fetchInfo(item.url) },
                                            modifier = Modifier.bouncy()
                                        ) {
                                            Icon(
                                                Icons.Filled.CloudDownload,
                                                contentDescription = "Скачать",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is DownloadUiState.InfoReady -> {
                    InfoReadyContent(
                        state = targetState,
                        viewModel = viewModel,
                        context = context
                    )
                }

                is DownloadUiState.Downloading -> {
                    DownloadingContent(state = targetState)
                }

                is DownloadUiState.Success -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Трек успешно скачан!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${targetState.artist} — ${targetState.title}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetState() },
                                        modifier = Modifier.weight(1f).bouncy(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Скачать ещё")
                                    }
                                    Button(
                                        onClick = onNavigateToLibrary,
                                        modifier = Modifier.weight(1f).bouncy(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("В Библиотеку")
                                    }
                                }
                            }
                        }
                    }
                }

                is DownloadUiState.Error -> {
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
                                        Text("Назад")
                                    }
                                    if (!targetState.retryQuery.isNullOrBlank()) {
                                        Button(
                                            onClick = { viewModel.searchOrFetch(targetState.retryQuery) },
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
            }
        }
    }
}

@Composable
private fun InfoReadyContent(
    state: DownloadUiState.InfoReady,
    viewModel: DownloadViewModel,
    context: Context
) {
    val info = state.info
    val selected = state.selectedFormat
    val audioFormats = remember(info.formats) {
        info.formats.filter {
            it.ext != "mhtml" && !it.formatId.startsWith("sb") && !it.note.contains("storyboard", ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    var isScrollingUp by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex != previousIndex || abs(currentOffset - previousOffset) > 15) {
                    val scrollingDown = when {
                        currentIndex > previousIndex -> true
                        currentIndex < previousIndex -> false
                        else -> currentOffset > previousOffset
                    }
                    isScrollingUp = !scrollingDown
                    previousIndex = currentIndex
                    previousOffset = currentOffset
                }
            }
    }

    val isCustomFormatSelected = selected != null && (selected.formatId != (audioFormats.firstOrNull { it.isRecommended }?.formatId ?: ""))
    val showFloatingButton = isCustomFormatSelected && isScrollingUp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // Track Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!info.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = info.thumbnail,
                                contentDescription = "Обложка",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.uploader,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val minutes = info.duration / 60
                                val seconds = info.duration % 60
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${minutes}:${String.format("%02d", seconds)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${info.extractor?.uppercase() ?: "WEB"} • ${audioFormats.size} вар.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Primary Action Button (Best Quality)
            item {
                val bestFormat = audioFormats.firstOrNull { it.isRecommended } ?: audioFormats.firstOrNull()
                Button(
                    onClick = { viewModel.startDownload(context, bestFormat) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(50.dp)
                        .bouncy(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Скачать в лучшем качестве (${bestFormat?.ext?.uppercase() ?: "OPUS"})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Format Selection Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.HighQuality,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Выбрать другое качество / формат",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(audioFormats) { fmt ->
                val isSelected = selected?.formatId == fmt.formatId
                val cleanLabel = fmt.qualityLabel
                    .replace(Regex("[🌟🎵⚡💾🎧]"), "")
                    .trim()
                    .ifBlank { "${fmt.ext.uppercase()} ${fmt.abr ?: ""}k" }

                val tierIcon = when {
                    fmt.isRecommended || fmt.qualityTier == "ultra" -> Icons.Filled.AutoAwesome
                    fmt.qualityTier == "high" -> Icons.Filled.HighQuality
                    else -> Icons.Filled.GraphicEq
                }

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .bounceClick(scaleDown = 0.96f) { viewModel.selectFormat(fmt) },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = tierIcon,
                                contentDescription = null,
                                tint = if (isSelected || fmt.isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = cleanLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (fmt.isRecommended) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ТОП",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (isSelected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Выбрано",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 26.dp)
                        ) {
                            val codec = fmt.acodec?.replace("mp4a.40.2", "AAC")?.replace("mp4a.40.5", "HE-AAC") ?: fmt.ext
                            Text(
                                text = "Кодек: $codec",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            fmt.filesizeFormatted?.let { size ->
                                Text(
                                    text = " • Размер: ~$size",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Action Button that appears when custom format is chosen
        AnimatedVisibility(
            visible = isCustomFormatSelected,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Выбранный формат",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selected?.ext?.uppercase() ?: "AUDIO"} • ${selected?.abr ?: ""}k",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = { viewModel.startDownload(context, selected) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.bouncy()
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скачать", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingContent(state: DownloadUiState.Downloading) {
    val progressAnimatable = remember { Animatable(state.progress) }

    LaunchedEffect(state.progress) {
        progressAnimatable.animateTo(
            targetValue = state.progress,
            animationSpec = tween(durationMillis = 1350, easing = LinearOutSlowInEasing)
        )
    }

    val currentProgress = progressAnimatable.value
    val percentDisplay = (currentProgress * 100).toInt().coerceIn(0, 100)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.stage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$percentDisplay%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (state.speed != null || state.eta != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.speed?.let {
                            Text(
                                text = "$it ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.eta?.let {
                            Text(
                                text = "• ETA: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
