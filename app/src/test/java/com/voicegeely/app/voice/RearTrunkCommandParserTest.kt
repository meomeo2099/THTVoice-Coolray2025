package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RearTrunkCommandParserTest {
    @Test
    fun parsesExplicitRearTrunkAliases() {
        val aliases = listOf(
            "mở cốp sau",
            "mở cốp phía sau",
            "mở cốp đằng sau",
            "mở cốp xe sau",
            "mở cốp xe phía sau",
            "mở khóa cốp sau",
            "mở khoá cốp phía sau",
            "mở khóa cốp xe đằng sau"
        )

        aliases.forEach { text ->
            assertEquals(
                VoiceCommand.UnlockRearTrunk,
                RearTrunkCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun rejectsAmbiguousOrDifferentVehicleActions() {
        val rejected = listOf(
            "mở cốp",
            "mở cốp trước",
            "mở cốp phía trước",
            "mở capo",
            "mở cửa sau",
            "mở khóa cửa",
            "đóng cốp sau",
            "mở cốp sau một nửa",
            "mở cốp sau 50 phần trăm"
        )

        rejected.forEach { text ->
            assertNull(text, RearTrunkCommandParser.parse(text))
        }
    }

    @Test
    fun productionParserExposesRearTrunkAfterIntegration() {
        listOf(
            "mở cốp sau",
            "mở khóa cốp sau"
        ).forEach { text ->
            assertEquals(
                text,
                VoiceCommand.UnlockRearTrunk,
                VoiceCommandParser.parse(text)?.command
            )
        }
    }
}
