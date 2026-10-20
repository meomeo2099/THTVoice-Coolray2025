package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntelligentHighBeamCommandParserTest {
    @Test
    fun parsesExplicitAutomaticOrIntelligentHighBeamAliases() {
        listOf(
            "bật pha tự động",
            "bật đèn pha tự động",
            "bật đèn pha thông minh",
            "bật đèn chiếu xa tự động",
            "bật đèn chiếu xa thông minh"
        ).forEach { text ->
            assertEquals(
                VoiceCommand.SetIntelligentHighBeam(enabled = true),
                IntelligentHighBeamCommandParser.parse(text)?.command
            )
        }

        listOf(
            "tắt pha tự động",
            "tắt đèn pha tự động",
            "tắt đèn pha thông minh",
            "tắt đèn chiếu xa tự động",
            "tắt đèn chiếu xa thông minh"
        ).forEach { text ->
            assertEquals(
                VoiceCommand.SetIntelligentHighBeam(enabled = false),
                IntelligentHighBeamCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun doesNotStealManualOrOtherLightingCommands() {
        listOf(
            "bật đèn pha",
            "tắt đèn pha",
            "bật đèn trước",
            "tắt đèn trước",
            "chỉnh độ cao đèn trước mức 3",
            "bật đèn viền",
            "tắt đèn viền"
        ).forEach { text ->
            assertNull(text, IntelligentHighBeamCommandParser.parse(text))
        }
    }
}
