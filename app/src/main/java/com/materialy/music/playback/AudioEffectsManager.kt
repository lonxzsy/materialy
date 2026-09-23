package com.materialy.music.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import com.materialy.music.data.audio.AudioSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

@Singleton
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioEffectsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioSettingsRepo: AudioSettingsRepository
) {
    private val TAG = "AudioEffectsManager"
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var visualizer: Visualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    private val _bassEnergy = MutableStateFlow(0.35f)
    val bassEnergy: StateFlow<Float> = _bassEnergy

    private var lastVisualizerUpdateMs = 0L
    private var beatTickerJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeJob: Job? = null

    private var lastNormEnabled = true
    private var lastAbsVolumeEnabled = false
    private var lastBoostDb = 6

    init {
        scope.launch {
            combine(
                audioSettingsRepo.equalizerEnabledFlow,
                audioSettingsRepo.bandLevelsFlow,
                audioSettingsRepo.bassBoostFlow,
                audioSettingsRepo.virtualizerFlow
            ) { enabled, bands, bass, virt ->
                applyEffects(enabled, bands, bass, virt)
            }.collect {}
        }
        scope.launch {
            combine(
                audioSettingsRepo.loudnessNormalizationFlow,
                audioSettingsRepo.absoluteVolumeEnabledFlow,
                audioSettingsRepo.absoluteVolumeBoostDbFlow
            ) { norm, absEnabled, boostDb ->
                lastNormEnabled = norm
                lastAbsVolumeEnabled = absEnabled
                lastBoostDb = boostDb
                updateLoudnessGain(norm, absEnabled, boostDb)
            }.collect {}
        }
        startBeatTicker()
    }

    private fun startBeatTicker() {
        beatTickerJob?.cancel()
        beatTickerJob = scope.launch {
            var tick = 0f
            while (isActive) {
                val now = System.currentTimeMillis()
                // If hardware visualizer hasn't updated in the last 350ms, run rhythmic pulse
                if (now - lastVisualizerUpdateMs > 350L) {
                    tick += 0.14f
                    val kick = (sin(tick.toDouble() * 2.2).toFloat() * 0.5f + 0.5f)
                    val sub = (sin(tick.toDouble() * 1.1 + 0.5).toFloat() * 0.5f + 0.5f)
                    val pulse = (kick * 0.65f + sub * 0.35f).coerceIn(0.15f, 0.95f)
                    val cur = _bassEnergy.value
                    _bassEnergy.value = cur * 0.72f + pulse * 0.28f
                }
                delay(30L)
            }
        }
    }

    private fun processFft(fft: ByteArray) {
        if (fft.isEmpty()) return
        lastVisualizerUpdateMs = System.currentTimeMillis()
        var bassSum = 0.0
        val numBins = minOf(6, (fft.size / 2) - 1).coerceAtLeast(1)
        for (i in 1..numBins) {
            val r = fft[2 * i].toDouble()
            val im = fft[2 * i + 1].toDouble()
            bassSum += hypot(r, im)
        }
        val rawBass = (bassSum / numBins / 48.0).toFloat().coerceIn(0f, 1f)
        val cur = _bassEnergy.value
        _bassEnergy.value = if (rawBass > cur) {
            (cur * 0.35f + rawBass * 0.65f).coerceIn(0f, 1f)
        } else {
            (cur * 0.88f + rawBass * 0.12f).coerceIn(0f, 1f)
        }
    }

    private fun processWaveform(waveform: ByteArray) {
        if (waveform.isEmpty()) return
        lastVisualizerUpdateMs = System.currentTimeMillis()
        var peak = 0
        for (b in waveform) {
            val amp = abs(b.toInt() - 128)
            if (amp > peak) peak = amp
        }
        val rawAmp = (peak / 128f).coerceIn(0f, 1f)
        val cur = _bassEnergy.value
        if (rawAmp > cur) {
            _bassEnergy.value = (cur * 0.4f + rawAmp * 0.6f).coerceIn(0f, 1f)
        }
    }

    fun attachPlayer(player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId <= 0 || sessionId == currentSessionId) return
        currentSessionId = sessionId
        initEffects(sessionId)
    }

    private fun initEffects(sessionId: Int) {
        releaseEffects()
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = false
            }
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = false
            }
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = false
            }
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                val baseGainMb = if (lastNormEnabled) 500 else 0
                val boostGainMb = if (lastAbsVolumeEnabled) (lastBoostDb.coerceIn(1, 15) * 100) else 0
                val totalGainMb = baseGainMb + boostGainMb
                setTargetGain(totalGainMb)
                enabled = lastNormEnabled || lastAbsVolumeEnabled
            }
            Log.d(TAG, "Audio effects and LoudnessEnhancer initialized for session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware audio effects unavailable: ${e.message}")
        }

        try {
            visualizer = Visualizer(sessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                if (range != null && range.isNotEmpty()) {
                    captureSize = range[0].coerceAtLeast(128)
                }
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        waveform?.let { processWaveform(it) }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft?.let { processFft(it) }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
            Log.d(TAG, "Visualizer attached to session $sessionId")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware Visualizer unavailable: ${e.message}")
        }
        startBeatTicker()
    }

    private fun applyEffects(enabled: Boolean, bands: List<Int>, bassStrength: Int, virtStrength: Int) {
        try {
            equalizer?.let { eq ->
                eq.enabled = enabled
                if (enabled) {
                    val numBands = eq.numberOfBands.toInt()
                    for (i in 0 until minOf(numBands, bands.size)) {
                        val levelMb = (bands[i] * 100).toShort()
                        val range = eq.bandLevelRange
                        val min = range[0]
                        val max = range[1]
                        val clamped = levelMb.coerceIn(min, max)
                        eq.setBandLevel(i.toShort(), clamped)
                    }
                }
            }

            bassBoost?.let { bb ->
                bb.enabled = enabled && bassStrength > 0
                if (bb.enabled) {
                    bb.setStrength(bassStrength.toShort())
                }
            }

            virtualizer?.let { virt ->
                virt.enabled = enabled && virtStrength > 0
                if (virt.enabled) {
                    virt.setStrength(virtStrength.toShort())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying audio effects: ${e.message}")
        }
    }

    fun fadeIn(controller: MediaController?, durationMs: Long = 1500L, targetVolume: Float = 1.0f) {
        val c = controller ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            c.volume = 0f
            c.play()
            val steps = 25
            val stepTime = (durationMs / steps).coerceAtLeast(15L)
            for (i in 1..steps) {
                if (!isActive) break
                val vol = (i.toFloat() / steps.toFloat()) * targetVolume
                c.volume = vol
                delay(stepTime)
            }
            c.volume = targetVolume
        }
    }

    fun fadeOut(controller: MediaController?, durationMs: Long = 1200L, onComplete: () -> Unit) {
        val c = controller ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startVolume = c.volume.coerceIn(0.1f, 1.0f)
            val steps = 20
            val stepTime = (durationMs / steps).coerceAtLeast(15L)
            for (i in (steps - 1) downTo 0) {
                if (!isActive) break
                val vol = (i.toFloat() / steps.toFloat()) * startVolume
                c.volume = vol
                delay(stepTime)
            }
            c.volume = 0f
            onComplete()
            c.volume = startVolume
        }
    }

    fun smoothTransition(controller: MediaController?, durationMs: Long = 1200L, onSwitch: () -> Unit) {
        val c = controller ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val halfDuration = durationMs / 2
            val steps = 15
            val stepTime = (halfDuration / steps).coerceAtLeast(15L)
            // Fade out
            for (i in (steps - 1) downTo 0) {
                if (!isActive) break
                c.volume = (i.toFloat() / steps.toFloat())
                delay(stepTime)
            }
            onSwitch()
            // Fade in
            for (i in 1..steps) {
                if (!isActive) break
                c.volume = (i.toFloat() / steps.toFloat())
                delay(stepTime)
            }
            c.volume = 1.0f
        }
    }

    private fun updateLoudnessGain(normEnabled: Boolean, absVolumeEnabled: Boolean, boostDb: Int) {
        try {
            val baseGainMb = if (normEnabled) 500 else 0
            val boostGainMb = if (absVolumeEnabled) (boostDb.coerceIn(1, 15) * 100) else 0
            val totalGainMb = baseGainMb + boostGainMb
            val shouldEnable = normEnabled || absVolumeEnabled

            loudnessEnhancer?.apply {
                setTargetGain(totalGainMb)
                this.enabled = shouldEnable
            }
            Log.d(TAG, "LoudnessEnhancer updated: gain=${totalGainMb}mB, enabled=$shouldEnable")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply loudness gain: ${e.message}")
        }
    }

    fun applyLoudnessNormalization(enabled: Boolean) {
        lastNormEnabled = enabled
        updateLoudnessGain(enabled, lastAbsVolumeEnabled, lastBoostDb)
    }

    fun applyAbsoluteVolume(enabled: Boolean, boostDb: Int = lastBoostDb) {
        lastAbsVolumeEnabled = enabled
        lastBoostDb = boostDb
        updateLoudnessGain(lastNormEnabled, enabled, boostDb)
    }

    fun releaseEffects() {
        beatTickerJob?.cancel()
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        visualizer = null
    }
}
