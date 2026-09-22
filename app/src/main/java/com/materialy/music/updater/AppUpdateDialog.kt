package com.materialy.music.updater

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialy.music.ui.components.bouncy
import kotlinx.coroutines.launch

@Composable
fun AppUpdateDialog(
    updateState: UpdateState,
    updateManager: AppUpdateManager,
    onDismiss: () -> Unit
) {
    if (updateState is UpdateState.Idle) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        icon = {
            Surface(
                shape = CircleShape,
                color = when (updateState) {
                    is UpdateState.Available -> MaterialTheme.colorScheme.primaryContainer
                    is UpdateState.Downloading -> MaterialTheme.colorScheme.primaryContainer
                    is UpdateState.ReadyToInstall, UpdateState.UpToDate -> MaterialTheme.colorScheme.secondaryContainer
                    is UpdateState.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (updateState) {
                        is UpdateState.Available -> Icon(
                            Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        is UpdateState.Downloading -> Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        is UpdateState.ReadyToInstall, UpdateState.UpToDate -> Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        is UpdateState.Error -> Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        is UpdateState.Checking -> CircularProgressIndicator(
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        else -> {}
                    }
                }
            }
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val titleText = when (updateState) {
                    is UpdateState.Checking -> "Проверка обновлений"
                    is UpdateState.Available -> "Доступно обновление"
                    is UpdateState.Downloading -> "Загрузка обновления"
                    is UpdateState.ReadyToInstall -> "Обновление готово!"
                    is UpdateState.UpToDate -> "Обновлений не найдено"
                    is UpdateState.Error -> "Ошибка обновления"
                    UpdateState.Idle -> ""
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (updateState is UpdateState.Available) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = "Версия ${updateState.info.versionName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (updateState) {
                    is UpdateState.Checking -> {
                        Text(
                            text = "Связываемся с сервером и проверяем наличие свежей версии...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    is UpdateState.Available -> {
                        ReleaseNotesCard(releaseNotes = updateState.info.releaseNotes)
                    }
                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Загрузка установочного пакета",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${updateState.progress}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { updateState.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "Файл обновления загружен во внутреннее хранилище. Нажмите «Установить», чтобы запустить установку новой версии.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    is UpdateState.UpToDate -> {
                        Text(
                            text = "У вас установлена самая актуальная версия Materialy Music (${updateManager.getCurrentVersionName()}).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    is UpdateState.Error -> {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    UpdateState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.Available -> {
                    Button(
                        onClick = {
                            scope.launch {
                                updateManager.downloadAndPrepareApk(updateState.info)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.bouncy()
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Обновить", fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            updateManager.promptInstall(context, updateState.apkFile)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.bouncy()
                    ) {
                        Text("Установить", fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateState.Error -> {
                    Button(
                        onClick = {
                            scope.launch {
                                updateManager.checkForUpdates(silent = false)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.bouncy()
                    ) {
                        Text("Повторить")
                    }
                }
                is UpdateState.UpToDate -> {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.bouncy()
                    ) {
                        Text("Отлично")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (updateState !is UpdateState.Downloading && updateState !is UpdateState.UpToDate) {
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.bouncy()
                ) {
                    Text(if (updateState is UpdateState.ReadyToInstall) "Позже" else "Закрыть")
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

@Composable
private fun ReleaseNotesCard(
    releaseNotes: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Что нового в этой версии",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val allLines = releaseNotes
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val bulletItems = allLines.filterNot { line ->
                line.startsWith("Обновление Materialy", ignoreCase = true) ||
                line.startsWith("Что нового", ignoreCase = true)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bulletItems.isEmpty()) {
                    Text(
                        text = releaseNotes.ifBlank { "Улучшения производительности и стабильности." },
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    bulletItems.forEach { item ->
                        val cleanText = item
                            .removePrefix("•")
                            .removePrefix("-")
                            .removePrefix("*")
                            .trim()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = cleanText,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

