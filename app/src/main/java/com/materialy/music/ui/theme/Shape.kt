package com.materialy.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val MaterialyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object MaterialyShapeExtras {
    val chip = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
    val poster = RoundedCornerShape(16.dp)
    val dock = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val largeIncreased = RoundedCornerShape(24.dp)
    val extraLargeIncreased = RoundedCornerShape(32.dp)
}
