package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.RegenLevel
import com.voicegeely.app.voice.VehicleStatusQuery
import com.voicegeely.app.voice.VoiceCommand
import com.voicegeely.app.voice.WindowOperation
import com.voicegeely.app.voice.WindowTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyPolicyTest {
    @Test fun classifiesSensitiveVehicleCommands() {
        assertEquals(
            SafetyRequirement.CONFIRM_WHEN_MOVING,
            SafetyPolicy.requirement(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN))
        )
        assertEquals(
            SafetyRequirement.CONFIRM_WHEN_MOVING,
            SafetyPolicy.requirement(VoiceCommand.SetDriveMode(DriveMode.SPORT))
        )
        assertEquals(
            SafetyRequirement.CONFIRM_WHEN_MOVING,
            SafetyPolicy.requirement(VoiceCommand.SetRegenLevel(RegenLevel.HIGH))
        )
    }

    @Test fun leavesNormalCommandsSafe() {
        assertEquals(SafetyRequirement.SAFE, SafetyPolicy.requirement(VoiceCommand.SetTemperature(22f)))
        assertEquals(
            SafetyRequirement.SAFE,
            SafetyPolicy.requirement(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.BATTERY_SOC))
        )
    }
}
