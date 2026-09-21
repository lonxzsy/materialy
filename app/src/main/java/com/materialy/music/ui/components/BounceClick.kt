package com.materialy.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Modifier adding tactile, silky-smooth spring scale effect with tap and long-press support.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {}
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scope.launch {
                        scale.animateTo(
                            targetValue = scaleDown,
                            animationSpec = spring(
                                dampingRatio = 0.80f,
                                stiffness = Spring.StiffnessHigh
                            )
                        )
                    }
                    tryAwaitRelease()
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                },
                onLongPress = {
                    onLongClick?.invoke()
                },
                onTap = {
                    onClick()
                }
            )
        }
}

/**
 * Modifier for standard buttons/cards adding live responsive tactile scale on press.
 * Uses silky-smooth spring physics for both press-down and release.
 */
fun Modifier.bouncy(
    scaleDown: Float = 0.95f
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.pressed }) {
                        scope.launch {
                            scale.animateTo(
                                targetValue = scaleDown,
                                animationSpec = spring(
                                    dampingRatio = 0.80f,
                                    stiffness = Spring.StiffnessHigh
                                )
                            )
                        }
                    } else {
                        scope.launch {
                            scale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.75f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                }
            }
        }
}

/**
 * Tactile modifier for navigation and action buttons with ultra-smooth response.
 */
fun Modifier.morphClick(
    scaleDown: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scope.launch {
                        scale.animateTo(
                            targetValue = scaleDown,
                            animationSpec = spring(
                                dampingRatio = 0.80f,
                                stiffness = Spring.StiffnessHigh
                            )
                        )
                    }
                    tryAwaitRelease()
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.75f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                },
                onTap = {
                    onClick()
                }
            )
        }
}
