package com.voicegeely.app.voice

import org.junit.Assert.*
import org.junit.Test

class CoolrayOemCommandParserTest {
    @Test fun explicitSwitchesPreserveFeatureAndPolarity() {
        for ((name, feature) in listOf("auto hold" to OemFeature.AUTO_HOLD, "start stop" to OemFeature.START_STOP,
            "đèn ban ngày" to OemFeature.DAYTIME_LIGHTS, "esc sport" to OemFeature.ESC_SPORT)) {
            assertEquals(VoiceCommand.OemSwitch(feature, true), CoolrayOemCommandParser.parse("bật $name")?.command)
            assertEquals(VoiceCommand.OemSwitch(feature, false), CoolrayOemCommandParser.parse("tắt $name")?.command)
            assertEquals(VoiceCommand.QueryOemSwitch(feature), CoolrayOemCommandParser.parse("$name có bật không")?.command)
        }
    }
    @Test fun autoClimateAndFanProfilesAreExplicit() {
        // Owner 12/11/2026: bare AUTO now explicitly names the HVAC mode, never Auto Hold.
        assertEquals(VoiceCommand.HvacAuto(true), CoolrayOemCommandParser.parse("bật auto")?.command)
        assertEquals(VoiceCommand.HvacAuto(true), CoolrayOemCommandParser.parse("bật điều hòa tự động")?.command)
        assertEquals(VoiceCommand.HvacAuto(false), CoolrayOemCommandParser.parse("tắt auto điều hòa")?.command)
        assertEquals(VoiceCommand.SetAutoFanProfile(AutoFanProfile.QUIET), CoolrayOemCommandParser.parse("chỉnh quạt tự động sang êm")?.command)
        assertEquals(VoiceCommand.SetAutoFanProfile(AutoFanProfile.STRONG), CoolrayOemCommandParser.parse("chỉnh quạt auto sang mạnh")?.command)
    }
    @Test fun ambiguousEngineAndStabilityRequestsDoNotSelectSwitches() {
        for (text in listOf("tắt máy", "tắt cân bằng điện tử", "bật điều hòa", "chế độ lái eco", "tắt tất cả")) {
            assertNull(text, CoolrayOemCommandParser.parse(text))
        }
    }
}
