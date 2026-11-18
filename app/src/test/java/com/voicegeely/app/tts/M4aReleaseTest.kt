package com.voicegeely.app.tts
import org.junit.Assert.*
import org.junit.Test
class M4aReleaseTest {
    @Test fun fallbackRequiresAuthorizationAndLegacyResponse() {
        assertTrue(M4aRelease.usePublicFallback(200, "mp3", true))
        assertTrue(M4aRelease.usePublicFallback(200, "", true))
        assertTrue(M4aRelease.usePublicFallback(404, null, true))
        for (format in listOf("m4a", "unknown")) assertFalse(M4aRelease.usePublicFallback(200, format, true))
        for (status in listOf(0, 401, 403, 429, 500, 502, 503)) assertFalse(M4aRelease.usePublicFallback(status, null, true))
        for (status in listOf(200, 404)) assertFalse(M4aRelease.usePublicFallback(status, "mp3", false))
    }
    @Test fun legacyMetadataMustBeWellFormed() {
        val sha = "a".repeat(64)
        assertTrue(M4aRelease.legacyMetadata(1, "", sha, 3))
        assertTrue(M4aRelease.legacyMetadata(1, "mp3", sha.uppercase(), 8*1024*1024))
        assertFalse(M4aRelease.legacyMetadata(0, "mp3", sha, 3))
        assertFalse(M4aRelease.legacyMetadata(1, "mp3", "bad", 3))
        assertFalse(M4aRelease.legacyMetadata(1, "unknown", sha, 3))
        for (size in listOf(-1, 0, 8*1024*1024+1)) assertFalse(M4aRelease.legacyMetadata(1, "mp3", sha, size))
    }
    @Test fun pinnedAssetsAreBoundedDistinctAndExact() {
        val a = M4aRelease.asset(true); val b = M4aRelease.asset(false)
        assertEquals(8565972, a.size); assertEquals(8332663, b.size)
        assertNotEquals(a.sha256, b.sha256)
        for (asset in listOf(a, b)) {
            assertTrue(asset.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(asset.url.startsWith("https://github.com/meomeo2099/THTVoice-Coolray2025/releases/download/voice-mp3-packs/THTVoice-M4A-"))
            assertTrue(asset.url.endsWith("-r6.zip"))
        }
    }
}
