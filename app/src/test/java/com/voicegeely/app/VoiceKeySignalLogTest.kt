package com.voicegeely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeySignalLogTest {
    @Test fun keepsLastEntriesWithRelativeTime() {
        VoiceKeySignalLog.clear()
        assertTrue(VoiceKeySignalLog.format().startsWith("Chưa ghi nhận"))
        for (i in 0 until VoiceKeySignalLog.MAX + 5) VoiceKeySignalLog.add(1_000L + i * 10, "xe", "raw=$i")
        val list = VoiceKeySignalLog.snapshot()
        assertEquals(VoiceKeySignalLog.MAX, list.size)
        assertEquals("raw=5", list.first().detail)
        val text = VoiceKeySignalLog.format()
        assertTrue(text.lines().first().startsWith("+0 ms  xe  raw=5"))
        assertTrue(text.lines().last().startsWith("+${(VoiceKeySignalLog.MAX - 1) * 10} ms"))
        VoiceKeySignalLog.clear()
    }
}
