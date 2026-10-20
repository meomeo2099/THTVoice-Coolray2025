package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntelligentHighBeamProductionParserIntegrationTest {
    @Test
    fun productionParserReturnsTypedIntelligentHighBeamCommands() {
        val cases = mapOf(
            "bật pha tự động" to true,
            "tắt đèn pha tự động" to false,
            "bật đèn pha thông minh" to true,
            "tắt đèn chiếu xa tự động" to false,
            "bật đèn chiếu xa thông minh" to true
        )

        cases.forEach { (text, enabled) ->
            assertEquals(
                VoiceCommand.SetIntelligentHighBeam(enabled),
                VoiceCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun manualAndOtherLightingPhrasesAreNotStolenAsIntelligentHighBeam() {
        val phrases = listOf(
            "bật đèn pha",
            "tắt đèn pha",
            "chỉnh độ cao đèn trước mức 3",
            "đèn viền mức 3"
        )

        phrases.forEach { text ->
            val command = VoiceCommandParser.parse(text)?.command
            assertFalse("Unexpected Intelligent High Beam parse for: $text", command is VoiceCommand.SetIntelligentHighBeam)
        }
    }

    @Test
    fun headlightHeightAndAmbientLightingKeepExistingMeaning() {
        assertEquals(
            VoiceCommand.SetHeadlightHeight(3),
            VoiceCommandParser.parse("chỉnh độ cao đèn trước mức 3")?.command
        )
        assertEquals(
            VoiceCommand.SetAmbientBrightness(3),
            VoiceCommandParser.parse("đèn viền mức 3")?.command
        )
    }
}
