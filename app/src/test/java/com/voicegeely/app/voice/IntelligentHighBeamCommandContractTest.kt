package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentHighBeamCommandContractTest {
    @Test
    fun typedContractCarriesRequestedState() {
        assertTrue(VoiceCommand.SetIntelligentHighBeam(true).enabled)
        assertFalse(VoiceCommand.SetIntelligentHighBeam(false).enabled)
    }

    @Test
    fun contractIsParkedOnly() {
        assertEquals(
            SafetyRequirement.PARKED_ONLY,
            SafetyPolicy.requirement(VoiceCommand.SetIntelligentHighBeam(true))
        )
    }

    @Test
    fun legacyBridgeRemainsContractOnly() {
        val parsed = ParsedVoiceCommand(
            originalText = "bật đèn pha thông minh",
            normalizedText = "bật đèn pha thông minh",
            command = VoiceCommand.SetIntelligentHighBeam(true)
        )

        val payload = LegacyPredictionBridge.toPayload(parsed)

        assertEquals("contract_only_vehicle_command", payload.intent)
        assertEquals(true, payload.entities["enabled"])
    }

    @Test
    fun productionParserExposesIntelligentHighBeamAfterIntegration() {
        val parsed = VoiceCommandParser.parse("bật đèn pha thông minh")?.command
        assertEquals(VoiceCommand.SetIntelligentHighBeam(true), parsed)
    }

    @Test
    fun formatterMatchesProductionIntelligentHighBeamContract() {
        val command = VoiceCommand.SetIntelligentHighBeam(true)
        val acknowledgement = VoiceResponseFormatter.acknowledgement(command)
        val confirmed = VoiceResponseFormatter.result(
            command,
            CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "bật")
        )

        assertEquals("Đang bật đèn pha thông minh.", acknowledgement)
        assertEquals("Đã bật đèn pha thông minh.", confirmed)
    }
}
