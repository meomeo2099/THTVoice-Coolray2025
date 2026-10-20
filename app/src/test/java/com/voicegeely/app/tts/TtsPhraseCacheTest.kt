package com.voicegeely.app.tts

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPhraseCacheTest {
    private fun dir(): File = Files.createTempDirectory("tts-cache").toFile()
    private fun clip(n: Int, rate: Int = 24_000) = TtsPhraseCache.Clip(ByteArray(n) { (it % 251).toByte() }, rate)

    @Test fun roundTrip() {
        val c = TtsPhraseCache(dir())
        val k = c.key("đã bật điều hòa", "NGOC_HUYEN", 0, 1f, "pack1")
        assertNull(c.get(k))
        c.put(k, clip(1000))
        val got = c.get(k)!!
        assertEquals(24_000, got.sampleRate)
        assertArrayEquals(clip(1000).pcm16, got.pcm16)
    }

    @Test fun keyChangesWithTextVoiceSpeakerSpeedAndPack() {
        val c = TtsPhraseCache(dir())
        val base = c.key("đã bật điều hòa", "NGOC_HUYEN", 0, 1f, "pack1")
        assertNotEquals(base, c.key("đã gửi lệnh bật điều hòa nhưng chưa xác nhận", "NGOC_HUYEN", 0, 1f, "pack1"))
        assertNotEquals(base, c.key("đã bật điều hòa", "DIEM_TRINH", 0, 1f, "pack1"))
        assertNotEquals(base, c.key("đã bật điều hòa", "NGOC_HUYEN", 1, 1f, "pack1"))
        assertNotEquals(base, c.key("đã bật điều hòa", "NGOC_HUYEN", 0, 1.1f, "pack1"))
        assertNotEquals(base, c.key("đã bật điều hòa", "NGOC_HUYEN", 0, 1f, "pack2"))
        assertEquals(base, c.key("đã bật điều hòa", "NGOC_HUYEN", 0, 1f, "pack1"))
    }

    @Test fun onlyShortTextCacheable() {
        val c = TtsPhraseCache(dir())
        assertTrue(c.cacheable("Đã bật điều hòa."))
        assertFalse(c.cacheable(""))
        assertFalse(c.cacheable("a".repeat(TtsPhraseCache.MAX_TEXT_CHARS + 1)))
    }

    @Test fun evictsLeastRecentlyUsed() {
        val d = dir()
        val c = TtsPhraseCache(d, maxBytes = 2_500)
        c.put("a", clip(1000)); File(d, "a.pcm").setLastModified(1_000)
        c.put("b", clip(1000)); File(d, "b.pcm").setLastModified(2_000)
        c.get("a")  // touch a
        c.put("c", clip(1000))
        assertTrue(c.sizeBytes() <= 2_500)
        assertNull("oldest evicted", c.get("b"))
        assertTrue(c.get("a") != null && c.get("c") != null)
    }

    @Test fun corruptFileIsAMiss() {
        val d = dir()
        val c = TtsPhraseCache(d)
        File(d, "x.pcm").writeBytes(byteArrayOf(1, 2))
        assertNull(c.get("x"))
        File(d, "y.pcm").writeBytes(byteArrayOf(0, 0, 0, 0, 1, 2, 3, 4))
        assertNull("rate 0 rejected", c.get("y"))
    }

    @Test fun pcmConversionClamps() {
        val pcm = TtsPhraseCache.toPcm16(floatArrayOf(2f, -2f, 0f))
        assertEquals(6, pcm.size)
        assertEquals(0x7f.toByte(), pcm[1])
        assertEquals(0x80.toByte(), pcm[3])
    }
}
