package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehicleDebugTrace

/** Reference OEM controls. New writes require positive runtime support and matching read-back. */
class CoolrayOemCommandExecutor(
    private val port: EcarxFunctionPort,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {
    private val support = EcarxCommandSupport(port, waitForReadback)
    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        val trace = VehicleDebugTrace.beginCommand("OEM:${command.javaClass.simpleName}")
        return try {
            when (command) {
                is VoiceCommand.SetDriveMode -> {
                    val level = when (command.mode) { DriveMode.ECO -> 1; DriveMode.COMFORT -> 2; DriveMode.SPORT -> 3 }
                    write(F.DM_FUNC_DRIVE_MODE_SELECT, F.level(F.DM_FUNC_DRIVE_MODE_SELECT, level), "chế độ lái")
                }
                is VoiceCommand.HvacEco -> write(F.HVAC_FUNC_ECO_SWITCH, bit(command.on), "ECO điều hòa")
                is VoiceCommand.HvacAuto -> setHvacAuto(command.on)
                is VoiceCommand.SetAutoFanProfile -> {
                    val level = when (command.profile) { AutoFanProfile.QUIET -> 1; AutoFanProfile.NORMAL -> 2; AutoFanProfile.STRONG -> 3 }
                    write(F.HVAC_FUNC_AUTO_FAN_SETTING, F.level(F.HVAC_FUNC_AUTO_FAN_SETTING, level), "quạt điều hòa tự động")
                }
                is VoiceCommand.OemSwitch -> write(function(command.feature), bit(command.on), command.feature.label)
                is VoiceCommand.QueryOemSwitch -> {
                    if (port.isSupported(function(command.feature), F.GLOBAL) != true) {
                        return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
                    }
                    val actual = support.readInt(function(command.feature), F.GLOBAL)
                    when (actual) {
                        F.OFF, F.ON -> support.confirmed("${command.feature.label} đang ${if (actual == F.ON) "bật" else "tắt"}")
                        else -> CommandExecutionResult.Rejected("Không đọc được trạng thái ${command.feature.label}.")
                    }
                }
                else -> CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm điều khiển OEM.")
            }
        } catch (error: Throwable) {
            CommandExecutionResult.Failed(error.message ?: "Lỗi API điều khiển xe.", error.javaClass.name)
        } finally { VehicleDebugTrace.endCommand(trace) }
    }
    private fun write(function: Int, value: Int, name: String): CommandExecutionResult {
        if (port.isSupported(function, F.GLOBAL) != true) return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
        return support.writeIntVerify(function, F.GLOBAL, value, name)
    }
    private fun setHvacAuto(on: Boolean): CommandExecutionResult {
        if (port.isSupported(F.HVAC_FUNC_AUTO, F.GLOBAL) != true)
            return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
        val power = if (on) write(F.HVAC_FUNC_POWER, F.ON, "nguồn điều hòa") else null
        if (power is CommandExecutionResult.Rejected) return power
        if (power is CommandExecutionResult.Failed) return power
        val direct = write(F.HVAC_FUNC_AUTO, bit(on), "AUTO điều hòa")
        val mode = if (!on && direct is CommandExecutionResult.AcceptedUnverified)
            manualFanAutoOff(direct) else direct
        return if (power is CommandExecutionResult.AcceptedUnverified && mode is CommandExecutionResult.Confirmed)
            CommandExecutionResult.AcceptedUnverified("AUTO đã được API tiếp nhận, nhưng chưa xác nhận đủ trạng thái nguồn điều hòa.")
        else mode
    }
    /** Manual fan override exits AUTO on the owner's car. Same-level request remains REFERENCE. */
    private fun manualFanAutoOff(original: CommandExecutionResult.AcceptedUnverified): CommandExecutionResult {
        if (support.readInt(F.HVAC_FUNC_AUTO, F.GLOBAL) != F.ON ||
            port.isSupported(F.HVAC_FUNC_FAN_SPEED, F.GLOBAL) != true) return original
        fun fanLevel(): Int? = F.levelOf(F.HVAC_FUNC_FAN_SPEED, support.readInt(F.HVAC_FUNC_FAN_SPEED, F.ZONE_ROW_1_LEFT))
            ?.takeIf { it in 1..7 }
            ?: F.levelOf(F.HVAC_FUNC_FAN_SPEED, support.readInt(F.HVAC_FUNC_FAN_SPEED, F.GLOBAL))?.takeIf { it in 1..7 }
        val level = fanLevel() ?: return original
        // Force one manual request even when the numeric level already matches. Never raise/lower it.
        if (!port.setInt(F.HVAC_FUNC_FAN_SPEED, F.GLOBAL, F.level(F.HVAC_FUNC_FAN_SPEED, level))) return original
        for (delay in EcarxCommandSupport.DEFAULT_DELAYS) {
            if (!support.sleep(delay)) return support.interrupted()
            val auto = support.readInt(F.HVAC_FUNC_AUTO, F.GLOBAL)
            val fan = fanLevel()
            com.voicegeely.app.car.VehicleTelemetryRegistry.recordResult("ECARX",
                "AUTO_MANUAL_FAN requested=$level auto=${auto ?: "unknown"} fan=${fan ?: "unknown"}", F.HVAC_FUNC_AUTO, F.GLOBAL,
                if (auto == F.OFF && fan == level) "MATCH" else "UNVERIFIED")
            if (auto == F.OFF && fan == level) return support.confirmed()
        }
        return CommandExecutionResult.AcceptedUnverified("Đã gửi yêu cầu chuyển quạt sang điều khiển tay ở mức hiện tại, nhưng chưa xác nhận AUTO đã tắt.")
    }
    private fun bit(on: Boolean) = if (on) F.ON else F.OFF
    companion object {
        fun function(feature: OemFeature): Int = when (feature) {
            OemFeature.DAYTIME_LIGHTS -> F.SETTING_FUNC_LAMP_DAYTIME_RUNNING_LIGHT
            OemFeature.START_STOP -> F.SETTING_FUNC_ENGINE_STOP_START
            OemFeature.AUTO_HOLD -> F.SETTING_FUNC_AUTO_HOLD
            OemFeature.ESC_SPORT -> F.SETTING_FUNC_ESC_SPORT_MODE
        }
    }
}
