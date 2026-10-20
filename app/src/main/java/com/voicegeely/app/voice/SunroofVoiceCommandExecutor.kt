package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehicleDebugTrace
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Coolray 2025 sunroof glass through the ECARX AdaptAPI, zone `WINDOW_ROOF_TOP` (4). REFERENCE:
 * same `BCM_FUNC_WINDOW_POS` / `BCM_FUNC_WINDOW` / `BCM_FUNC_WINDOW_CURRENT_POS` functions as the
 * door windows. The sunshade has no AdaptAPI actuator with a known zone on this head unit, so shade
 * commands are rejected without a write.
 */
class SunroofVoiceCommandExecutor(
    private val port: EcarxFunctionPort,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {
    private val support = EcarxCommandSupport(port, waitForReadback, VERIFY_DELAYS)

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (command !is VoiceCommand.Sunroof) return CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm cửa sổ trời.")
        if (command.part == SunroofPart.SHADE) return CommandExecutionResult.Rejected(SHADE_UNSUPPORTED)
        val traceId = VehicleDebugTrace.beginCommand("SUNROOF:${command.operation.name}")
        return try {
            if (!support.supported(F.BCM_FUNC_WINDOW_POS, ZONE) && !support.supported(F.BCM_FUNC_WINDOW, ZONE)) {
                return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
            }
            val target = when (command.operation) {
                WindowOperation.OPEN -> 100
                WindowOperation.CLOSE -> 0
                WindowOperation.SET_PERCENT -> command.percent?.takeIf { it in 0..100 }
                    ?: return CommandExecutionResult.Rejected("Phần trăm cửa sổ trời phải từ 0 đến 100.")
                WindowOperation.STOP -> return stop()
            }
            moveTo(target)
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(t.message ?: "Lỗi khi điều khiển cửa sổ trời.", t::class.java.name)
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun moveTo(target: Int): CommandExecutionResult {
        val before = position()
        if (before != null && abs(before - target) <= TOLERANCE) return support.confirmed("$target phần trăm")
        var accepted = try { port.setFloat(F.BCM_FUNC_WINDOW_POS, ZONE, target.toFloat()) } catch (_: Throwable) { false }
        if (!accepted && (target == 0 || target == 100)) {
            accepted = try { port.setInt(F.BCM_FUNC_WINDOW, ZONE, if (target == 100) F.ON else F.OFF) } catch (_: Throwable) { false }
        }
        if (!accepted) return CommandExecutionResult.Rejected("Xe không nhận lệnh cửa sổ trời.")
        if (port.isMock) return support.confirmed("$target phần trăm")
        var moved = false
        var previous = before
        for (delay in VERIFY_DELAYS) {
            if (!support.sleep(delay)) return support.interrupted()
            val actual = position() ?: continue
            if (abs(actual - target) <= TOLERANCE) return support.confirmed("$target phần trăm")
            if (previous != null && abs(target - actual) < abs(target - previous)) moved = true
            previous = actual
        }
        return CommandExecutionResult.AcceptedUnverified(
            if (moved) "Cửa sổ trời đang di chuyển về vị trí đích." else "Chưa đọc lại được vị trí cửa sổ trời."
        )
    }

    private fun stop(): CommandExecutionResult {
        val accepted = try { port.setInt(F.BCM_FUNC_WINDOW, ZONE, F.WINDOW_PAUSE) } catch (_: Throwable) { false }
        return if (accepted) CommandExecutionResult.AcceptedUnverified("Đã gửi lệnh dừng cửa sổ trời.")
        else CommandExecutionResult.Rejected("Xe không nhận lệnh dừng cửa sổ trời.")
    }

    private fun position(): Int? =
        (support.readFloat(F.BCM_FUNC_WINDOW_CURRENT_POS, ZONE) ?: support.readFloat(F.BCM_FUNC_WINDOW_POS, ZONE))
            ?.takeIf { it in 0f..100f }?.roundToInt()

    companion object {
        const val ZONE = F.WINDOW_ROOF_TOP
        const val TOLERANCE = 4
        const val SHADE_UNSUPPORTED = "Rèm che nắng chưa hỗ trợ điều khiển bằng giọng nói trên xe này."
        /** Sunroof motors are slower than door windows: poll for up to ~10 s. */
        val VERIFY_DELAYS = longArrayOf(500L, 1000L, 1500L, 2000L, 2500L, 2500L)
    }
}
