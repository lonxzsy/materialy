package com.materialy.music.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import com.materialy.music.updater.AppUpdateDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.materialy.music.data.audio.EqualizerPreset
import com.materialy.music.ui.components.bouncy
import com.materialy.music.ui.screens.online.BackendStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isStandaloneMode by viewModel.isStandaloneMode.collectAsState()
    val customServerUrl by viewModel.customServerUrl.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val localSongs by viewModel.localSongs.collectAsState()
    val onlineSongs by viewModel.onlineSongs.collectAsState()
    val isClearingCache by viewModel.isClearingCache.collectAsState()

    // Equalizer State
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val selectedPresetName by viewModel.selectedPresetName.collectAsState()
    val bandLevels by viewModel.bandLevels.collectAsState()
    val bassBoost by viewModel.bassBoost.collectAsState()
    val virtualizer by viewModel.virtualizer.collectAsState()
    val customPresets by viewModel.customPresets.collectAsState()
    val builtInPresets = viewModel.builtInPresets

    // Smooth Audio State
    val smoothAudioEnabled by viewModel.smoothAudioEnabled.collectAsState()
    val smoothAudioDurationMs by viewModel.smoothAudioDurationMs.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var inputUrl by remember(customServerUrl) { mutableStateOf(customServerUrl) }
    var showClearOnlineConfirm by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var presetToDelete by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp)
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

            Column {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Звук, эквалайзер, сервер и медиатека",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // SECTION: Audio Enhancement (Equalizer & Presets)
            item {
                SettingsSectionCard(
                    title = "Улучшение звучания",
                    icon = Icons.Filled.Tune,
                    badge = if (equalizerEnabled) "ВКЛ" else "ВЫКЛ",
                    badgeColor = if (equalizerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    // Header switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Эквалайзер и эффекты",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "5-полосный аппаратный эквалайзер, усиление басов и 3D-звук",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = equalizerEnabled,
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                            modifier = Modifier.bouncy()
                        )
                    }

                    AnimatedVisibility(visible = equalizerEnabled) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            // Presets horizontal selector
                            Text(
                                text = "Пресеты эквалайзера",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val allPresets = builtInPresets + customPresets

                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(allPresets, key = { it.name }) { preset ->
                                    val isSelected = selectedPresetName.equals(preset.name, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setPreset(preset) },
                                        label = { Text(preset.name) },
                                        trailingIcon = if (!preset.isBuiltIn) {
                                            {
                                                IconButton(
                                                    onClick = { presetToDelete = preset.name },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Удалить", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        } else null,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.bouncy()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 5-Band Equalizer Sliders
                            val bandFrequencies = listOf("60 Hz\n(Бас)", "230 Hz\n(Низкие)", "910 Hz\n(Средние)", "3.6 kHz\n(Высокие)", "14 kHz\n(Верхние)")

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Полосы частот (-15 dB … +15 dB)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    bandLevels.forEachIndexed { index, level ->
                                        if (index < bandFrequencies.size) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = bandFrequencies[index],
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.width(62.dp),
                                                    lineHeight = 13.sp
                                                )

                                                Slider(
                                                    value = level.toFloat(),
                                                    onValueChange = { viewModel.setBandLevel(index, it.toInt()) },
                                                    valueRange = -15f..15f,
                                                    steps = 29,
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = MaterialTheme.colorScheme.primary,
                                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )

                                                Text(
                                                    text = if (level > 0) "+$level dB" else "$level dB",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (level != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.width(52.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Save custom preset button
                                    OutlinedButton(
                                        onClick = {
                                            newPresetName = ""
                                            showSavePresetDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().bouncy()
                                    ) {
                                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Сохранить текущие настройки как пресет")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Bass Boost & Virtualizer
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Bass Boost
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Усиление басов (Bass Boost)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("${(bassBoost / 10)}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Slider(
                                        value = bassBoost.toFloat(),
                                        onValueChange = { viewModel.setBassBoost(it.toInt()) },
                                        valueRange = 0f..1000f,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Virtualizer (3D Sound)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.SurroundSound, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Объёмный 3D-звук (Virtualizer)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("${(virtualizer / 10)}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Slider(
                                        value = virtualizer.toFloat(),
                                        onValueChange = { viewModel.setVirtualizer(it.toInt()) },
                                        valueRange = 0f..1000f,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: Smooth Audio (Fade & Seamless Transitions)
            item {
                SettingsSectionCard(
                    title = "Плавное звучание",
                    icon = Icons.Filled.AutoAwesome,
                    badge = if (smoothAudioEnabled) "ВКЛ" else "ВЫКЛ",
                    badgeColor = if (smoothAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Плавные переходы и Fade",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Мягкое нарастание при старте, плавное затухание при паузе и бесшовный переход между треками",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = smoothAudioEnabled,
                            onCheckedChange = { viewModel.setSmoothAudioEnabled(it) },
                            modifier = Modifier.bouncy()
                        )
                    }

                    AnimatedVisibility(visible = smoothAudioEnabled) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Длительность перехода (Fade):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${String.format("%.1f", smoothAudioDurationMs / 1000f)} сек",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = smoothAudioDurationMs.toFloat(),
                                onValueChange = { viewModel.setSmoothAudioDurationMs(it.toLong()) },
                                valueRange = 500f..5000f,
                                steps = 9,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Benefit bullet points
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "• Fade-in: громкость мягко нарастает при запуске",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Fade-out: плавное затихание при нажатии паузы",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "• Crossfade: плавное перетекание при переключении следующего/предыдущего трека",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Backend connection controls live on the nested diagnostics screen only.
            if (false) item {
                SettingsSectionCard(
                    title = "Режим работы и сервер",
                    icon = Icons.Filled.Dns,
                    badge = if (isStandaloneMode) "ВСТРОЕННЫЙ" else "ПК СЕРВЕР",
                    badgeColor = if (isStandaloneMode) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                ) {
                    // Standalone Mode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Встроенный бэкенд на телефоне",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Поиск, стриминг и скачивание прямо на телефоне без ПК (порт 8080)",
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

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isStandaloneMode) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Автономный режим активен",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "Локальный HTTP сервер запущен на 127.0.0.1:8080. Компьютер не требуется!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        // Remote PC Server Settings
                        Column {
                            Text(
                                text = "Адрес внешнего PC Backend (FastAPI + yt-dlp):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = inputUrl,
                                onValueChange = {
                                    inputUrl = it
                                    viewModel.saveServerUrl(it)
                                },
                                placeholder = { Text("Диагностический адрес") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = { viewModel.checkBackend(inputUrl) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.bouncy()
                                ) {
                                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Проверить связь")
                                }

                                when (val st = backendStatus) {
                                    is BackendStatus.Checking -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Проверка...", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    is BackendStatus.Connected -> {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.CloudDone, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Онлайн (v${st.ytDlpVersion})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    }
                                    is BackendStatus.Disconnected -> {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Оффлайн",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: Storage & Media Library
            item {
                SettingsSectionCard(title = "Хранилище и медиатека", icon = Icons.Filled.Storage) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Локальные треки:", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = "${localSongs.size} треков",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сохраненные онлайн-треки:", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = "${onlineSongs.size} треков",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearOnlineConfirm = true },
                            enabled = onlineSongs.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).bouncy()
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Очистить онлайн")
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearImageCache() },
                            enabled = !isClearingCache,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).bouncy()
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Очистить кэш")
                        }
                    }
                }
            }

            // SECTION: About App
            item {
                SettingsSectionCard(title = "О приложении", icon = Icons.Filled.Info) {
                    Text(
                        text = "Materialy Music",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Версия ${viewModel.updateManager.getCurrentVersionName()} (код ${viewModel.updateManager.getCurrentVersionCode()}) • Material 3 Expressive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Современный музыкальный плеер с интеллектуальным AutoMix, эквалайзером, плавным звучанием, загрузкой треков и мгновенным онлайн-стримингом.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.checkForUpdates() },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .bouncy()
                        ) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновления", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDiagnostics,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .bouncy()
                        ) {
                            Text("Диагностика")
                        }
                    }
                }
            }
        }
    }

    // Save Custom Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            icon = {
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = { Text("Сохранить пресет", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Введите название для вашего пресета эквалайзера:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("Например: Мой Драйв") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            viewModel.saveCustomPreset(newPresetName, bandLevels)
                            showSavePresetDialog = false
                        }
                    },
                    enabled = newPresetName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }, modifier = Modifier.bouncy()) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Delete Custom Preset Dialog
    presetToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            icon = {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Удалить пресет?", fontWeight = FontWeight.Bold) },
            text = { Text("Вы уверены, что хотите удалить пользовательский пресет «$name»?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomPreset(name)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }, modifier = Modifier.bouncy()) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Clear Online History Confirmation Dialog
    if (showClearOnlineConfirm) {
        AlertDialog(
            onDismissRequest = { showClearOnlineConfirm = false },
            icon = {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = { Text("Очистить историю онлайн-треков?", fontWeight = FontWeight.Bold) },
            text = { Text("Все сохранённые онлайн-треки и плейлисты будут удалены из медиатеки.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearOnlineHistory()
                        showClearOnlineConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOnlineConfirm = false }, modifier = Modifier.bouncy()) {
                    Text("Отмена")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Material 3 Expressive Online Update Dialog
    AppUpdateDialog(
        updateState = updateState,
        updateManager = viewModel.updateManager,
        onDismiss = { viewModel.dismissUpdate() }
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
