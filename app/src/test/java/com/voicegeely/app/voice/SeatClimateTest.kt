package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatClimateTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command
    private val driver = setOf(SeatTarget.DRIVER)
    private val passenger = setOf(SeatTarget.PASSENGER)
    private val both = setOf(SeatTarget.DRIVER, SeatTarget.PASSENGER)

    @Test fun heatPhrasesFollowReferenceDefaults() {
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, level = 2), cmd("bật sưởi ghế"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, level = 2), cmd("bật sưởi ghế lái"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, passenger, level = 3), cmd("bật sưởi ghế phụ mức 3"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, both, level = 1), cmd("bật sưởi hai ghế trước mức một"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, level = 3), cmd("sưởi ghế lái cao nhất"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, level = 0), cmd("tắt sưởi ghế"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, delta = 1), cmd("tăng sưởi ghế"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, passenger, delta = -1), cmd("giảm sưởi ghế phụ"))
    }

    @Test fun ventilationPhrases() {
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.VENT, driver, level = 2), cmd("bật thông gió ghế"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.VENT, passenger, level = 2), cmd("bật làm mát ghế phụ"))
        assertEquals(VoiceCommand.SeatClimate(SeatClimateKind.VENT, both, level = 0), cmd("tắt thông gió cả hai ghế"))
    }

    @Test fun otherCommandsAreNotStolen() {
        assertNull(SeatClimateCommandParser.parse("bật sưởi gương"))
        assertNull(SeatClimateCommandParser.parse("thông gió ngoài"))
        assertNull(SeatClimateCommandParser.parse("bật sưởi ghế mức 5"))
        assertNull(SeatClimateCommandParser.parse("tăng nhiệt độ"))
        assertTrue(cmd("sấy gương") is VoiceCommand.Defrost)
    }

    @Test fun setWritesAdaptApiZonesAndLevelValues() = runBlocking {
        val port = FakeEcarxPort()
        val result = SeatClimateVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, both, level = 2))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(
            listOf(
                Triple(F.HVAC_FUNC_SEAT_HEATING, 1, F.HVAC_FUNC_SEAT_HEATING + 2),
                Triple(F.HVAC_FUNC_SEAT_HEATING, 4, F.HVAC_FUNC_SEAT_HEATING + 2)
            ),
            port.intWrites
        )
    }

    @Test fun offWritesZeroAndVentUsesVentFunction() = runBlocking {
        val port = FakeEcarxPort().apply { ints[F.HVAC_FUNC_SEAT_VENTILATION to 4] = F.HVAC_FUNC_SEAT_VENTILATION + 3 }
        val result = SeatClimateVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.SeatClimate(SeatClimateKind.VENT, passenger, level = 0))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(Triple(F.HVAC_FUNC_SEAT_VENTILATION, 4, 0), port.intWrites.single())
    }

    @Test fun stepClampsAndNeedsReadableCurrentLevel() = runBlocking {
        val port = FakeEcarxPort().apply { ints[F.HVAC_FUNC_SEAT_HEATING to 1] = F.HVAC_FUNC_SEAT_HEATING + 3 }
        SeatClimateVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, delta = 1))
        assertTrue(port.intWrites.isEmpty()) // already at 3, clamp keeps 3
        val unknown = SeatClimateVoiceCommandExecutor(FakeEcarxPort(), waitForReadback = {})
            .execute(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, delta = 1))
        assertTrue(unknown is CommandExecutionResult.Rejected)
    }

    @Test fun noReadBackIsNotSuccessAndRouterEnablesFamily() = runBlocking {
        val port = FakeEcarxPort(reflectWrites = false)
        val result = SeatClimateVoiceCommandExecutor(port, waitForReadback = {})
            .execute(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, driver, level = 1))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
        assertTrue(CoolrayCapabilities.isEnabled(CoolrayCapabilities.Family.SEAT_CLIMATE))
    }

    @Test fun levelHelpersFollowAdaptApiConvention() {
        assertEquals(0, F.level(F.HVAC_FUNC_SEAT_HEATING, 0))
        assertEquals(F.HVAC_FUNC_SEAT_HEATING + 3, F.level(F.HVAC_FUNC_SEAT_HEATING, 3))
        assertEquals(2, F.levelOf(F.HVAC_FUNC_SEAT_HEATING, F.HVAC_FUNC_SEAT_HEATING + 2))
        assertEquals(0, F.levelOf(F.HVAC_FUNC_SEAT_HEATING, 0))
        assertNull(F.levelOf(F.HVAC_FUNC_SEAT_HEATING, 999))
    }
}
