package com.materialy.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

data class NavigationTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Material 3 Expressive Navigation Bar.
 * Inspired by Google Material Components Android M3 Expressive BottomNavigation specs:
 * - Sleek, compact 66dp profile
 * - Fluid sliding indicator pill with elastic spring squish/stretch
 * - Expressive icon scale and bounce on selection
 * - Tonal elevated container with rounded top corners
 */
@Composable
fun ExpressiveNavigationBar(
    items: List<NavigationTabItem>,
    currentRoute: String?,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 66.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedIconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.primary,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensity.current
    val itemPositions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() } // index -> (centerX, pillWidth)

    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0
    val targetPos = itemPositions[selectedIndex] ?: Pair(0f, 64f)

    // Animated sliding center and width with spring physics
    val animatedPillCenterX by animateFloatAsState(
        targetValue = targetPos.first,
        animationSpec = spring(
            dampingRatio = 0.74f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navPillCenterX"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
        color = containerColor,
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .navigationBarsPadding()
        ) {
            // Continuous Sliding Active Pill Indicator
            if (targetPos.first > 0f) {
                val pillWidthDp = 58.dp
                val pillHeightDp = 30.dp
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (animatedPillCenterX - with(density) { (pillWidthDp / 2).toPx() }).toInt(),
                                y = with(density) { 6.dp.toPx() }.toInt()
                            )
                        }
                        .size(width = pillWidthDp, height = pillHeightDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(indicatorColor)
                )
            }

            // Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, tab ->
                    val isSelected = currentRoute == tab.route

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "tabIconScale"
                    )

                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) selectedIconColor else unselectedIconColor,
                        animationSpec = tween(180),
                        label = "tabIconColor"
                    )

                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) selectedTextColor else unselectedTextColor,
                        animationSpec = tween(180),
                        label = "tabTextColor"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                val pos = coordinates.positionInParent()
                                val width = coordinates.size.width.toFloat()
                                val centerX = pos.x + width / 2f
                                itemPositions[index] = Pair(centerX, width)
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        onNavigateToTab(tab.route)
                                    }
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon with bouncy spring scale
                        Box(
                            modifier = Modifier
                                .height(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
