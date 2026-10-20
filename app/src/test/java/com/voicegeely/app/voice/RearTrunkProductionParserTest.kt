package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RearTrunkProductionParserTest {
    @Test
    fun explicitRearTrunkAliasesReachProductionParser() {
        listOf(
            "mở cốp sau",
            "mở cốp phía sau",
            "mở cốp xe đằng sau",
            "mở khóa cốp sau",
            "mở khoá cốp xe phía sau"
        ).forEach { text ->
            assertEquals(
                VoiceCommand.UnlockRearTrunk,
                VoiceCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun frontGenericAndDoorPhrasesKeepExistingBoundaries() {
        assertEquals(
            VoiceCommand.OpenFrontTrunk,
            VoiceCommandParser.parse("mở cốp trước")?.command
        )
        assertNull(VoiceCommandParser.parse("mở cốp"))
        assertNull(VoiceCommandParser.parse("đóng cốp sau"))
        assertNull(VoiceCommandParser.parse("mở cửa sau"))
    }
}
