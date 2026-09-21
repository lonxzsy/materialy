package com.materialy.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BouncyHeartButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    val tintColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        label = "heartColor"
    )

    IconButton(
        onClick = {
            scope.launch {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    delay(60)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {}

                scale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh))
                scale.animateTo(0.88f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
            }
            onToggle()
        },
        modifier = modifier
            .size(44.dp)
            .scale(scale.value)
            .bouncy(scaleDown = 0.92f)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isFavorite) "Удалить из избранного" else "В избранное",
            tint = tintColor,
            modifier = Modifier.size(26.dp)
        )
    }
}
