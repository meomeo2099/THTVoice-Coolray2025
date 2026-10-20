package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirror heating shares the rear-defrost command (GalaxyVoiceAssistant AcControlHandler.setWindowHeat). */
class MirrorHeatParserTest {
    @Test
    fun mirrorHeatPhrasesMapToRearDefrostOn() {
        listOf(
            "bật sấy gương",
            "sấy gương",
            "sấy gương chiếu hậu",
            "bật sấy gương chiếu hậu",
            "sấy gương xe",
            "bật sưởi gương",
            "sưởi gương",
            "sưởi gương chiếu hậu",
            "sấy kính chiếu hậu",
            "bật sấy kính chiếu hậu",
            "mở sấy gương",
            "bật sấy gương giúp tôi",
            "gương bị mờ",
            "gương chiếu hậu bị mờ"
        ).forEach { text ->
            assertEquals(text, VoiceCommand.Defrost(DefrostZone.REAR, true), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun mirrorHeatOffPhrasesMapToRearDefrostOff() {
        listOf(
            "tắt sấy gương",
            "tắt sấy gương chiếu hậu",
            "tắt sưởi gương",
            "tắt sưởi gương chiếu hậu",
            "tắt sấy kính chiếu hậu",
            "tắt sấy gương xe",
            "tắt sấy gương giúp tôi"
        ).forEach { text ->
            assertEquals(text, VoiceCommand.Defrost(DefrostZone.REAR, false), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun rearWindowHauAliasesAreRearNotWindshield() {
        assertEquals(VoiceCommand.Defrost(DefrostZone.REAR, true), VoiceCommandParser.parse("sấy kính hậu")?.command)
        assertEquals(VoiceCommand.Defrost(DefrostZone.REAR, false), VoiceCommandParser.parse("tắt sấy kính hậu")?.command)
    }

    @Test
    fun windshieldDefrostStaysFront() {
        listOf("sấy kính", "sấy kính lái", "sấy kính trước", "bật sấy kính chắn gió").forEach { text ->
            assertEquals(text, VoiceCommand.Defrost(DefrostZone.FRONT, true), VoiceCommandParser.parse(text)?.command)
        }
    }
}
