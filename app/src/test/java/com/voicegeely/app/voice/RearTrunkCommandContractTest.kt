package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearTrunkCommandContractTest {
    @Test
    fun typedContractIsDedicatedRearTrunkAccessIntent() {
        assertEquals(VoiceCommand.UnlockRearTrunk, VoiceCommand.UnlockRearTrunk)
    }

    @Test
    fun contractIsParkedOnly() {
        assertEquals(
            SafetyRequirement.PARKED_ONLY,
            SafetyPolicy.requirement(VoiceCommand.UnlockRearTrunk)
        )
    }

    @Test
    fun legacyBridgeRemainsContractOnly() {
        val parsed = ParsedVoiceCommand(
            originalText = "mở cốp sau",
            normalizedText = "mở cốp sau",
            command = VoiceCommand.UnlockRearTrunk
        )

        val payload = LegacyPredictionBridge.toPayload(parsed)

        assertEquals("contract_only_vehicle_command", payload.intent)
        assertTrue(payload.entities.isEmpty())
    }

    @Test
    fun productionParserExposesRearTrunkAfterIntegration() {
        assertEquals(
            VoiceCommand.UnlockRearTrunk,
            VoiceCommandParser.parse("mở cốp sau")?.command
        )
        assertEquals(
            VoiceCommand.UnlockRearTrunk,
            VoiceCommandParser.parse("mở cốp phía sau")?.command
        )
    }

    @Test
    fun formatterReflectsProductionRuntimeWithoutClaimingRearTrunkOpened() {
        val command = VoiceCommand.UnlockRearTrunk
        val acknowledgement = VoiceResponseFormatter.acknowledgement(command)
        val confirmed = VoiceResponseFormatter.result(
            command,
            CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "unlocked")
        )
        val unverified = VoiceResponseFormatter.result(
            command,
            CommandExecutionResult.AcceptedUnverified("test")
        )

        assertTrue(acknowledgement.contains("mở khóa"))
        assertTrue(confirmed.contains("Đã mở khóa"))
        assertTrue(unverified.contains("Đã gửi lệnh"))
        assertFalse(acknowledgement.contains("chưa được nối vào production"))
        assertFalse(confirmed.contains("Đã mở cốp sau"))
        assertFalse(unverified.contains("Đã mở cốp sau"))
    }
}
