package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehicleDebugTrace
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Coolray 2025 door windows through the ECARX AdaptAPI (the head unit exposes no window VHAL
 * property). REFERENCE (AdapterAPI 1.1.18.9, `IBcm`):
 * - target position: `BCM_FUNC_WINDOW_POS`, customize value 0..100 %, zones 16 / 32 / 256 / 512;
 * - when the position write is refused, open/close fall back once to `BCM_FUNC_WINDOW` ON / OFF;
 * - stop: `BCM_FUNC_WINDOW` = WINDOW_PAUSE;
 * - read-back: `BCM_FUNC_WINDOW_CURRENT_POS`, else `BCM_FUNC_WINDOW_POS`.
 * Success only after every window reads back at its target.
 */
class WindowVoiceCommandExecutor(
    private val port: EcarxFunctionPort,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {
    private val support = EcarxCommandSupport(port, waitForReadback, VERIFY_DELAYS)

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (command !is VoiceCommand.Window) return CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm kính.")
        val traceId = VehicleDebugTrace.beginCommand("WINDOW:${command.operation.name}")
        return try {
            val zones = resolveZones(command.targets)
            if (zones.isEmpty()) return CommandExecutionResult.Rejected("Chưa xác định được kính cần điều khiển.")
            if (zones.none { support.supported(F.BCM_FUNC_WINDOW_POS, it) || support.supported(F.BCM_FUNC_WINDOW, it) }) {
                return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)
            }
            if (command.operation == WindowOperation.STOP) return stop(zones)
            val percent = when (command.operation) {
                WindowOperation.OPEN -> 100
                WindowOperation.CLOSE -> 0
                WindowOperation.SET_PERCENT -> command.percent?.coerceIn(0, 100)
                    ?: return CommandExecutionResult.Rejected("Thiếu phần trăm vị trí kính.")
                WindowOperation.STOP -> error("handled above")
            }
            aggregate(zones.map { moveTo(it, percent) }, percent)
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(t.message ?: "Lỗi khi điều khiển kính.", t::class.java.name)
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun moveTo(zone: Int, percent: Int): ZoneResult {
        val before = position(zone)
        if (before != null && abs(before - percent) <= TOLERANCE) return ZoneResult(zone, true, true, false, before)
        var accepted = try { port.setFloat(F.BCM_FUNC_WINDOW_POS, zone, percent.toFloat()) } catch (_: Throwable) { false }
        if (!accepted && (percent == 0 || percent == 100)) {
            accepted = try { port.setInt(F.BCM_FUNC_WINDOW, zone, if (percent == 100) F.ON else F.OFF) } catch (_: Throwable) { false }
        }
        if (!accepted) return ZoneResult(zone, false, false, false, null)
        if (port.isMock) return ZoneResult(zone, true, true, false, percent)
        var previous = before
        var moved = false
        for (delay in VERIFY_DELAYS) {
            if (!support.sleep(delay)) break
            val actual = position(zone) ?: continue
            if (abs(actual - percent) <= TOLERANCE) return ZoneResult(zone, true, true, moved, actual)
            val reference = previous
            if (reference != null && abs(percent - actual) < abs(percent - reference)) moved = true
            previous = actual
        }
        return ZoneResult(zone, true, false, moved, previous)
    }

    private fun stop(zones: Set<Int>): CommandExecutionResult {
        val accepted = zones.filter { zone ->
            try { port.setInt(F.BCM_FUNC_WINDOW, zone, F.WINDOW_PAUSE) } catch (_: Throwable) { false }
        }
        return if (accepted.isNotEmpty()) {
            CommandExecutionResult.AcceptedUnverified("Đã gửi lệnh dừng ${accepted.joinToString { zoneName(it) }}.")
        } else CommandExecutionResult.Rejected("Xe không nhận lệnh dừng kính.")
    }

    private fun position(zone: Int): Int? =
        (support.readFloat(F.BCM_FUNC_WINDOW_CURRENT_POS, zone) ?: support.readFloat(F.BCM_FUNC_WINDOW_POS, zone))
            ?.takeIf { it in 0f..100f }?.roundToInt()

    private fun aggregate(results: List<ZoneResult>, requested: Int): CommandExecutionResult {
        if (results.any { !it.accepted }) {
            return CommandExecutionResult.Rejected("Xe không chấp nhận lệnh cho ${results.filter { !it.accepted }.joinToString { zoneName(it.zone) }}.")
        }
        if (results.all { it.confirmed }) {
            val actual = if (results.size == 1) results.first().actual ?: requested else requested
            return CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, "$actual phần trăm")
        }
        val moving = results.filter { it.moved }.joinToString { zoneName(it.zone) }
        return CommandExecutionResult.AcceptedUnverified(
            if (moving.isNotBlank()) "Kính $moving đang di chuyển về vị trí đích." else "Đã gửi lệnh nhưng chưa xác nhận được vị trí kính."
        )
    }

    private fun resolveZones(targets: Set<WindowTarget>): Set<Int> {
        if (WindowTarget.ALL in targets) return ALL_ZONES
        return targets.mapTo(linkedSetOf()) { zoneOf(it) }
    }

    private data class ZoneResult(val zone: Int, val accepted: Boolean, val confirmed: Boolean, val moved: Boolean, val actual: Int?)

    companion object {
        const val TOLERANCE = 3
        val VERIFY_DELAYS = longArrayOf(300L, 600L, 900L, 1200L, 1500L)
        val ALL_ZONES: Set<Int> = linkedSetOf(F.WINDOW_ROW_1_LEFT, F.WINDOW_ROW_1_RIGHT, F.WINDOW_ROW_2_LEFT, F.WINDOW_ROW_2_RIGHT)

        fun zoneOf(target: WindowTarget): Int = when (target) {
            WindowTarget.FRONT_LEFT -> F.WINDOW_ROW_1_LEFT
            WindowTarget.FRONT_RIGHT -> F.WINDOW_ROW_1_RIGHT
            WindowTarget.REAR_LEFT -> F.WINDOW_ROW_2_LEFT
            WindowTarget.REAR_RIGHT -> F.WINDOW_ROW_2_RIGHT
            WindowTarget.ALL -> error("ALL expands to every zone")
        }

        fun zoneName(zone: Int): String = when (zone) {
            F.WINDOW_ROW_1_LEFT -> "kính bên lái"
            F.WINDOW_ROW_1_RIGHT -> "kính trước bên phụ"
            F.WINDOW_ROW_2_LEFT -> "kính sau bên lái"
            F.WINDOW_ROW_2_RIGHT -> "kính sau bên phụ"
            F.WINDOW_ROOF_TOP -> "cửa sổ trời"
            else -> "kính vùng $zone"
        }
    }
}
