package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleDebugTrace
import com.voicegeely.app.car.VehiclePropertyAccess

/**
 * Steering wheel heat, HVAC_STEERING_WHEEL_HEAT 289408269 area 0. Coolray 2025 config: int -2..2
 * (AOSP: positive = heating level, 0 = off, negative = cooling). On writes 2, off writes 0. State
 * reads 1..2 on, 0 off. Success only after read-back. REFERENCE.
 */
class SteeringWheelHeatVoiceCommandExecutor(
    private val vehicle: VehiclePropertyAccess,
    private val waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (command !is VoiceCommand.SteeringWheelHeat) return CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm sưởi vô lăng.")
        val traceId = VehicleDebugTrace.beginCommand("STEERING_HEAT:${command.on}")
        return try {
            val property = CoolrayProperties.STEERING_WHEEL_HEAT
            val area = CoolrayProperties.STEERING_WHEEL_HEAT_AREA
            if (read() == command.on) return confirmed()
            val first = if (command.on) CoolrayProperties.STEERING_WHEEL_HEAT_ON else CoolrayProperties.STEERING_WHEEL_HEAT_OFF
            val accepted = try { vehicle.setIntProperty(property, area, first) } catch (t: Throwable) {
                return CommandExecutionResult.Failed(t.message ?: "Lỗi khi gửi lệnh sưởi vô lăng.", t::class.java.name)
            }
            if (!accepted) return CommandExecutionResult.Rejected("Xe không nhận lệnh sưởi vô lăng.")
            if (vehicle.isMock) return confirmed()
            var readable = false
            for (delay in VERIFY_DELAYS) {
                if (!sleep(delay)) return CommandExecutionResult.AcceptedUnverified("Quá trình xác nhận bị gián đoạn.")
                val actual = read() ?: continue
                readable = true
                if (actual == command.on) return confirmed()
            }
            CommandExecutionResult.AcceptedUnverified(
                if (readable) "Trạng thái sưởi vô lăng đọc lại chưa khớp lệnh." else "Không đọc lại được trạng thái sưởi vô lăng."
            )
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(t.message ?: "Lỗi khi điều khiển sưởi vô lăng.", t::class.java.name)
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun read(): Boolean? = when (
        try { vehicle.getIntPropertyOrNull(CoolrayProperties.STEERING_WHEEL_HEAT, CoolrayProperties.STEERING_WHEEL_HEAT_AREA) } catch (_: Throwable) { null }
    ) {
        in 1..CoolrayProperties.STEERING_WHEEL_HEAT_MAX -> true
        0 -> false
        else -> null
    }

    private fun confirmed() = CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, null)
    private fun sleep(ms: Long): Boolean = try { waitForReadback(ms); true } catch (_: InterruptedException) {
        Thread.currentThread().interrupt(); false
    }

    private companion object {
        val VERIFY_DELAYS = longArrayOf(150L, 300L, 500L, 800L)
    }
}
