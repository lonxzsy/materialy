package com.materialy.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentIdTest {
    @Test fun roundTrip_preservesProviderTypeAndNativeId() {
        val id = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, "https://youtu.be/a:b?x=1")
        assertEquals(id, ContentId.decode(id.encode()))
    }

    @Test fun invalidValue_isRejected() {
        assertNull(ContentId.decode("legacy-number"))
    }

    @Test fun legacyAdapter_isPositiveStableAndNamespaced() {
        val id = ContentId(ContentProvider.YOUTUBE, ContentType.TRACK, "video-id")
        assertEquals(id.legacySongId(), id.legacySongId())
        assertEquals(true, id.legacySongId() > 0)
        assertEquals(true, ContentId.isLegacyProviderId(id.legacySongId()))
    }
}
