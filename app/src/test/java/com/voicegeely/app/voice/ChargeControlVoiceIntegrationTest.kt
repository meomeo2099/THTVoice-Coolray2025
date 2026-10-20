package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeControlVoiceIntegrationTest {
    @Test fun explicitStopAndStartNowPhrasesBecomeGuardedTypedCommands() {
        val stop = requireNotNull(VehicleStatusQueryParser.parse("dừng sạc xe")).command
            as VoiceCommand.SetChargeCurrentLimit
        assertEquals(ChargeControlAction.STOP, stop.control)
        assertEquals(0, stop.amps)

        val start = requireNotNull(VehicleStatusQueryParser.parse("bắt đầu sạc xe ngay")).command
            as VoiceCommand.SetChargeCurrentLimit
        assertEquals(ChargeControlAction.START_NOW, start.control)
        assertEquals(0, start.amps)

        val english = requireNotNull(ChargeControlVoiceParser.parse("start charging now")).command
            as VoiceCommand.SetChargeCurrentLimit
        assertEquals(ChargeControlAction.START_NOW, english.control)
    }

    @Test fun restartPhrasesBecomeGuardedTypedCommandsWhileAmbiguousChargingFailsClosed() {
        for (text in listOf("khởi động lại sạc", "tiếp tục sạc", "restart charging")) {
            val parsed = requireNotNull(ChargeControlVoiceParser.parse(text)).command
                as VoiceCommand.SetChargeCurrentLimit
            assertEquals(ChargeControlAction.RESTART, parsed.control)
        }
        for (text in listOf("bật sạc", "tắt sạc", "bật sạc không dây", "tắt sạc không dây")) {
            assertNull("text=$text", ChargeControlVoiceParser.parse(text))
        }
    }

    @Test fun restartPhrasesWorkAcrossAssistantParserChain() {
        for (text in listOf("khởi động lại sạc", "tiếp tục sạc", "restart charging")) {
            val parsed = VehicleStatusQueryParser.parse(text)
                ?: MediaVoiceParser.parse(text)
                ?: VoiceCommandParser.parse(text)
            val command = requireNotNull(parsed).command as VoiceCommand.SetChargeCurrentLimit
            assertEquals(ChargeControlAction.RESTART, command.control)
        }
    }

    @Test fun chargingControlRemainsParkedOnly() {
        val stop = VoiceCommand.SetChargeCurrentLimit(0, ChargeControlAction.STOP)
        val restart = VoiceCommand.SetChargeCurrentLimit(0, ChargeControlAction.RESTART)
        val start = VoiceCommand.SetChargeCurrentLimit(0, ChargeControlAction.START_NOW)
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(stop))
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(restart))
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(start))
    }

    @Test fun feedbackNeverCallsChargeControlZeroAmpere() {
        val stop = VoiceCommand.SetChargeCurrentLimit(0, ChargeControlAction.STOP)
        val restart = VoiceCommand.SetChargeCurrentLimit(0, ChargeControlAction.RESTART)
        val start = VoiceCommand.SetChargeCurrentLimit(0, ChargeControlAction.START_NOW)

        assertEquals("Đang dừng sạc.", VoiceResponseFormatter.acknowledgement(stop))
        assertEquals("Đang tiếp tục sạc.", VoiceResponseFormatter.acknowledgement(restart))
        assertEquals("Đang bắt đầu sạc ngay.", VoiceResponseFormatter.acknowledgement(start))

        val confirmed = CommandExecutionResult.Confirmed(
            VerificationSource.VEHICLE_READ_BACK,
            "đang sạc"
        )
        assertEquals("Đã bắt đầu sạc.", VoiceResponseFormatter.result(start, confirmed))
        assertEquals("Đã tiếp tục sạc.", VoiceResponseFormatter.result(restart, confirmed))
        assertFalse(VoiceResponseFormatter.result(start, confirmed).contains("ampe"))

        val unverified = CommandExecutionResult.AcceptedUnverified("read-back chưa khớp")
        val text = VoiceResponseFormatter.result(stop, unverified)
        assertTrue(text.contains("điều khiển sạc"))
        assertFalse(text.contains("ampe"))
    }

    @Test fun legacyDiagnosticPayloadSeparatesControlFromCurrentLimit() {
        val parsed = requireNotNull(ChargeControlVoiceParser.parse("dừng sạc xe"))
        val payload = LegacyPredictionBridge.toPayload(parsed)
        assertEquals("charge_control", payload.intent)
        assertEquals("stop", payload.entities["action"])
        assertFalse(payload.entities.containsKey("value"))
        assertFalse(payload.entities.containsKey("unit"))

        val restartParsed = requireNotNull(ChargeControlVoiceParser.parse("tiếp tục sạc"))
        val restartPayload = LegacyPredictionBridge.toPayload(restartParsed)
        assertEquals("charge_control", restartPayload.intent)
        assertEquals("restart", restartPayload.entities["action"])

        val limit = ParsedVoiceCommand(
            "đặt giới hạn dòng sạc 16 ampe",
            "đặt giới hạn dòng sạc 16 ampe",
            VoiceCommand.SetChargeCurrentLimit(16)
        )
        val limitPayload = LegacyPredictionBridge.toPayload(limit)
        assertEquals("charge_current_limit", limitPayload.intent)
        assertEquals(16, limitPayload.entities["value"])
    }
}
