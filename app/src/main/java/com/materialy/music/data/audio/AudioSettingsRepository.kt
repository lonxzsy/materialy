package com.materialy.music.data.audio

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EqualizerPreset(
    val name: String,
    val levels: List<Int>,
    val isBuiltIn: Boolean = false
)

private val Context.audioSettingsDs by preferencesDataStore("audio_settings")

@Singleton
class AudioSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val BUILT_IN_PRESETS = listOf(
            EqualizerPreset("Обычный (Flat)", listOf(0, 0, 0, 0, 0), isBuiltIn = true),
            EqualizerPreset("Усиление басов", listOf(7, 5, 1, 0, 0), isBuiltIn = true),
            EqualizerPreset("Вокал", listOf(-2, 1, 6, 4, -1), isBuiltIn = true),
            EqualizerPreset("Рок", listOf(5, 3, -1, 3, 5), isBuiltIn = true),
            EqualizerPreset("Поп", listOf(-1, 2, 5, 2, -2), isBuiltIn = true),
            EqualizerPreset("Электроника", listOf(6, 4, 0, 2, 5), isBuiltIn = true),
            EqualizerPreset("Акустика / Джаз", listOf(3, 2, 1, 3, 4), isBuiltIn = true),
            EqualizerPreset("Классика", listOf(4, 3, -2, 3, 2), isBuiltIn = true)
        )
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val equalizerEnabledKey = booleanPreferencesKey("eq_enabled")
    private val selectedPresetNameKey = stringPreferencesKey("eq_preset_name")
    private val bandLevelsKey = stringPreferencesKey("eq_band_levels")
    private val bassBoostKey = intPreferencesKey("eq_bass_boost")
    private val virtualizerKey = intPreferencesKey("eq_virtualizer")
    private val customPresetsKey = stringPreferencesKey("eq_custom_presets")

    private val smoothAudioEnabledKey = booleanPreferencesKey("smooth_audio_enabled")
    private val smoothAudioDurationMsKey = longPreferencesKey("smooth_audio_duration_ms")
    private val loudnessNormalizationKey = booleanPreferencesKey("loudness_normalization_enabled")
    private val offlineModeKey = booleanPreferencesKey("offline_mode_enabled")

    val equalizerEnabledFlow: Flow<Boolean> = context.audioSettingsDs.data.map { it[equalizerEnabledKey] ?: false }
    val selectedPresetNameFlow: Flow<String> = context.audioSettingsDs.data.map { it[selectedPresetNameKey] ?: "Обычный (Flat)" }
    val bandLevelsFlow: Flow<List<Int>> = context.audioSettingsDs.data.map {
        val str = it[bandLevelsKey] ?: "0,0,0,0,0"
        str.split(",").mapNotNull { s -> s.trim().toIntOrNull() }.takeIf { list -> list.size == 5 } ?: listOf(0, 0, 0, 0, 0)
    }
    val bassBoostFlow: Flow<Int> = context.audioSettingsDs.data.map { it[bassBoostKey] ?: 0 }
    val virtualizerFlow: Flow<Int> = context.audioSettingsDs.data.map { it[virtualizerKey] ?: 0 }

    val customPresetsFlow: Flow<List<EqualizerPreset>> = context.audioSettingsDs.data.map {
        val raw = it[customPresetsKey] ?: ""
        if (raw.isBlank()) emptyList()
        else try { json.decodeFromString<List<EqualizerPreset>>(raw) } catch (_: Exception) { emptyList() }
    }

    val smoothAudioEnabledFlow: Flow<Boolean> = context.audioSettingsDs.data.map { it[smoothAudioEnabledKey] ?: true }
    val smoothAudioDurationMsFlow: Flow<Long> = context.audioSettingsDs.data.map { it[smoothAudioDurationMsKey] ?: 2000L }
    val loudnessNormalizationFlow: Flow<Boolean> = context.audioSettingsDs.data.map { it[loudnessNormalizationKey] ?: true }
    val offlineModeFlow: Flow<Boolean> = context.audioSettingsDs.data.map { it[offlineModeKey] ?: false }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.audioSettingsDs.edit { it[equalizerEnabledKey] = enabled }
    }

    suspend fun setPreset(preset: EqualizerPreset) {
        context.audioSettingsDs.edit {
            it[selectedPresetNameKey] = preset.name
            it[bandLevelsKey] = preset.levels.joinToString(",")
        }
    }

    suspend fun setBandLevel(bandIndex: Int, levelDb: Int) {
        context.audioSettingsDs.edit { prefs ->
            val curStr = prefs[bandLevelsKey] ?: "0,0,0,0,0"
            val list = curStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
            while (list.size < 5) list.add(0)
            if (bandIndex in 0..4) {
                list[bandIndex] = levelDb.coerceIn(-15, 15)
            }
            prefs[bandLevelsKey] = list.joinToString(",")
            prefs[selectedPresetNameKey] = "Пользовательский"
        }
    }

    suspend fun setBassBoost(strength: Int) {
        context.audioSettingsDs.edit { it[bassBoostKey] = strength.coerceIn(0, 1000) }
    }

    suspend fun setVirtualizer(strength: Int) {
        context.audioSettingsDs.edit { it[virtualizerKey] = strength.coerceIn(0, 1000) }
    }

    suspend fun saveCustomPreset(name: String, levels: List<Int>) {
        context.audioSettingsDs.edit { prefs ->
            val raw = prefs[customPresetsKey] ?: ""
            val list = if (raw.isBlank()) mutableListOf() else try { json.decodeFromString<List<EqualizerPreset>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
            list.removeAll { it.name.equals(name, ignoreCase = true) }
            val newPreset = EqualizerPreset(name = name.trim(), levels = levels.take(5), isBuiltIn = false)
            list.add(newPreset)
            prefs[customPresetsKey] = json.encodeToString(list)
            prefs[selectedPresetNameKey] = newPreset.name
            prefs[bandLevelsKey] = newPreset.levels.joinToString(",")
        }
    }

    suspend fun deleteCustomPreset(presetName: String) {
        context.audioSettingsDs.edit { prefs ->
            val raw = prefs[customPresetsKey] ?: ""
            if (raw.isNotBlank()) {
                val list = try { json.decodeFromString<List<EqualizerPreset>>(raw).toMutableList() } catch (_: Exception) { mutableListOf() }
                list.removeAll { it.name.equals(presetName, ignoreCase = true) }
                prefs[customPresetsKey] = json.encodeToString(list)
                if (prefs[selectedPresetNameKey] == presetName) {
                    prefs[selectedPresetNameKey] = "Обычный (Flat)"
                    prefs[bandLevelsKey] = "0,0,0,0,0"
                }
            }
        }
    }

    suspend fun setSmoothAudioEnabled(enabled: Boolean) {
        context.audioSettingsDs.edit { it[smoothAudioEnabledKey] = enabled }
    }

    suspend fun setSmoothAudioDurationMs(durationMs: Long) {
        context.audioSettingsDs.edit { it[smoothAudioDurationMsKey] = durationMs.coerceIn(500L, 5000L) }
    }

    suspend fun setLoudnessNormalization(enabled: Boolean) {
        context.audioSettingsDs.edit { it[loudnessNormalizationKey] = enabled }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.audioSettingsDs.edit { it[offlineModeKey] = enabled }
    }
}
