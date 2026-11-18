package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Gỡ kẹt màn hình" → THT Split `recover` (status bar gone after a ROM divider crash). */
class SplitRecoverCommandTest {
    private val recover = VoiceCommand.SplitScreen(SplitAction.RECOVER)

    @Test fun phrasesParseToRecover() {
        for (t in listOf(
            "gỡ kẹt màn hình", "gỡ kẹt", "gở kẹt màn hình", "gỡ kẹp màn hình", "sửa kẹt giao diện xe",
            "khôi phục giao diện", "khôi phục thanh trạng thái", "mất thanh trạng thái", "gỡ kẹt màn hình giùm"
        )) assertEquals(t, recover, VoiceParseChain.parse(t)?.command)
    }

    @Test fun otherSplitCommandsUnchanged() {
        assertEquals(VoiceCommand.SplitScreen(SplitAction.RESTORE), VoiceParseChain.parse("khôi phục chia màn hình")?.command)
        assertEquals(VoiceCommand.SplitScreen(SplitAction.STOP), VoiceParseChain.parse("tắt chia màn hình")?.command)
        assertTrue(VoiceParseChain.parse("mở cốp sau")?.command !is VoiceCommand.SplitScreen)
    }

    @Test fun formatterNamesIt() {
        val text = VoiceResponseFormatter.result(recover, CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE, null))
        assertTrue(text, text.contains("gỡ kẹt màn hình"))
    }
}
