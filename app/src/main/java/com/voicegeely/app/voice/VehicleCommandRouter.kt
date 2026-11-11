package com.voicegeely.app.voice

import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.MockEcarxFunctionPort
import com.voicegeely.app.car.VehiclePropertyAccess

/**
 * Single execution entry point for typed vehicle commands on Coolray 2025.
 * HVAC / steering heat / status go through VHAL ([vehicle]); windows, sunroof, seats and lights go
 * through the ECARX AdaptAPI ([ecarx]). Without an AdaptAPI connection those families are rejected.
 * Families without a Coolray mapping are rejected by CoolrayCapabilities before routing;
 * the parser still recognises them so the user hears a clear "not supported" answer.
 */
class VehicleCommandRouter(
    vehicle: VehiclePropertyAccess,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) },
    /**
     * False when the head unit is not a recognised Coolray (see CoolrayHeadUnit). Then every
     * vehicle command except read-only status queries is rejected before any write.
     * Production passes the detection result; JVM tests use the default.
     */
    private val headUnitSupported: Boolean = true,
    /** ECARX AdaptAPI; null = not available on this head unit (body families rejected). Mock VHAL gets a mock port. */
    ecarx: EcarxFunctionPort? = if (vehicle.isMock) MockEcarxFunctionPort() else null
) : VoiceCommandExecutor {
    private val body: EcarxFunctionPort? = ecarx
    private val hvac = HvacVoiceCommandExecutor(vehicle, waitForReadback)
    private val windows = body?.let { WindowVoiceCommandExecutor(it, waitForReadback) }
    private val sunroof = body?.let { SunroofVoiceCommandExecutor(it, waitForReadback) }
    private val lights = body?.let { CoolrayLightingVoiceCommandExecutor(it, waitForReadback) }
    private val seats = body?.let { SeatClimateVoiceCommandExecutor(it, waitForReadback) }
    private val steering = SteeringWheelHeatVoiceCommandExecutor(vehicle, waitForReadback)
    private val status = VehicleStatusQueryExecutor(vehicle, body)
    private val oem = body?.let { CoolrayOemCommandExecutor(it, waitForReadback) }

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (!headUnitSupported && command !is VoiceCommand.QueryVehicleStatus) {
            return CommandExecutionResult.Rejected(UNSUPPORTED_HEAD_UNIT)
        }
        val family = CoolrayCapabilities.familyOf(command)
        if (family != null && !CoolrayCapabilities.isEnabled(family)) {
            return CommandExecutionResult.Rejected(CoolrayCapabilities.unsupportedReason(family))
        }
        return route(command)
    }

    private suspend fun route(command: VoiceCommand): CommandExecutionResult = when (command) {
        is VoiceCommand.Window -> windows?.execute(command) ?: noBody()
        is VoiceCommand.Sunroof,
        is VoiceCommand.SetSunroofTilt -> sunroof?.execute(command) ?: noBody()
        is VoiceCommand.CabinLight,
        is VoiceCommand.ExteriorLight,
        is VoiceCommand.FogLight -> lights?.execute(command) ?: noBody()
        is VoiceCommand.SeatClimate -> seats?.execute(command) ?: noBody()
        is VoiceCommand.SteeringWheelHeat -> steering.execute(command)
        is VoiceCommand.QueryVehicleStatus -> status.execute(command)
        is VoiceCommand.SetDriveMode,
        is VoiceCommand.HvacEco,
        is VoiceCommand.HvacAuto,
        is VoiceCommand.SetAutoFanProfile,
        is VoiceCommand.OemSwitch,
        is VoiceCommand.QueryOemSwitch -> oem?.execute(command) ?: noBody()
        is VoiceCommand.SetTemperature,
        is VoiceCommand.AdjustTemperature,
        VoiceCommand.TemperatureHigh,
        VoiceCommand.TemperatureLow,
        is VoiceCommand.AcPower,
        is VoiceCommand.SetFanSpeed,
        is VoiceCommand.AdjustFanSpeed,
        is VoiceCommand.Recirculation,
        is VoiceCommand.MaxAc,
        is VoiceCommand.Defrost,
        is VoiceCommand.FanDirection,
        is VoiceCommand.HvacAirflowAutoCycle -> hvac.execute(command)
        else -> CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm điều khiển xe.")
    }

    private fun noBody() = CommandExecutionResult.Rejected(EcarxCommandSupport.NO_BODY_API)

    companion object {
        const val UNSUPPORTED_HEAD_UNIT = "Không nhận ra màn hình xe, đã chặn lệnh điều khiển xe."
    }
}
