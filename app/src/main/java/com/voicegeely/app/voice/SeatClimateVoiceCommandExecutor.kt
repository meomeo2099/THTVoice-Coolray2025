package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehicleDebugTrace

/**
 * Coolray 2025 front seat heating / ventilation through the ECARX AdaptAPI. REFERENCE
 * (AdapterAPI 1.1.18.9 `IHvac`): `HVAC_FUNC_SEAT_HEATING` / `HVAC_FUNC_SEAT_VENTILATION`,
 * zone ROW_1_LEFT (1) driver / ROW_1_RIGHT (4) passenger, value OFF or `function + level` (1..3).
 * Step changes read the current level first (unreadable = no write). Success only after every seat
 * reads back its target.
 */
class SeatClimateVoiceCommandExecutor(
    private val port: EcarxFunctionPort,
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) }
) : VoiceCommandExecutor {
    private val support = EcarxCommandSupport(port, waitForReadback)

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (command !is VoiceCommand.SeatClimate) return CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm ghế.")
        if (command.seats.isEmpty()) return CommandExecutionResult.Rejected("Chưa xác định được ghế.")
        val traceId = VehicleDebugTrace.beginCommand("SEAT:${command.kind.name}")
        return try {
            val function = when (command.kind) {
                SeatClimateKind.HEAT -> F.HVAC_FUNC_SEAT_HEATING
                SeatClimateKind.VENT -> F.HVAC_FUNC_SEAT_VENTILATION
            }
            val zones = command.seats.map { zoneOf(it) }
            if (zones.none { support.supported(function, it) }) return CommandExecutionResult.Rejected(EcarxCommandSupport.NOT_ACTIVE)

            val targets = linkedMapOf<Int, Int>()
            for (zone in zones) {
                targets[zone] = when {
                    command.level != null -> clamp(command.level)
                    command.delta != null -> {
                        val current = read(function, zone)
                            ?: return CommandExecutionResult.Rejected("Không đọc được mức hiện tại của ghế, chưa thay đổi.")
                        clamp(current + command.delta)
                    }
                    else -> return CommandExecutionResult.Rejected("Thiếu mức cho ghế.")
                }
            }
            val pending = if (port.isMock) targets else targets.filter { (zone, level) -> read(function, zone) != level }
            for ((zone, level) in pending) {
                val accepted = try { port.setInt(function, zone, F.level(function, level)) } catch (t: Throwable) {
                    return CommandExecutionResult.Failed(t.message ?: "Lỗi khi gửi lệnh ghế.", t::class.java.name)
                }
                if (!accepted) return CommandExecutionResult.Rejected("Xe không nhận lệnh ghế.")
            }
            if (pending.isEmpty() || port.isMock) return confirmed(targets)
            var readable = false
            for (delay in EcarxCommandSupport.DEFAULT_DELAYS) {
                if (!support.sleep(delay)) return support.interrupted()
                val actual = pending.keys.associateWith { read(function, it) }
                if (actual.values.any { it != null }) readable = true
                if (actual.all { (zone, value) -> value == pending[zone] }) return confirmed(targets)
            }
            CommandExecutionResult.AcceptedUnverified(
                if (readable) "Mức ghế đọc lại chưa khớp lệnh." else "Không đọc lại được mức ghế."
            )
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(t.message ?: "Lỗi khi điều khiển ghế.", t::class.java.name)
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun read(function: Int, zone: Int): Int? =
        F.levelOf(function, support.readInt(function, zone))?.takeIf { it in 0..F.SEAT_LEVEL_MAX }

    private fun clamp(level: Int): Int = if (level <= 0) 0 else level.coerceIn(F.SEAT_LEVEL_MIN, F.SEAT_LEVEL_MAX)

    private fun confirmed(targets: Map<Int, Int>): CommandExecutionResult {
        val levels = targets.values.distinct()
        return support.confirmed(if (levels.size == 1) levels.single().toString() else null)
    }

    companion object {
        fun zoneOf(seat: SeatTarget): Int = when (seat) {
            SeatTarget.DRIVER -> F.ZONE_ROW_1_LEFT
            SeatTarget.PASSENGER -> F.ZONE_ROW_1_RIGHT
        }
    }
}
