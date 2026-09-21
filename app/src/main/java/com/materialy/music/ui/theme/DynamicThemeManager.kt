package com.materialy.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.materialy.music.playback.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
class DynamicThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerManager: PlayerManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val imageLoader = ImageLoader(context)

    private val _artworkSeedColor = MutableStateFlow<Color?>(null)
    val artworkSeedColor: StateFlow<Color?> = _artworkSeedColor.asStateFlow()

    private val _dynamicDarkScheme = MutableStateFlow<ColorScheme?>(null)
    val dynamicDarkScheme: StateFlow<ColorScheme?> = _dynamicDarkScheme.asStateFlow()

    private val _dynamicLightScheme = MutableStateFlow<ColorScheme?>(null)
    val dynamicLightScheme: StateFlow<ColorScheme?> = _dynamicLightScheme.asStateFlow()

    init {
        scope.launch {
            playerManager.currentSong.collect { song ->
                val artwork = song?.artworkPath?.takeIf(String::isNotBlank)
                    ?: song?.fileUri?.takeIf { it.startsWith("http") }
                if (artwork.isNullOrBlank()) {
                    _artworkSeedColor.value = null
                    _dynamicDarkScheme.value = null
                    _dynamicLightScheme.value = null
                } else {
                    extractColorFromArtwork(artwork)
                }
            }
        }
    }

    private suspend fun extractColorFromArtwork(path: String) = withContext(Dispatchers.IO) {
        try {
            val data: Any = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                File(path)
            }

            val request = ImageRequest.Builder(context)
                .data(data)
                .size(Size(48, 48))
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            val drawable = result.drawable as? BitmapDrawable ?: return@withContext
            val bitmap = drawable.bitmap ?: return@withContext

            val dominantHsl = extractDominantHsl(bitmap)
            if (dominantHsl != null) {
                val (h, s, _) = dominantHsl
                val seedColor = hslToColor(h, s, 0.5f)
                _artworkSeedColor.value = seedColor
                _dynamicDarkScheme.value = buildMaterial3DarkScheme(h, s)
                _dynamicLightScheme.value = buildMaterial3LightScheme(h, s)
            }
        } catch (_: Exception) {}
    }

    private data class HueBin(
        var count: Int = 0,
        var sumS: Float = 0f,
        var sumR: Long = 0,
        var sumG: Long = 0,
        var sumB: Long = 0
    )

    private fun extractDominantHsl(bitmap: Bitmap): Triple<Float, Float, Float>? {
        val width = bitmap.width
        val height = bitmap.height
        val bins = Array(18) { HueBin() }

        for (x in 0 until width step 2) {
            for (y in 0 until height step 2) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                val (h, s, l) = rgbToHsl(r, g, b)

                // Prioritize vibrant pixels with healthy chroma and midtones
                // Discard near-black, pure whites, and lifeless grey
                if (l in 0.16f..0.85f && s >= 0.20f) {
                    val binIdx = ((h / 20f).toInt()).coerceIn(0, 17)
                    // High chroma weighting: saturated colors are favored over muddy backgrounds
                    val sWeight = (s * s * 100f).toLong().coerceAtLeast(1L)
                    bins[binIdx].count++
                    bins[binIdx].sumS += s
                    bins[binIdx].sumR += r * sWeight
                    bins[binIdx].sumG += g * sWeight
                    bins[binIdx].sumB += b * sWeight
                }
            }
        }

        // Score bins by average saturation squared * log(count + 1) with aesthetic hue weighting
        val bestBin = bins.maxByOrNull { bin ->
            if (bin.count < 3) return@maxByOrNull 0f
            val avgS = bin.sumS / bin.count
            val binIdx = bins.indexOf(bin)
            val centerHue = binIdx * 20f
            // Penalize muddy olive/brown (45..75 deg) if low saturation
            val hueWeight = if (centerHue in 40f..80f && avgS < 0.5f) 0.6f else 1.15f
            avgS * avgS * kotlin.math.ln(bin.count.toFloat() + 1f) * hueWeight
        }

        if (bestBin == null || bestBin.count < 3) {
            // Elegant fallback: deep electric violet
            return Triple(265f, 0.75f, 0.5f)
        }

        val totalWeight = bestBin.count
        val avgR = ((bestBin.sumR / totalWeight) / 100).toInt().coerceIn(0, 255)
        val avgG = ((bestBin.sumG / totalWeight) / 100).toInt().coerceIn(0, 255)
        val avgB = ((bestBin.sumB / totalWeight) / 100).toInt().coerceIn(0, 255)

        val (rawH, rawS, rawL) = rgbToHsl(avgR, avgG, avgB)
        // Normalize saturation to a tasteful, vibrant range for M3 Expressive
        val calibratedS = rawS.coerceIn(0.60f, 0.95f)
        return Triple(rawH, calibratedS, rawL)
    }

    private fun buildMaterial3DarkScheme(h: Float, s: Float): ColorScheme {
        val primary = hslToColor(h, (s * 1.05f).coerceIn(0.70f, 0.95f), 0.70f)
        val onPrimary = hslToColor(h, 0.45f, 0.10f)
        val primaryContainer = hslToColor(h, (s * 0.85f).coerceIn(0.50f, 0.85f), 0.28f)
        val onPrimaryContainer = hslToColor(h, 0.35f, 0.94f)

        val secondary = hslToColor((h + 24f) % 360f, (s * 0.65f).coerceIn(0.40f, 0.75f), 0.72f)
        val onSecondary = hslToColor((h + 24f) % 360f, 0.35f, 0.12f)
        val secondaryContainer = hslToColor((h + 24f) % 360f, (s * 0.60f).coerceIn(0.35f, 0.65f), 0.24f)
        val onSecondaryContainer = hslToColor((h + 24f) % 360f, 0.25f, 0.92f)

        val tertiary = hslToColor((h + 48f) % 360f, (s * 0.75f).coerceIn(0.45f, 0.80f), 0.74f)
        val onTertiary = hslToColor((h + 48f) % 360f, 0.40f, 0.12f)
        val tertiaryContainer = hslToColor((h + 48f) % 360f, (s * 0.70f).coerceIn(0.40f, 0.70f), 0.26f)
        val onTertiaryContainer = hslToColor((h + 48f) % 360f, 0.30f, 0.94f)

        val background = hslToColor(h, 0.14f, 0.06f)
        val onBackground = Color(0xFFF1EFF6)
        val surface = hslToColor(h, 0.16f, 0.07f)
        val onSurface = Color(0xFFF1EFF6)
        val surfaceVariant = hslToColor(h, 0.22f, 0.16f)
        val onSurfaceVariant = hslToColor(h, 0.20f, 0.80f)

        // Expressive 5-level autonomous SurfaceContainer hierarchy with prominent cover tint
        val surfaceContainerLowest = hslToColor(h, 0.14f, 0.04f)
        val surfaceContainerLow = hslToColor(h, 0.18f, 0.08f)
        val surfaceContainer = hslToColor(h, 0.24f, 0.12f)
        val surfaceContainerHigh = hslToColor(h, 0.28f, 0.16f)
        val surfaceContainerHighest = hslToColor(h, 0.32f, 0.20f)

        val outline = hslToColor(h, 0.22f, 0.44f)
        val outlineVariant = hslToColor(h, 0.18f, 0.24f)

        return darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant
        )
    }

    private fun buildMaterial3LightScheme(h: Float, s: Float): ColorScheme {
        val primary = hslToColor(h, (s * 1.05f).coerceIn(0.65f, 0.95f), 0.38f)
        val onPrimary = Color.White
        val primaryContainer = hslToColor(h, (s * 0.70f).coerceIn(0.35f, 0.80f), 0.88f)
        val onPrimaryContainer = hslToColor(h, 0.85f, 0.14f)

        val secondary = hslToColor((h + 24f) % 360f, (s * 0.50f).coerceIn(0.30f, 0.65f), 0.42f)
        val onSecondary = Color.White
        val secondaryContainer = hslToColor((h + 24f) % 360f, (s * 0.45f).coerceIn(0.30f, 0.60f), 0.86f)
        val onSecondaryContainer = hslToColor((h + 24f) % 360f, 0.80f, 0.14f)

        val tertiary = hslToColor((h + 48f) % 360f, (s * 0.55f).coerceIn(0.35f, 0.70f), 0.44f)
        val onTertiary = Color.White
        val tertiaryContainer = hslToColor((h + 48f) % 360f, (s * 0.50f).coerceIn(0.35f, 0.65f), 0.88f)
        val onTertiaryContainer = hslToColor((h + 48f) % 360f, 0.80f, 0.14f)

        val surface = hslToColor(h, 0.08f, 0.98f)
        val onSurface = Color(0xFF1B1B22)
        val surfaceVariant = hslToColor(h, 0.14f, 0.90f)
        val onSurfaceVariant = Color(0xFF47464F)

        val surfaceContainerLowest = Color.White
        val surfaceContainerLow = hslToColor(h, 0.12f, 0.95f)
        val surfaceContainer = hslToColor(h, 0.18f, 0.91f)
        val surfaceContainerHigh = hslToColor(h, 0.22f, 0.87f)
        val surfaceContainerHighest = hslToColor(h, 0.26f, 0.83f)

        val outline = hslToColor(h, 0.16f, 0.50f)
        val outlineVariant = hslToColor(h, 0.14f, 0.78f)

        return lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = outline,
            outlineVariant = outlineVariant
        )
    }

    companion object {
        fun rgbToHsl(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f

            val maxC = max(rf, max(gf, bf))
            val minC = min(rf, min(gf, bf))
            val delta = maxC - minC
            val l = (maxC + minC) / 2f

            val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))

            var h = when {
                delta == 0f -> 0f
                maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
                maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
                else -> 60f * (((rf - gf) / delta) + 4f)
            }
            if (h < 0f) h += 360f

            return Triple(h, s, l)
        }

        fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
            val h = (hue % 360f + 360f) % 360f
            val s = saturation.coerceIn(0f, 1f)
            val l = lightness.coerceIn(0f, 1f)

            val c = (1f - abs(2f * l - 1f)) * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = l - c / 2f

            val (r1, g1, b1) = when ((h / 60f).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

            return Color(
                red = (r1 + m).coerceIn(0f, 1f),
                green = (g1 + m).coerceIn(0f, 1f),
                blue = (b1 + m).coerceIn(0f, 1f),
                alpha = 1f
            )
        }
    }
}
