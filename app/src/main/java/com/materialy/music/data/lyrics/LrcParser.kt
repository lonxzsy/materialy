package com.materialy.music.data.lyrics

import java.util.regex.Pattern

data class LrcLine(
    val timestampMs: Long,
    val text: String
)

object LrcParser {
    // Regex for [mm:ss.xx] and [mm:ss.xxx]
    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    fun parse(lrcContent: String): List<LrcLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lines = mutableListOf<LrcLine>()
        lrcContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val matcher = LRC_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val rawMillis = matcher.group(3) ?: "00"

                val millis = if (rawMillis.length == 2) {
                    (rawMillis.toLongOrNull() ?: 0L) * 10
                } else {
                    rawMillis.toLongOrNull() ?: 0L
                }

                val totalMs = minutes * 60_000L + seconds * 1_000L + millis
                val text = matcher.group(4)?.trim() ?: ""

                if (text.isNotBlank()) {
                    lines.add(LrcLine(timestampMs = totalMs, text = text))
                }
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    /**
     * Binary search to find index of current active lyric line
     */
    fun findCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
