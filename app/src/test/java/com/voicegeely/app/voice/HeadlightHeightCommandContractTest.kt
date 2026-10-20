package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadlightHeightCommandContractTest {
    @Test
    fun typedContractCarriesLevelAndIsParkedOnly() {
        val command = VoiceCommand.SetHeadlightHeight(level = 3)

        assertEquals(3, command.level)
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(command))
    }

    @Test
    fun legacyBridgeKeepsContractOnlyIntent() {
        val parsed = ParsedVoiceCommand(
            originalText = "chỉnh độ cao đèn trước mức 4",
            normalizedText = "chỉnh độ cao đèn trước mức 4",
            command = VoiceCommand.SetHeadlightHeight(level = 4)
        )

        val payload = LegacyPredictionBridge.toPayload(parsed)

        assertEquals("contract_only_vehicle_command", payload.intent)
        assertEquals(4, payload.entities["level"])
    }

    @Test
    fun productionParserExposesHeadlightHeightAfterIntegration() {
        val parsed = VoiceCommandParser.parse("chỉnh độ cao đèn trước mức 3")

        assertEquals(VoiceCommand.SetHeadlightHeight(level = 3), parsed?.command)
    }

    @Test
    fun formatterMatchesProductionHeadlightHeightContract() {
        val command = VoiceCommand.SetHeadlightHeight(level = 2)
        val acknowledgement = VoiceResponseFormatter.acknowledgement(command)
        val confirmed = VoiceResponseFormatter.result(
            command,
            CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "mức 2")
        )

        assertEquals("Đang chỉnh độ cao đèn trước về mức 2.", acknowledgement)
        assertEquals("Đã chỉnh độ cao đèn trước về mức 2.", confirmed)
    }
}
