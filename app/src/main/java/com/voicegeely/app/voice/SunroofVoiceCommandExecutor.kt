package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehicleDebugTrace
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Coolray 2025 sunroof glass and sunshade. REFERENCE (same platform reference, 21/10/2026):
 * - primary: vendor MCU property `INFO_ID_WINDOW_GROUP` (0x287000A2) via AdaptAPI
 *   `CarImpl.sendBytesToMcuByPropID`, index 4 = sunroof glass, 6 = sunshade, 0..100 %;
 *   position read back from `INFO_ID_WINDOW_SUNROOF` / `INFO_ID_WINDOW_ROLLSHADE`;
 * - glass fallback: ECARX AdaptAPI `BCM_FUNC_WINDOW_POS` / `BCM_FUNC_WINDOW` zone `WINDOW_ROOF_TOP` (4).
 * The sunshade has no AdaptAPI fallback. Success only after the position reads back at the target.
 */
class SunroofVoiceCommandExecutor(
    private val port: EcarxFunctionPort,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {
    private val support = EcarxCommandSupport(port, waitForReadback, VERIFY_DELAYS)

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (command is VoiceCommand.SetSunroofTilt) {
            val trace = VehicleDebugTrace.beginCommand("SUNROOF:TILT")
            return try { setTilt(command.on) }
            catch (error: Throwable) { CommandExecutionResult.Failed(error.message ?: "Lỗi điều khiển độ nghiêng cửa sổ trời.", error.javaClass.name) }
            finally { VehicleDebugTrace.endCommand(trace) }
        }
        if (command !is VoiceCommand.Sunroof) return CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm cửa sổ trời.")
        val traceId = VehicleDebugTrace.beginCommand("SUNROOF:${command.part.name}:${command.operation.name}")
        return try {
            val target = when (command.operation) {
                WindowOperation.OPEN -> 100
                WindowOperation.CLOSE -> 0
                WindowOperation.SET_PERCENT -> command.percent?.takeIf { it in 0..100 }
                    ?: return CommandExecutionResult.Rejected("Phần trăm cửa sổ trời phải từ 0 đến 100.")
                WindowOperation.STOP -> return if (command.part == SunroofPart.GLASS) stop()
                    else CommandExecutionResult.Rejected("Chưa hỗ trợ dừng rèm che nắng.")
            }
            if (command.part == SunroofPart.GLASS && target == 0 && tiltState() == F.ON) {
                val closed = setTilt(false)
                if (closed !is CommandExecutionResult.Confirmed) return closed
            }
            when (command.part) {
                SunroofPart.GLASS -> moveTo(F.GROUP_SUNROOF, target, "cửa sổ trời", glassFallback = true)
                SunroofPart.SHADE -> moveTo(F.GROUP_SUNSHADE, target, "rèm che nắng", glassFallback = false)
            }
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(t.message ?: "Lỗi khi điều khiển cửa sổ trời.", t::class.java.name)
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun moveTo(group: Int, target: Int, name: String, glassFallback: Boolean): CommandExecutionResult {
        val before = position(group, glassFallback)
        if (before != null && abs(before - target) <= TOLERANCE) return support.confirmed("$target phần trăm")
        var accepted = try { port.sendBytesToMcu(F.INFO_ID_WINDOW_GROUP, F.windowGroupPayload(group, target)) } catch (_: Throwable) { false }
        if (!accepted && glassFallback) {
            accepted = try { port.setFloat(F.BCM_FUNC_WINDOW_POS, ZONE, target.toFloat()) } catch (_: Throwable) { false }
            if (!accepted) accepted = try { port.setInt(F.BCM_FUNC_WINDOW_POS, ZONE, target) } catch (_: Throwable) { false }
            if (!accepted && (target == 0 || target == 100)) {
                accepted = try { port.setInt(F.BCM_FUNC_WINDOW, ZONE, if (target == 100) F.ON else F.OFF) } catch (_: Throwable) { false }
            }
        }
        if (!accepted) return CommandExecutionResult.Rejected("Xe không nhận lệnh $name.")
        if (port.isMock) return support.confirmed("$target phần trăm")
        var moved = false
        var previous = before
        for (delay in VERIFY_DELAYS) {
            if (!support.sleep(delay)) return support.interrupted()
            val actual = position(group, glassFallback) ?: continue
            if (abs(actual - target) <= TOLERANCE) return support.confirmed("$target phần trăm")
            if (previous != null && abs(target - actual) < abs(target - previous)) moved = true
            previous = actual
        }
        return CommandExecutionResult.AcceptedUnverified(
            if (moved) "${name.replaceFirstChar { it.uppercase() }} đang di chuyển về vị trí đích." else "Chưa đọc lại được vị trí $name."
        )
    }

    private fun stop(): CommandExecutionResult {
        val accepted = try { port.setInt(F.BCM_FUNC_WINDOW, ZONE, F.WINDOW_PAUSE) } catch (_: Throwable) { false }
        return if (accepted) CommandExecutionResult.AcceptedUnverified("Đã gửi lệnh dừng cửa sổ trời.")
        else CommandExecutionResult.Rejected("Xe không nhận lệnh dừng cửa sổ trời.")
    }

    private fun position(group: Int, glassFallback: Boolean): Int? {
        F.windowStateProp(group)?.let { prop ->
            (try { port.getIntByProp(prop, F.BCM_FUNC_WINDOW_POS) } catch (_: Throwable) { null })
                ?.takeIf { it in 0..100 }?.let { return it }
        }
        if (!glassFallback) return null
        support.readInt(F.BCM_FUNC_WINDOW_CURRENT_POS, ZONE)?.takeIf { it in 0..100 }?.let { return it }
        return (support.readFloat(F.BCM_FUNC_WINDOW_CURRENT_POS, ZONE) ?: support.readFloat(F.BCM_FUNC_WINDOW_POS, ZONE))
            ?.takeIf { it in 0f..100f }?.roundToInt()
    }

    private fun tiltState(): Int? =
        (try { port.getIntByProp(F.INFO_ID_WINDOW_SUNROOF_TILT, F.BCM_FUNC_WINDOW_POS) } catch (_: Throwable) { null })?.takeIf { it in 0..1 }
            ?: support.readInt(F.BCM_FUNC_SUNROOF_TILT, F.GLOBAL)?.takeIf { it in 0..1 }

    private fun setTilt(on: Boolean): CommandExecutionResult {
        if (port.isSupported(F.BCM_FUNC_SUNROOF_TILT, F.GLOBAL) != true) return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
        return support.writeIntVerify(F.BCM_FUNC_SUNROOF_TILT, F.GLOBAL, if (on) F.ON else F.OFF,
            "nghiêng cửa sổ trời", state = ::tiltState)
    }

    companion object {
        const val ZONE = F.WINDOW_ROOF_TOP
        const val TOLERANCE = 4
        /** Sunroof motors are slower than door windows: poll for up to ~10 s. */
        val VERIFY_DELAYS = longArrayOf(500L, 1000L, 1500L, 2000L, 2500L, 2500L)
    }
}
