package com.materialy.music.domain.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.nio.ByteBuffer

enum class ContentProvider { YOUTUBE, LOCAL }

enum class ContentType { TRACK, ALBUM, ARTIST, PLAYLIST }

/** Stable, provider-owned identity. Database row ids must never cross this boundary. */
data class ContentId(
    val provider: ContentProvider,
    val type: ContentType,
    val nativeId: String
) {
    init {
        require(nativeId.isNotBlank()) { "nativeId must not be blank" }
    }

    fun encode(): String = listOf(
        provider.name,
        type.name,
        URLEncoder.encode(nativeId, StandardCharsets.UTF_8.name())
    ).joinToString(":")

    /** Temporary adapter for legacy Room/UI models that still expose Long row ids. */
    fun legacySongId(): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(encode().toByteArray(StandardCharsets.UTF_8))
        return (ByteBuffer.wrap(digest).long and 0x0FFF_FFFF_FFFF_FFFFL) or LEGACY_NAMESPACE
    }

    companion object {
        private const val LEGACY_NAMESPACE = 0x7000_0000_0000_0000L
        fun isLegacyProviderId(value: Long): Boolean = value and 0x7000_0000_0000_0000L == LEGACY_NAMESPACE
        fun decode(value: String): ContentId? {
            val parts = value.split(':', limit = 3)
            if (parts.size != 3) return null
            return runCatching {
                ContentId(
                    provider = ContentProvider.valueOf(parts[0]),
                    type = ContentType.valueOf(parts[1]),
                    nativeId = URLDecoder.decode(parts[2], StandardCharsets.UTF_8.name())
                )
            }.getOrNull()
        }
    }
}
