package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlightHeightProductionParserIntegrationTest {
    @Test
    fun productionParserReturnsTypedHeadlightHeightCommand() {
        assertEquals(
            VoiceCommand.SetHeadlightHeight(3),
            VoiceCommandParser.parse("chỉnh độ cao đèn trước mức 3")?.command
        )
        assertEquals(
            VoiceCommand.SetHeadlightHeight(5),
            VoiceCommandParser.parse("đặt đèn pha chiều cao cấp năm")?.command
        )
    }

    @Test
    fun ambientLightingStillKeepsItsExistingMeaning() {
        val command = VoiceCommandParser.parse("đèn viền mức 3")?.command

        assertEquals(VoiceCommand.SetAmbientBrightness(3), command)
    }

    @Test
    fun unrelatedLightingPhrasesAreNotStolenAsHeadlightHeight() {
        val phrases = listOf(
            "bật đèn pha",
            "bật pha tự động",
            "bật đèn chiếu xa thông minh",
            "chỉnh độ sáng đèn viền mức 3",
            "chỉnh độ cao đèn trước mức 6"
        )

        phrases.forEach { text ->
            val command = VoiceCommandParser.parse(text)?.command
            assertFalse("Unexpected Headlight Height parse for: $text", command is VoiceCommand.SetHeadlightHeight)
        }
    }

    @Test
    fun supportedRangeRemainsZeroThroughFive() {
        for (level in 0..5) {
            val command = VoiceCommandParser.parse("chỉnh độ cao đèn trước mức $level")?.command
            assertTrue(command is VoiceCommand.SetHeadlightHeight)
            assertEquals(level, (command as VoiceCommand.SetHeadlightHeight).level)
        }
    }
}
