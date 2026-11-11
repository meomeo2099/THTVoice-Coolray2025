package com.voicegeely.app.voice

import com.voicegeely.app.car.VehiclePropertyAccess
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoolrayOemCommandsTest {
    private val vehicle = object : VehiclePropertyAccess {
        override fun getIntProperty(propertyId: Int, areaId: Int) = 4
        override fun getFloatProperty(propertyId: Int, areaId: Int) = 0f
        override fun getBoolProperty(propertyId: Int, areaId: Int) = false
        override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean = error("OEM command must not write VHAL")
        override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean = error("OEM command must not write VHAL")
        override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean = error("OEM command must not write VHAL")
        override fun disconnect() {}
    }
    private fun router(port: FakeEcarxPort) = VehicleCommandRouter(vehicle, waitForReadback = {}, ecarx = port)

    @Test fun threeDriveModesUseTheirOemEnumsAndReadback() = runBlocking {
        for ((mode, value) in listOf(DriveMode.ECO to 0x22010101, DriveMode.COMFORT to 0x22010102, DriveMode.SPORT to 0x22010103)) {
            val port = FakeEcarxPort()
            val result = router(port).execute(VoiceCommand.SetDriveMode(mode))
            assertTrue("$mode: $result", result is CommandExecutionResult.Confirmed)
            assertEquals(Triple(0x22010100, 0, value), port.intWrites.single())
        }
    }
    @Test fun hvacEcoUsesClimateFunctionAndNeverDriveMode() = runBlocking {
        val port = FakeEcarxPort()
        val result = router(port).execute(VoiceCommand.HvacEco(true))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(Triple(0x10080100, 0, 1)), port.intWrites)
    }
    @Test fun unsupportedDriveModeHasNoWrites() = runBlocking {
        val port = FakeEcarxPort().apply { unsupported += 0x22010100 }
        assertTrue(router(port).execute(VoiceCommand.SetDriveMode(DriveMode.SPORT)) is CommandExecutionResult.Rejected)
        assertTrue(port.intWrites.isEmpty())
    }
    @Test fun acceptedButUnchangedDriveModeIsNotConfirmed() = runBlocking {
        val port = FakeEcarxPort(reflectWrites = false).apply { ints[0x22010100 to 0] = 0x22010101 }
        assertTrue(router(port).execute(VoiceCommand.SetDriveMode(DriveMode.SPORT)) is CommandExecutionResult.AcceptedUnverified)
    }
    @Test fun everySwitchUsesItsOwnGlobalFunctionAndPolarity() = runBlocking {
        val expected = mapOf(OemFeature.DAYTIME_LIGHTS to 0x20040800, OemFeature.START_STOP to 0x20020100,
            OemFeature.AUTO_HOLD to 0x20060400, OemFeature.ESC_SPORT to 0x20020300)
        for ((feature, function) in expected) {
            val port = FakeEcarxPort()
            assertTrue(router(port).execute(VoiceCommand.OemSwitch(feature, true)) is CommandExecutionResult.Confirmed)
            assertTrue(router(port).execute(VoiceCommand.OemSwitch(feature, false)) is CommandExecutionResult.Confirmed)
            assertEquals(listOf(Triple(function, 0, 1), Triple(function, 0, 0)), port.intWrites)
            val query = router(port).execute(VoiceCommand.QueryOemSwitch(feature)) as CommandExecutionResult.Confirmed
            assertTrue(query.actualValue.orEmpty().contains("đang tắt"))
            assertEquals(2, port.intWrites.size)
        }
    }
    @Test fun autoFanProfilesAndAutoClimateUseCorrectEncodings() = runBlocking {
        val port = FakeEcarxPort()
        for ((profile, value) in listOf(AutoFanProfile.QUIET to 0x10020201, AutoFanProfile.NORMAL to 0x10020202, AutoFanProfile.STRONG to 0x10020203)) {
            assertTrue(router(port).execute(VoiceCommand.SetAutoFanProfile(profile)) is CommandExecutionResult.Confirmed)
            assertEquals(Triple(0x10020200, 0, value), port.intWrites.last())
        }
        assertTrue(router(port).execute(VoiceCommand.HvacAuto(true)) is CommandExecutionResult.Confirmed)
        assertEquals(Triple(0x10010200, 0, 1), port.intWrites.last())
    }
    @Test fun refusalAndUnknownReadAreNeverSuccess() = runBlocking {
        val port = FakeEcarxPort(acceptWrites = false)
        assertTrue(router(port).execute(VoiceCommand.OemSwitch(OemFeature.AUTO_HOLD, true)) is CommandExecutionResult.Rejected)
        assertTrue(router(port).execute(VoiceCommand.QueryOemSwitch(OemFeature.AUTO_HOLD)) is CommandExecutionResult.Rejected)
    }
    @Test fun heatingIsExcludedAndRegenRemainsDisabled() = runBlocking {
        val port = FakeEcarxPort()
        assertTrue(router(port).execute(VoiceCommand.SeatClimate(SeatClimateKind.HEAT, setOf(SeatTarget.DRIVER), level = 1)) is CommandExecutionResult.Rejected)
        assertTrue(router(port).execute(VoiceCommand.SteeringWheelHeat(true)) is CommandExecutionResult.Rejected)
        assertTrue(port.intWrites.isEmpty())
        assertFalse(CoolrayCapabilities.isEnabled(CoolrayCapabilities.Family.DRIVE_REGEN))
    }
}
