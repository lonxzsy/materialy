package com.materialy.music.ui.components

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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ToastType {
    INFO, SUCCESS, ERROR, PLAYBACK
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val type: ToastType = ToastType.INFO
)

object ToastManager {
    private val _currentToast = MutableStateFlow<ToastMessage?>(null)
    val currentToast: StateFlow<ToastMessage?> = _currentToast

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var dismissJob: Job? = null

    fun show(text: String, type: ToastType = ToastType.INFO) {
        if (text.isBlank()) return
        dismissJob?.cancel()
        _currentToast.value = ToastMessage(text = text, type = type)
        dismissJob = scope.launch {
            delay(2800)
            _currentToast.value = null
        }
    }

    fun success(text: String) = show(text, ToastType.SUCCESS)
    fun info(text: String) = show(text, ToastType.INFO)
    fun error(text: String) = show(text, ToastType.ERROR)
    fun playback(text: String) = show(text, ToastType.PLAYBACK)

    fun dismiss() {
        dismissJob?.cancel()
        _currentToast.value = null
    }
}

/**
 * Authentic Google Material 3 Expressive Toast Notification Pill.
 * Matches app dark/dynamic theme, features smooth spring physics with overshoot,
 * swipe-up to dismiss gesture, tonal badges, and reliable exit transitions.
 */
@Composable
fun CustomToastHost(modifier: Modifier = Modifier) {
    val toast by ToastManager.currentToast.collectAsState()
    val scope = rememberCoroutineScope()

    // Retain last non-null toast message so exit transition renders completely without disappearing early
    var lastToast by remember { mutableStateOf<ToastMessage?>(null) }
    if (toast != null) {
        lastToast = toast
    }

    // Drag offset for dynamic swipe-up to dismiss
    val dragOffsetY = remember { Animatable(0f) }

    LaunchedEffect(toast) {
        if (toast != null) {
            dragOffsetY.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 10.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 80 },
                animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow)
            ) + fadeIn(
                animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)
            ) + scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(dampingRatio = 0.66f, stiffness = Spring.StiffnessLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it - 80 },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            ) + scaleOut(
                targetScale = 0.90f,
                animationSpec = tween(durationMillis = 240)
            )
        ) {
            val current = lastToast ?: return@AnimatedVisibility

            val (icon: ImageVector, badgeBg: Color, badgeFg: Color) = when (current.type) {
                ToastType.SUCCESS -> Triple(
                    Icons.Filled.Check,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer
                )
                ToastType.ERROR -> Triple(
                    Icons.Filled.ErrorOutline,
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
                ToastType.PLAYBACK -> Triple(
                    Icons.Filled.GraphicEq,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary
                )
                ToastType.INFO -> Triple(
                    Icons.Filled.Info,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .widthIn(min = 140.dp, max = 420.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffsetY.value < -40f) {
                                    scope.launch {
                                        dragOffsetY.animateTo(-250f, tween(180))
                                        ToastManager.dismiss()
                                    }
                                } else {
                                    scope.launch {
                                        dragOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (dragOffsetY.value + dragAmount).coerceAtMost(20f)
                                scope.launch { dragOffsetY.snapTo(newOffset) }
                            }
                        )
                    }
                    .bounceClick(scaleDown = 0.96f) {
                        ToastManager.dismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Expressive Tonal Micro-Badge
                    Surface(
                        shape = CircleShape,
                        color = badgeBg,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
