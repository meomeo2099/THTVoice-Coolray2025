package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HvacTemperatureRegressionTest {
    @Test fun everySupportedBareNumericSetpointMapsToTemperature() {
        for (value in 16..32) {
            val expected = VoiceCommand.SetTemperature(value.toFloat())
            assertEquals("bare $value", expected, VoiceCommandParser.parse(value.toString())?.command)
            assertEquals("$value độ", expected, VoiceCommandParser.parse("$value độ")?.command)
            assertEquals("$value độ C", expected, VoiceCommandParser.parse("$value độ C")?.command)
        }
    }

    @Test fun spokenStandaloneSetpointsAreCanonicalizedBeforeParsing() {
        assertEquals(VoiceCommand.SetTemperature(20f), VoiceCommandParser.parse("hai mươi")?.command)
        assertEquals(VoiceCommand.SetTemperature(20f), VoiceCommandParser.parse("hai mươi độ")?.command)
        assertEquals(VoiceCommand.SetTemperature(22f), VoiceCommandParser.parse("hai mươi hai")?.command)
    }

    @Test fun haiHaiRequiresHvacContext() {
        assertNull(VoiceCommandParser.parse("hai hai"))
        assertEquals(
            VoiceCommand.SetTemperature(22f),
            VoiceCommandParser.parse("đặt nhiệt độ hai hai")?.command
        )
    }

    @Test fun whisperHiArtifactsRemainNumbersInsideHvacContext() {
        assertEquals(
            VoiceCommand.SetTemperature(20f),
            VoiceCommandParser.parse("đặt nhiệt độ hi mươi")?.command
        )
        assertEquals(
            VoiceCommand.SetTemperature(22f),
            VoiceCommandParser.parse("đặt nhiệt độ hi mươi hi")?.command
        )
    }

    @Test fun onlyExplicitStandaloneHiLoBecomeTemperatureEndpoints() {
        assertTrue(VoiceCommandParser.parse("hi")?.command is VoiceCommand.TemperatureHigh)
        assertTrue(VoiceCommandParser.parse("lo")?.command is VoiceCommand.TemperatureLow)
        assertNull(VoiceCommandParser.parse("hi mươi"))
        assertNull(VoiceCommandParser.parse("hi mươi hi"))
    }

    @Test fun fanEndpointWordsStayInFanDomain() {
        assertEquals(VoiceCommand.SetFanSpeed(8), VoiceCommandParser.parse("quạt tối đa")?.command)
        assertEquals(VoiceCommand.SetFanSpeed(1), VoiceCommandParser.parse("quạt thấp nhất")?.command)
    }

    @Test fun valuesOutsideCabinSetpointRangeAreNotImplicitTemperatureCommands() {
        assertNull(VoiceCommandParser.parse("15"))
        assertNull(VoiceCommandParser.parse("33"))
        assertNull(VoiceCommandParser.parse("20 phần trăm"))
    }
}
