package com.voicegeely.app.voice

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehicleDebugTrace
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlin.math.abs

/** Coolray 2025 HVAC executor (standard VHAL, area 75 / temperature areas 31 & 44). REFERENCE. */
class HvacVoiceCommandExecutor(
    private val vehicle: VehiclePropertyAccess,
    private val waitForReadback: (Long) -> Unit = { Thread.sleep(it) },
    private val minTemp: Float = CoolrayProperties.HVAC_MIN_TEMP,
    private val maxTemp: Float = CoolrayProperties.HVAC_MAX_TEMP
) : VoiceCommandExecutor {

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        val traceId = VehicleDebugTrace.beginCommand("HVAC:${command.javaClass.simpleName}")
        return try {
            when (command) {
                is VoiceCommand.SetTemperature -> setTemperature(command.celsius)
                is VoiceCommand.AdjustTemperature -> adjustTemperature(command.deltaCelsius)
                VoiceCommand.TemperatureHigh -> setTemperature(maxTemp, Endpoint.HIGH)
                VoiceCommand.TemperatureLow -> setTemperature(minTemp, Endpoint.LOW)
                is VoiceCommand.AcPower -> setAcPower(command.on, command.target)
                is VoiceCommand.SetFanSpeed -> setFanSpeed(command.level)
                is VoiceCommand.AdjustFanSpeed -> adjustFanSpeed(command.delta)
                is VoiceCommand.Recirculation -> setRecirculation(command.enabled)
                is VoiceCommand.MaxAc -> writeAndVerifyBool(
                    CoolrayProperties.HVAC_MAX_AC,
                    CoolrayProperties.HVAC_MAX_AC_AREA,
                    command.on,
                    "Xe không chấp nhận lệnh làm lạnh nhanh.",
                    if (command.on) "đang bật" else "đã tắt"
                )
                is VoiceCommand.Defrost -> setDefrost(command.zone, command.on)
                is VoiceCommand.FanDirection -> setFanDirection(command.directions)
                is VoiceCommand.HvacEco -> setHvacEco(command.on)
                // Coolray has no car-side automatic airflow cycle, so plain "bật/tắt đảo gió" drives
                // THT Voice's periodic swing (interval from Settings).
                is VoiceCommand.HvacAirflowAutoCycle -> setPeriodicAirflow(command.on, command.periodicMinutes ?: 0)
                else -> CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm HVAC.")
            }
        } finally {
            VehicleDebugTrace.endCommand(traceId)
        }
    }

    private fun adjustTemperature(delta: Float): CommandExecutionResult {
        val current = safeFloat(CoolrayProperties.HVAC_TEMP_SET, CoolrayProperties.HVAC_TEMP_AREA)
        if (current == null || current !in minTemp..maxTemp) return CommandExecutionResult.Rejected("Không đọc được nhiệt độ hiện tại của xe.")
        return setTemperature(roundToStep(current + delta).coerceIn(minTemp, maxTemp))
    }

    private fun setTemperature(rawRequested: Float, endpoint: Endpoint? = null): CommandExecutionResult {
        val requested = roundToStep(rawRequested)
        if (!requested.isFinite() || requested !in minTemp..maxTemp) {
            return CommandExecutionResult.Rejected("Nhiệt độ phải nằm trong khoảng ${format(minTemp)} đến ${format(maxTemp)} độ.")
        }
        val accepted = try { vehicle.setFloatProperty(CoolrayProperties.HVAC_TEMP_SET, CoolrayProperties.HVAC_TEMP_AREA, requested) }
        catch (t: Throwable) { return failed(t, "Lỗi khi gửi lệnh nhiệt độ.") }
        if (!accepted) return CommandExecutionResult.Rejected("Xe không chấp nhận lệnh điều chỉnh nhiệt độ.")
        if (vehicle.isMock) return confirmed("${format(requested)} độ")

        repeat(VERIFY_ATTEMPTS) { attempt ->
            val actual = safeFloat(CoolrayProperties.HVAC_TEMP_SET, CoolrayProperties.HVAC_TEMP_AREA)
            if (actual != null && isTemperatureConfirmed(requested, actual, endpoint)) {
                return confirmed("${format(actual.coerceIn(minTemp, maxTemp))} độ")
            }
            if (!pause(attempt)) return interrupted()
        }
        return unverified("Lệnh VHAL đã được gửi nhưng chưa đọc lại được mức ${format(requested)} độ.")
    }

    private fun setAcPower(on: Boolean, target: HvacPowerTarget): CommandExecutionResult = when (target) {
        HvacPowerTarget.COMBINED -> setCombinedAcPower(on)
        HvacPowerTarget.SYSTEM -> writeAndVerifyBool(
            CoolrayProperties.HVAC_POWER,
            CoolrayProperties.HVAC_POWER_AREA,
            on,
            "Xe không chấp nhận lệnh nguồn điều hòa.",
            if (on) "nguồn điều hòa đang bật" else "nguồn điều hòa đã tắt"
        )
        HvacPowerTarget.COMPRESSOR -> writeAndVerifyBool(
            CoolrayProperties.HVAC_AC,
            CoolrayProperties.HVAC_AC_AREA,
            on,
            "Xe không chấp nhận lệnh A/C.",
            if (on) "A/C đang bật" else "A/C đã tắt"
        )
    }

    private fun setCombinedAcPower(on: Boolean): CommandExecutionResult {
        val accepted = try {
            if (on) {
                vehicle.setBoolProperty(CoolrayProperties.HVAC_POWER, CoolrayProperties.HVAC_POWER_AREA, true) &&
                    vehicle.setBoolProperty(CoolrayProperties.HVAC_AC, CoolrayProperties.HVAC_AC_AREA, true)
            } else {
                vehicle.setBoolProperty(CoolrayProperties.HVAC_AC, CoolrayProperties.HVAC_AC_AREA, false) &&
                    vehicle.setBoolProperty(CoolrayProperties.HVAC_POWER, CoolrayProperties.HVAC_POWER_AREA, false)
            }
        } catch (t: Throwable) { return failed(t, "Lỗi khi gửi lệnh điều hòa.") }
        if (!accepted) return CommandExecutionResult.Rejected("Xe không chấp nhận lệnh điều hòa.")
        if (vehicle.isMock) return confirmed(if (on) "đang bật" else "đã tắt")

        var readable = false
        repeat(VERIFY_ATTEMPTS) { attempt ->
            val power = safeBool(CoolrayProperties.HVAC_POWER, CoolrayProperties.HVAC_POWER_AREA)
            val ac = safeBool(CoolrayProperties.HVAC_AC, CoolrayProperties.HVAC_AC_AREA)
            if (power != null || ac != null) readable = true
            if (power == on && ac == on) return confirmed(if (on) "đang bật" else "đã tắt")
            if (!pause(attempt)) return interrupted()
        }
        return unverified(if (readable) "Trạng thái đọc lại chưa khớp lệnh." else "Không đọc lại được trạng thái điều hòa.")
    }

    private fun adjustFanSpeed(delta: Int): CommandExecutionResult {
        val current = try { vehicle.getIntPropertyOrNull(CoolrayProperties.HVAC_FAN_SPEED, CoolrayProperties.HVAC_FAN_AREA) } catch (_: Throwable) { null }
            ?: return CommandExecutionResult.Rejected("Không đọc được mức quạt hiện tại.")
        return setFanSpeed((current + delta).coerceIn(CoolrayProperties.HVAC_MIN_FAN, CoolrayProperties.HVAC_MAX_FAN))
    }

    private fun setFanSpeed(requested: Int): CommandExecutionResult {
        var level = requested
        // Coolray 2025 fan range is 1..7 (VHAL config); "tắt quạt" (level 0) turns the climate system off.
        if (level == 0) return setAcPower(false, HvacPowerTarget.SYSTEM)
        // The shared parser maps "mạnh nhất / tối đa" to 8; this car's top level is 7.
        level = if (level in (CoolrayProperties.HVAC_MAX_FAN + 1)..PARSER_MAX_FAN) CoolrayProperties.HVAC_MAX_FAN else level
        if (level !in CoolrayProperties.HVAC_MIN_FAN..CoolrayProperties.HVAC_MAX_FAN) {
            return CommandExecutionResult.Rejected("Mức quạt phải từ ${CoolrayProperties.HVAC_MIN_FAN} đến ${CoolrayProperties.HVAC_MAX_FAN}.")
        }
        return writeAndVerifyInt(CoolrayProperties.HVAC_FAN_SPEED, CoolrayProperties.HVAC_FAN_AREA, level, "Xe không chấp nhận mức quạt $level.") { "mức $it" }
    }

    // Coolray 2025 recirculation: HVAC_RECIRC_ON 354419976 area 75 (true = gió trong).
    private fun setRecirculation(enabled: Boolean): CommandExecutionResult = writeAndVerifyBool(
        CoolrayProperties.HVAC_RECIRC,
        CoolrayProperties.HVAC_RECIRC_AREA,
        enabled,
        "Xe không chấp nhận lệnh lấy gió.",
        if (enabled) "gió trong" else "gió ngoài"
    )

    /**
     * Coolray 2025: standard HVAC_DEFROSTER (0x13200504) with window areas 1 = front windshield,
     * 2 = rear windshield (head unit config dump). Boolean write + read-back on the same area.
     */
    private fun setDefrost(zone: DefrostZone, on: Boolean): CommandExecutionResult = writeAndVerifyBool(
        CoolrayProperties.HVAC_DEFROSTER,
        when (zone) {
            DefrostZone.FRONT -> CoolrayProperties.HVAC_DEFROST_FRONT_AREA
            DefrostZone.REAR -> CoolrayProperties.HVAC_DEFROST_REAR_AREA
        },
        on,
        when (zone) {
            DefrostZone.FRONT -> "Xe không chấp nhận lệnh sấy kính trước."
            DefrostZone.REAR -> "Xe không chấp nhận lệnh sấy kính sau."
        },
        if (on) "đang bật" else "đã tắt"
    )

    // Coolray 2025: HVAC Eco has no VHAL mapping on this head unit.
    @Suppress("UNUSED_PARAMETER")
    private fun setHvacEco(on: Boolean): CommandExecutionResult = CommandExecutionResult.Rejected(NO_COOLRAY_MAPPING)

    private fun setPeriodicAirflow(on: Boolean, minutes: Int): CommandExecutionResult {
        if (!on) {
            AirflowDirectionCycler.stop()
            return confirmed("đã tắt")
        }
        val interval = (if (minutes > 0) minutes else AirflowDirectionCycler.defaultMinutes)
        if (interval !in AirflowDirectionCycler.MIN_MINUTES..AirflowDirectionCycler.MAX_MINUTES) {
            return CommandExecutionResult.Rejected("Thời gian đảo gió phải từ 1 đến 10 phút.")
        }
        if (!vehicle.isMock) {
            val power = safeBool(CoolrayProperties.HVAC_POWER, CoolrayProperties.HVAC_POWER_AREA)
            if (power == false) return CommandExecutionResult.Rejected("Điều hòa đang tắt, bật điều hòa trước rồi mới đảo gió.")
        }
        // First swing now (verified), then every interval.
        val current = safeInt(CoolrayProperties.HVAC_FAN_DIRECTION, CoolrayProperties.HVAC_FAN_DIRECTION_AREA)
        val first = writeAndVerifyInt(
            CoolrayProperties.HVAC_FAN_DIRECTION,
            CoolrayProperties.HVAC_FAN_DIRECTION_AREA,
            AirflowDirectionCycler.nextDirection(current),
            "Xe không chấp nhận lệnh hướng gió."
        ) { "mỗi $interval phút" }
        if (first is CommandExecutionResult.Rejected || first is CommandExecutionResult.Failed) return first
        AirflowDirectionCycler.start(vehicle, interval)
        return if (first is CommandExecutionResult.Confirmed) confirmed("mỗi $interval phút") else first
    }

    private fun setFanDirection(directions: Set<FanDirectionTarget>): CommandExecutionResult {
        // A direction chosen by hand ends the periodic swing.
        AirflowDirectionCycler.stop()
        if (directions.isEmpty()) return CommandExecutionResult.Rejected("Chưa xác định được hướng gió.")
        val value = directions.fold(0) { acc, direction -> acc or when (direction) {
            FanDirectionTarget.FACE -> 1
            FanDirectionTarget.FEET -> 2
            FanDirectionTarget.WINDSHIELD -> 4
        } }
        if (value !in SUPPORTED_FAN_DIRECTION_VALUES) {
            return CommandExecutionResult.Rejected(
                "Tổ hợp hướng gió này không được xe hỗ trợ."
            )
        }
        return writeAndVerifyInt(
            CoolrayProperties.HVAC_FAN_DIRECTION,
            CoolrayProperties.HVAC_FAN_DIRECTION_AREA,
            value,
            "Xe không chấp nhận lệnh hướng gió."
        ) { fanDirectionText(directions) }
    }

    private fun writeAndVerifyBool(property: Int, area: Int, value: Boolean, rejectMessage: String, actualText: String): CommandExecutionResult {
        // IHU629G (drive_assist CLIMATE-FACTS): writing the state it already has makes the HVAC
        // re-plan airflow and cycle dampers (audible). Already there → nothing to send.
        if (!vehicle.isMock && safeBool(property, area) == value) return confirmed(actualText)
        val accepted = try { vehicle.setBoolProperty(property, area, value) } catch (t: Throwable) { return failed(t, "Lỗi khi gửi lệnh HVAC.") }
        if (!accepted) return CommandExecutionResult.Rejected(rejectMessage)
        if (vehicle.isMock) return confirmed(actualText)
        var readable = false
        repeat(VERIFY_ATTEMPTS) { attempt ->
            val actual = safeBool(property, area)
            if (actual != null) readable = true
            if (actual == value) return confirmed(actualText)
            if (!pause(attempt)) return interrupted()
        }
        return unverified(if (readable) "Trạng thái đọc lại chưa khớp lệnh." else "Không đọc lại được trạng thái HVAC.")
    }

    private fun writeAndVerifyInt(property: Int, area: Int, value: Int, rejectMessage: String, actualText: (Int) -> String): CommandExecutionResult {
        if (!vehicle.isMock && safeInt(property, area) == value) return confirmed(actualText(value))
        val accepted = try { vehicle.setIntProperty(property, area, value) } catch (t: Throwable) { return failed(t, "Lỗi khi gửi lệnh HVAC.") }
        if (!accepted) return CommandExecutionResult.Rejected(rejectMessage)
        if (vehicle.isMock) return confirmed(actualText(value))
        repeat(VERIFY_ATTEMPTS) { attempt ->
            val actual = try { vehicle.getIntPropertyOrNull(property, area) } catch (_: Throwable) { null }
            if (actual == value) return confirmed(actualText(actual))
            if (!pause(attempt)) return interrupted()
        }
        return unverified("Lệnh VHAL đã được gửi nhưng chưa xác nhận được trạng thái.")
    }

    private fun safeBool(property: Int, area: Int): Boolean? = try { vehicle.getBoolPropertyOrNull(property, area) } catch (_: Throwable) { null }

    private fun safeInt(property: Int, area: Int): Int? =
        try { vehicle.getIntPropertyOrNull(property, area)?.takeIf { it >= 0 } } catch (_: Throwable) { null }
    private fun safeFloat(property: Int, area: Int): Float? = try { vehicle.getFloatProperty(property, area).takeIf { it.isFinite() } } catch (_: Throwable) { null }

    private fun isTemperatureConfirmed(requested: Float, actual: Float, endpoint: Endpoint?): Boolean {
        if (!actual.isFinite() || actual < 0f) return false
        if (abs(actual - requested) <= 0.25f) return true
        return when (endpoint) {
            Endpoint.LOW -> actual <= minTemp + 1f
            Endpoint.HIGH -> actual >= maxTemp - 2f
            null -> false
        }
    }

    private fun pause(attempt: Int): Boolean {
        if (attempt >= VERIFY_ATTEMPTS - 1) return true
        return try { waitForReadback(VERIFY_DELAY_MS); true } catch (_: InterruptedException) { Thread.currentThread().interrupt(); false }
    }

    private fun confirmed(actual: String? = null) = CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK, actual)
    private fun unverified(detail: String) = CommandExecutionResult.AcceptedUnverified(detail)
    private fun interrupted() = unverified("Quá trình xác nhận bị gián đoạn.")
    private fun failed(t: Throwable, fallback: String) = CommandExecutionResult.Failed(t.message ?: fallback, t::class.java.name)
    private fun fanDirectionText(directions: Set<FanDirectionTarget>) = directions.joinToString(" và ") { when (it) {
        FanDirectionTarget.FACE -> "mặt"
        FanDirectionTarget.FEET -> "chân"
        FanDirectionTarget.WINDSHIELD -> "kính"
    } }
    /** Spoken form: 22.5 -> "22 rưỡi" so the TTS lexicon reads it naturally. */
    private fun format(value: Float): String {
        val rounded = roundToStep(value)
        val whole = rounded.toInt()
        return if (rounded - whole >= 0.25f) "$whole rưỡi" else whole.toString()
    }
    private fun roundToStep(value: Float): Float =
        if (!value.isFinite()) value else Math.round(value / CoolrayProperties.HVAC_TEMP_STEP) * CoolrayProperties.HVAC_TEMP_STEP

    private enum class Endpoint { LOW, HIGH }
    private companion object {
        const val VERIFY_ATTEMPTS = 10
        const val PARSER_MAX_FAN = 8
        const val NO_COOLRAY_MAPPING = "Chức năng này chưa hỗ trợ trên xe này."

        const val VERIFY_DELAY_MS = 250L
        // Coolray REFERENCE accepts every face/floor/windshield bitmask combination.
        val SUPPORTED_FAN_DIRECTION_VALUES = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
