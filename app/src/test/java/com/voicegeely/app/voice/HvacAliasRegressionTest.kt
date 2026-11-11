package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class HvacAliasRegressionTest {
    @Test fun fanLevelAliasesNormalizeToCanonicalFanCommands() {
        assertEquals(VoiceCommand.SetFanSpeed(3), VoiceCommandParser.parse("quạt số ba")?.command)
        assertEquals(VoiceCommand.SetFanSpeed(2), VoiceCommandParser.parse("gió mức hai")?.command)
        assertEquals(VoiceCommand.SetFanSpeed(8), VoiceCommandParser.parse("gió mạnh nhất")?.command)
        assertEquals(VoiceCommand.SetFanSpeed(1), VoiceCommandParser.parse("gió yếu nhất")?.command)
    }

    @Test fun fanAdjustmentAliasesPreserveRequestedDelta() {
        assertEquals(VoiceCommand.AdjustFanSpeed(2), VoiceCommandParser.parse("tăng gió hai mức")?.command)
        assertEquals(VoiceCommand.AdjustFanSpeed(-1), VoiceCommandParser.parse("giảm gió")?.command)
    }

    @Test fun recirculationAirAliasesMapToExistingTypedCommands() {
        assertEquals(VoiceCommand.Recirculation(true), VoiceCommandParser.parse("lấy khí trong")?.command)
        assertEquals(VoiceCommand.Recirculation(false), VoiceCommandParser.parse("lấy khí ngoài")?.command)
    }

    @Test fun defrostAliasesStayInDefrostDomain() {
        assertEquals(
            VoiceCommand.Defrost(DefrostZone.FRONT, true),
            VoiceCommandParser.parse("khử sương kính trước")?.command
        )
        assertEquals(
            VoiceCommand.Defrost(DefrostZone.REAR, true),
            VoiceCommandParser.parse("khử mờ kính sau")?.command
        )
        assertEquals(
            VoiceCommand.Defrost(DefrostZone.REAR, false),
            VoiceCommandParser.parse("tắt làm tan sương kính sau")?.command
        )
    }

    @Test fun shortFanDirectionAliasesMapToCanonicalDirections() {
        assertEquals(
            VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE)),
            VoiceCommandParser.parse("gió mặt")?.command
        )
        assertEquals(
            VoiceCommand.FanDirection(setOf(FanDirectionTarget.FEET)),
            VoiceCommandParser.parse("gió chân")?.command
        )
    }

    @Test fun naturalTemperatureAdjustmentAliasesKeepDirectionAndAmount() {
        assertEquals(VoiceCommand.AdjustTemperature(0.5f), VoiceCommandParser.parse("ấm lên")?.command)
        assertEquals(VoiceCommand.AdjustTemperature(1f), VoiceCommandParser.parse("nóng lên một độ")?.command)
        assertEquals(VoiceCommand.AdjustTemperature(-0.5f), VoiceCommandParser.parse("mát xuống")?.command)
        assertEquals(VoiceCommand.AdjustTemperature(-3f), VoiceCommandParser.parse("lạnh xuống ba độ")?.command)
    }
}
