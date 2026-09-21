package com.materialy.music.core.util

import com.materialy.music.data.db.entity.SongEntity
import kotlin.math.abs
import kotlin.random.Random

/**
 * Rule-based AutoMix — V1
 * Score = genre + bpm proximity + key distance + energy
 */
object AutoMix {

    // Camelot wheel distance approximation
    private fun keyDistance(a: String?, b: String?): Float {
        if (a == null || b == null) return 0.5f
        if (a == b) return 0f
        // simple: if same number different letter = 1, else 2-3
        val numA = a.filter { it.isDigit() }.toIntOrNull() ?: return 1f
        val numB = b.filter { it.isDigit() }.toIntOrNull() ?: return 1f
        val letterA = a.lastOrNull() ?: 'A'
        val letterB = b.lastOrNull() ?: 'A'
        val numDist = minOf(abs(numA - numB), 12 - abs(numA - numB))
        val letterDist = if (letterA == letterB) 0 else 1
        return (numDist * 0.15f + letterDist * 0.5f).coerceIn(0f, 1f)
    }

    fun score(from: SongEntity, to: SongEntity): Float {
        var s = 0f
        if (from.genre != null && from.genre == to.genre) s += 0.3f else s += 0.1f
        val bpmA = from.bpm
        val bpmB = to.bpm
        if (bpmA != null && bpmB != null) {
            s += (1f - (abs(bpmA - bpmB) / 20f).coerceIn(0f, 1f)) * 0.3f
        } else s += 0.15f
        s += (1f - keyDistance(from.musicalKey, to.musicalKey)) * 0.2f
        if (from.energy != null && to.energy != null) {
            s += (1f - abs(from.energy - to.energy)) * 0.2f
        } else s += 0.1f
        return s
    }

    fun autoMix(seed: SongEntity, pool: List<SongEntity>, limit: Int = 20): List<SongEntity> {
        if (pool.isEmpty()) return emptyList()
        val sorted = pool.filter { it.songId != seed.songId }
            .sortedByDescending { score(seed, it) }
        return sorted.take(limit)
    }

    fun smartShuffle(songs: List<SongEntity>): List<SongEntity> {
        if (songs.size <= 1) return songs
        val mutable = songs.toMutableList()
        // weighted Fisher-Yates: less played + older first weighted
        val now = System.currentTimeMillis()
        mutable.sortBy { song ->
            val days = (now - (song.lastPlayedAt ?: 0)) / (1000 * 60 * 60 * 24).toFloat().coerceAtLeast(0f)
            val weight = 1f / (song.playCount + days * 0.1f + 1f)
            // use weight to randomize: lower weight -> earlier
            Random.nextFloat() * weight
        }
        // anti-repeat artist window 5
        val result = mutableListOf<SongEntity>()
        val remaining = mutable.toMutableList()
        val recentArtists = ArrayDeque<String>()
        while (remaining.isNotEmpty()) {
            val candidateIndex = remaining.indexOfFirst { it.artistName !in recentArtists }
                .takeIf { it >= 0 } ?: 0
            val song = remaining.removeAt(candidateIndex)
            result += song
            recentArtists.addLast(song.artistName)
            if (recentArtists.size > 2) recentArtists.removeFirst()
        }
        return result
    }
}
