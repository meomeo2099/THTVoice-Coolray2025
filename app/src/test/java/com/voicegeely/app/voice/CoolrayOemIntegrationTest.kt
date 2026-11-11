package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import org.junit.Assert.*
import org.junit.Test

/** Runs with the complete production parse chain and centralized speech formatter in CI. */
class CoolrayOemIntegrationTest {
    @Test fun productionChainReachesEveryNewFamily() {
        val commands = listOf(
            "bật auto hold" to VoiceCommand.OemSwitch(OemFeature.AUTO_HOLD, true),
            "tắt start stop" to VoiceCommand.OemSwitch(OemFeature.START_STOP, false),
            "bật đèn ban ngày" to VoiceCommand.OemSwitch(OemFeature.DAYTIME_LIGHTS, true),
            "bật esc sport" to VoiceCommand.OemSwitch(OemFeature.ESC_SPORT, true),
            "auto hold có bật không" to VoiceCommand.QueryOemSwitch(OemFeature.AUTO_HOLD),
            "bật điều hòa tự động" to VoiceCommand.HvacAuto(true),
            "chỉnh quạt tự động sang êm" to VoiceCommand.SetAutoFanProfile(AutoFanProfile.QUIET),
            "nghiêng cửa sổ trời" to VoiceCommand.SetSunroofTilt(true),
            "chế độ lái eco" to VoiceCommand.SetDriveMode(DriveMode.ECO),
            "bật điều hòa tiết kiệm" to VoiceCommand.HvacEco(true)
        )
        for ((phrase, command) in commands) assertEquals(phrase, command, VoiceParseChain.parse(phrase)?.command)
    }
    @Test fun newSwitchesAreParkedOnlyAndQueriesRemainReadOnly() {
        for (feature in OemFeature.values()) {
            assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(VoiceCommand.OemSwitch(feature, true)))
            assertEquals(SafetyRequirement.SAFE, SafetyPolicy.requirement(VoiceCommand.QueryOemSwitch(feature)))
        }
        assertEquals(SafetyRequirement.CONFIRM_WHEN_MOVING, SafetyPolicy.requirement(VoiceCommand.SetSunroofTilt(true)))
    }
    @Test fun unverifiedSwitchCannotSpeakCompletedAction() {
        val command = VoiceCommand.OemSwitch(OemFeature.AUTO_HOLD, true)
        val speech = VoiceResponseFormatter.result(command, CommandExecutionResult.AcceptedUnverified("Chưa có phản hồi xe."))
        assertFalse(speech.contains("Đã bật"))
        assertTrue(speech.contains("chưa", ignoreCase = true))
    }
    @Test fun ambiguousOrNegatedDriveRequestsCannotSelectAnActuator() {
        for (phrase in listOf("chế độ lái eco hay sport", "không chuyển chế độ lái sport", "đừng chuyển chế độ sport", "chế độ lái eco và comfort")) {
            assertFalse(phrase, VoiceParseChain.parse(phrase)?.command is VoiceCommand.SetDriveMode)
        }
    }
}
