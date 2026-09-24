package com.materialy.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive Segmented Button Group.
 * Inspired by Google Material Components Android M3 Expressive ButtonGroup:
 * - Fluid sliding active pill with spring physics
 * - Tactile micro-scaling on segment clicks
 * - Cohesive pill container geometry with tonal elevations
 */
@Composable
fun <T> ExpressiveSegmentedButtonGroup(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    activeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconProvider: ((T) -> ImageVector?)? = null,
    labelProvider: (T) -> String
) {
    val density = LocalDensity.current
    val itemPositions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() } // index -> (xOffsetPx, widthPx)

    val currentTarget = itemPositions[selectedIndex] ?: Pair(0f, 0f)

    val animatedIndicatorX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = currentTarget.first,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "segmentIndicatorX"
    )

    val animatedIndicatorWidth by androidx.compose.animation.core.animateFloatAsState(
        targetValue = currentTarget.second,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "segmentIndicatorWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(22.dp))
            .background(containerColor)
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Sliding Active Indicator Pill
        if (animatedIndicatorWidth > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = animatedIndicatorX.toInt(), y = 0) }
                    .width(with(density) { animatedIndicatorWidth.toDp() })
                    .fillMaxHeight()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(18.dp), spotColor = activeColor)
                    .clip(RoundedCornerShape(18.dp))
                    .background(activeColor)
            )
        }

        // Segment Items Row
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val label = labelProvider(item)
                val icon = iconProvider?.invoke(item)

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) activeContentColor else inactiveContentColor,
                    animationSpec = tween(180),
                    label = "segmentContentColor"
                )

                val itemScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.03f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                    label = "segmentItemScale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coordinates ->
                            val pos = coordinates.positionInParent()
                            val width = coordinates.size.width.toFloat()
                            itemPositions[index] = Pair(pos.x, width)
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onItemSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                            }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
