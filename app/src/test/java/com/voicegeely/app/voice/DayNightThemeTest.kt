package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** From THTVoice (owner 15/11/2026): day / night screen wording switches the display theme. */
class DayNightThemeTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test
    fun dayNightWordingSwitchesTheDisplayTheme() {
        val dark = VoiceCommand.SetDisplayTheme(DisplayThemeMode.DARK)
        val light = VoiceCommand.SetDisplayTheme(DisplayThemeMode.LIGHT)
        val auto = VoiceCommand.SetDisplayTheme(DisplayThemeMode.AUTO)
        for (t in listOf("chế độ ban đêm", "bật chế độ ban đêm", "chuyển màn hình sang chế độ đêm", "màn hình ban đêm",
            "giao diện ban đêm", "tắt chế độ ban ngày", "giao diện tối")) assertEquals(t, dark, cmd(t))
        for (t in listOf("chế độ ban ngày", "bật chế độ ngày", "màn hình ban ngày", "tắt chế độ ban đêm",
            "giao diện sáng")) assertEquals(t, light, cmd(t))
        for (t in listOf("chế độ ngày đêm tự động", "màn hình ngày đêm tự động", "ngày đêm tự động",
            "giao diện tự động")) assertEquals(t, auto, cmd(t))
    }

    @Test
    fun askingDayOrNightNeverSwitches() {
        for (t in listOf("màn hình đang ở chế độ ngày hay đêm", "đang chế độ ngày hay đêm"))
            assertTrue(t, cmd(t) !is VoiceCommand.SetDisplayTheme)
    }

    @Test
    fun otherCommandsKeepTheirMeaning() {
        assertEquals(VoiceCommand.QueryLocalInfo(LocalInfoKind.DATE), cmd("hôm nay ngày bao nhiêu"))
    }
}
