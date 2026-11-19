package com.voicegeely.app.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real reply from the R6 pack ("Đã mở bản đồ.", 27 AAC-LC frames, 24 kHz mono). */
class Mp4ToAdtsTest {
    private val m4a: ByteArray = javaClass.getResourceAsStream("/tts/apps-68-r6.m4a")!!.readBytes()

    private fun frames(adts: ByteArray): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < adts.size) {
            assertEquals(0xFF, adts[i].toInt() and 0xFF)
            assertEquals(0xF1, adts[i + 1].toInt() and 0xFF)
            val len = ((adts[i + 3].toInt() and 3) shl 11) or ((adts[i + 4].toInt() and 0xFF) shl 3) or
                ((adts[i + 5].toInt() and 0xFF) shr 5)
            out += i to len
            i += len
        }
        assertEquals(adts.size, i)
        return out
    }

    @Test
    fun realReplyBecomesOneAdtsFramePerAacSample() {
        assertTrue(Mp4ToAdts.isMp4(m4a))
        val adts = Mp4ToAdts.convert(m4a)
        assertNotNull(adts)
        assertEquals(27, frames(adts!!).size)
        // Profile LC (1), 24 kHz (index 6), mono (1).
        val b2 = adts[2].toInt() and 0xFF
        assertEquals(1, b2 shr 6)
        assertEquals(6, (b2 shr 2) and 0x0F)
        val channels = ((b2 and 1) shl 2) or ((adts[3].toInt() and 0xFF) shr 6)
        assertEquals(1, channels)
    }

    @Test
    fun audioBytesAreCopiedUnchanged() {
        val adts = Mp4ToAdts.convert(m4a)!!
        val payload = java.io.ByteArrayOutputStream()
        frames(adts).forEach { (start, len) -> payload.write(adts, start + 7, len - 7) }
        val mdat = indexOf(m4a, "mdat")
        val mdatSize = ((m4a[mdat - 4].toInt() and 0xFF) shl 24) or ((m4a[mdat - 3].toInt() and 0xFF) shl 16) or
            ((m4a[mdat - 2].toInt() and 0xFF) shl 8) or (m4a[mdat - 1].toInt() and 0xFF)
        assertArrayEquals(m4a.copyOfRange(mdat + 4, mdat - 4 + mdatSize), payload.toByteArray())
    }

    @Test
    fun notMp4OrBrokenIsLeftAlone() {
        assertFalse(Mp4ToAdts.isMp4(byteArrayOf(0x49, 0x44, 0x33, 3, 0, 0, 0, 0, 0, 0, 0, 0)))
        assertNull(Mp4ToAdts.convert(ByteArray(64)))
        assertNull(Mp4ToAdts.convert(m4a.copyOf(200)))
    }

    private fun indexOf(data: ByteArray, type: String): Int {
        val t = type.toByteArray()
        for (i in 0..data.size - 4) if ((0 until 4).all { data[i + it] == t[it] }) return i
        return -1
    }
}
