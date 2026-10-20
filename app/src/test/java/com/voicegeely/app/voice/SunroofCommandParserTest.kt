package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunroofCommandParserTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test fun glassOpenClose() {
        listOf("mở cửa sổ trời", "mở cửa sổ trời giúp tôi", "mở cửa trời", "mở sunroof", "mở kính nóc").forEach {
            assertEquals(it, VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.OPEN, 100), cmd(it))
        }
        listOf("đóng cửa sổ trời", "đóng cửa trời", "đóng kính nóc").forEach {
            assertEquals(it, VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.CLOSE, 0), cmd(it))
        }
    }

    @Test fun glassPercentHalfAndCrack() {
        assertEquals(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 50), cmd("mở cửa sổ trời 50%"))
        assertEquals(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 50), cmd("mở cửa sổ trời năm mươi phần trăm"))
        assertEquals(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 50), cmd("mở cửa sổ trời một nửa"))
        assertEquals(VoiceCommand.Sunroof(SunroofPart.GLASS, WindowOperation.SET_PERCENT, 10), cmd("hé cửa sổ trời"))
    }

    @Test fun shade() {
        assertEquals(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.OPEN, 100), cmd("mở rèm cửa sổ trời"))
        assertEquals(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.OPEN, 100), cmd("mở rèm che nắng"))
        assertEquals(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.CLOSE, 0), cmd("đóng rèm"))
        assertEquals(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.SET_PERCENT, 30), cmd("mở rèm 30 phần trăm"))
    }

    @Test fun ambiguousOrOutOfRangeFailsClosed() {
        assertNull(SunroofCommandParser.parse("dừng cửa sổ trời"))
        assertNull(SunroofCommandParser.parse("mở cửa sổ trời 150%"))
        assertNull(SunroofCommandParser.parse("cửa sổ trời"))
        assertNull(SunroofCommandParser.parse("mở đóng cửa sổ trời"))
        assertNull(SunroofCommandParser.parse("mở cửa sổ trời 2"))
    }

    @Test fun doorWindowsAreNotSunroof() {
        listOf("mở kính lái", "đóng tất cả kính", "mở cửa sổ bên lái").forEach {
            assertTrue(it, cmd(it) !is VoiceCommand.Sunroof)
        }
        assertNull(SunroofCommandParser.parse("mở kính trước bên phụ"))
    }
}
