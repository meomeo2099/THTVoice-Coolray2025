package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseEchoTest {
    private val ownerHeard = listOf(
        "hegel", "hay galay", "hay ghe la", "he ghe", "hay gher", "hây ghê", "hay ghel",
        "hay ghe lay", "hay dây lay", "hay ghe đây", "hay gher đây", "hey geely", "hello geely", "hi geely"
    )

    @Test fun ownerReportedWakeEchoesAreOnlyWakePhrase() {
        for (text in ownerHeard) assertTrue(text, WakePhraseEcho.isOnlyWakePhrase(text))
    }

    @Test fun wakeEchoBeforeCommandIsRemoved() {
        for (text in ownerHeard) assertEquals(text, "mở nhạc", WakePhraseEcho.strip("$text mở nhạc"))
        assertEquals("bật điều hòa", WakePhraseEcho.strip("Hây ghê, bật điều hòa"))
    }

    @Test fun ordinaryCommandsAreUntouched() {
        for (text in listOf("mở nhạc", "hai mươi hai độ", "hay là mở nhạc", "ghế lái nóng", "hai kính", "")) {
            assertEquals(text, text, WakePhraseEcho.strip(text))
            assertFalse(text, WakePhraseEcho.isOnlyWakePhrase(text))
        }
    }
}
