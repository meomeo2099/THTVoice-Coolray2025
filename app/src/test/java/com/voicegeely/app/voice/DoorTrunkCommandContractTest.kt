package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import org.junit.Assert.assertEquals
import org.junit.Test

class DoorTrunkCommandContractTest {
    @Test
    fun doorLockAndUnlockContractsAreParkedOnly() {
        assertEquals(
            SafetyRequirement.PARKED_ONLY,
            SafetyPolicy.requirement(VoiceCommand.SetDoorLock(locked = true))
        )
        assertEquals(
            SafetyRequirement.PARKED_ONLY,
            SafetyPolicy.requirement(VoiceCommand.SetDoorLock(locked = false))
        )
    }

    @Test
    fun frontTrunkContractIsParkedOnly() {
        assertEquals(
            SafetyRequirement.PARKED_ONLY,
            SafetyPolicy.requirement(VoiceCommand.OpenFrontTrunk)
        )
    }

    @Test
    fun productionParserExposesDoorTrunkContracts() {
        assertEquals(
            VoiceCommand.SetDoorLock(locked = true),
            VoiceCommandParser.parse("khóa cửa")?.command
        )
        assertEquals(
            VoiceCommand.SetDoorLock(locked = false),
            VoiceCommandParser.parse("mở khóa cửa")?.command
        )
        assertEquals(
            VoiceCommand.OpenFrontTrunk,
            VoiceCommandParser.parse("mở cốp trước")?.command
        )
    }
}
