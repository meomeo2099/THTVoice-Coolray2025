package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.VoiceCommand

/** Safety level assigned to every typed voice command before execution. */
enum class SafetyRequirement {
    SAFE,
    CONFIRM_WHEN_MOVING,
    PARKED_ONLY
}

/** Central policy table. Keep this deterministic and independent of UI/ASR. */
object SafetyPolicy {
    fun requirement(command: VoiceCommand): SafetyRequirement = when (command) {
        is VoiceCommand.SetBrakePedalFeel,
        is VoiceCommand.ComfortableStop,
        is VoiceCommand.SetChargeCurrentLimit,
        is VoiceCommand.ParkingWiperAutoReduction,
        is VoiceCommand.SetParkingModeDuration,
        is VoiceCommand.SetManualDisplayBrightness,
        is VoiceCommand.SetDoorLock,
        VoiceCommand.OpenFrontTrunk,
        VoiceCommand.UnlockRearTrunk,
        is VoiceCommand.SetHeadlightHeight,
        is VoiceCommand.SetIntelligentHighBeam,
        is VoiceCommand.OpenSceneScreen -> SafetyRequirement.PARKED_ONLY
        is VoiceCommand.OemSwitch -> SafetyRequirement.PARKED_ONLY

        is VoiceCommand.ScreenBlackout ->
            if (command.on) SafetyRequirement.PARKED_ONLY else SafetyRequirement.SAFE

        is VoiceCommand.Window,
        is VoiceCommand.Sunroof,
        is VoiceCommand.SetSunroofTilt,
        is VoiceCommand.ExteriorLight,
        is VoiceCommand.FogLight,
        is VoiceCommand.SetDriveMode,
        is VoiceCommand.SetRegenLevel -> SafetyRequirement.CONFIRM_WHEN_MOVING

        else -> SafetyRequirement.SAFE
    }
}
