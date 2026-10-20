package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolrayLightingTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test fun cabinLightPhrases() {
        listOf("bật đèn trong xe", "mở đèn nội thất", "bật đèn trần", "bật đèn đọc sách", "bật giúp tôi đèn cabin").forEach {
            assertEquals(it, VoiceCommand.CabinLight(true), cmd(it))
        }
        listOf("tắt đèn trong xe", "tắt đèn cabin", "tắt giúp tôi đèn nội thất nhé").forEach {
            assertEquals(it, VoiceCommand.CabinLight(false), cmd(it))
        }
    }

    @Test fun exteriorAndFogPhrases() {
        assertEquals(VoiceCommand.ExteriorLight(ExteriorLightMode.LOW_BEAM), cmd("bật đèn cốt"))
        assertEquals(VoiceCommand.ExteriorLight(ExteriorLightMode.LOW_BEAM), cmd("bật đèn chiếu gần"))
        assertEquals(VoiceCommand.ExteriorLight(ExteriorLightMode.POSITION), cmd("bật đèn định vị"))
        assertEquals(VoiceCommand.ExteriorLight(ExteriorLightMode.OFF), cmd("tắt đèn pha"))
        assertEquals(VoiceCommand.ExteriorLight(ExteriorLightMode.OFF), cmd("tắt đèn ngoài xe"))
        assertEquals(VoiceCommand.FogLight(true), cmd("bật đèn sương mù"))
        assertEquals(VoiceCommand.FogLight(false), cmd("tắt đèn gầm"))
    }

    @Test fun otherLightingPhrasesAreNotStolen() {
        listOf(
            "bật đèn viền", "đèn viền mức 3", "bật đèn pha tự động", "bật đèn pha thông minh",
            "chỉnh độ cao đèn trước mức 3", "bật đèn nội thất khi mở cửa", "bật đèn ngoài", "bật tắt đèn trần"
        ).forEach { assertNull(it, CoolrayLightCommandParser.parse(it)) }
    }

    @Test fun exteriorLightsNeedConfirmationWhileMovingButCabinDoesNot() {
        assertEquals(SafetyRequirement.CONFIRM_WHEN_MOVING, SafetyPolicy.requirement(VoiceCommand.ExteriorLight(ExteriorLightMode.OFF)))
        assertEquals(SafetyRequirement.CONFIRM_WHEN_MOVING, SafetyPolicy.requirement(VoiceCommand.FogLight(true)))
        assertEquals(SafetyRequirement.SAFE, SafetyPolicy.requirement(VoiceCommand.CabinLight(true)))
    }

    @Test fun cabinLightUsesReadingLightSwitchAndConfirms() = runBlocking {
        val port = FakeEcarxPort()
        val result = CoolrayLightingVoiceCommandExecutor(port, waitForReadback = {}).execute(VoiceCommand.CabinLight(true))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(Triple(F.BCM_FUNC_LIGHT_READING_LIGHT, F.ZONE_ALL, F.ON), port.intWrites.single())
    }

    @Test fun exteriorAndFogUseBcmSwitches() = runBlocking {
        val port = FakeEcarxPort()
        val exec = CoolrayLightingVoiceCommandExecutor(port, waitForReadback = {})
        assertTrue(exec.execute(VoiceCommand.ExteriorLight(ExteriorLightMode.LOW_BEAM)) is CommandExecutionResult.Confirmed)
        assertEquals(F.ON, port.ints[F.BCM_FUNC_LIGHT_DIPPED_BEAM to F.ZONE_ALL])
        assertTrue(exec.execute(VoiceCommand.ExteriorLight(ExteriorLightMode.POSITION)) is CommandExecutionResult.Confirmed)
        assertEquals(F.OFF, port.ints[F.BCM_FUNC_LIGHT_DIPPED_BEAM to F.ZONE_ALL])
        assertEquals(F.ON, port.ints[F.BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS to F.ZONE_ALL])
        assertTrue(exec.execute(VoiceCommand.ExteriorLight(ExteriorLightMode.OFF)) is CommandExecutionResult.Confirmed)
        assertEquals(F.OFF, port.ints[F.BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS to F.ZONE_ALL])
        assertTrue(exec.execute(VoiceCommand.FogLight(true)) is CommandExecutionResult.Confirmed)
        assertEquals(F.ON, port.ints[F.BCM_FUNC_LIGHT_FRONT_FOG_LAMPS to F.ZONE_ALL])
    }

    @Test fun unreadableOrRejectedIsNotSuccess() = runBlocking {
        val unread = CoolrayLightingVoiceCommandExecutor(FakeEcarxPort(reflectWrites = false), waitForReadback = {})
            .execute(VoiceCommand.FogLight(true))
        assertTrue(unread is CommandExecutionResult.AcceptedUnverified)
        val refused = CoolrayLightingVoiceCommandExecutor(FakeEcarxPort(acceptWrites = false), waitForReadback = {})
            .execute(VoiceCommand.CabinLight(true))
        assertTrue(refused is CommandExecutionResult.Rejected)
        assertFalse(refused is CommandExecutionResult.Confirmed)
    }

    @Test fun inactiveLightFunctionIsRejected() = runBlocking {
        val port = FakeEcarxPort().apply { unsupported += F.BCM_FUNC_LIGHT_FRONT_FOG_LAMPS }
        val result = CoolrayLightingVoiceCommandExecutor(port, waitForReadback = {}).execute(VoiceCommand.FogLight(true))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(port.intWrites.isEmpty())
        assertNull(port.ints[F.BCM_FUNC_LIGHT_FRONT_FOG_LAMPS to F.ZONE_ALL])
    }
}
