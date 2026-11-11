package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehiclePropertyAccess
import kotlin.math.roundToInt

/**
 * Read-only status queries for Coolray 2025. Only standard VHAL properties that this head unit
 * declares (docs/COOLRAY2025_PROPERTIES.md) are read; window positions for "tình trạng xe" come
 * from the ECARX AdaptAPI when available. Queries without a mapping are rejected without a read.
 * See CoolrayCapabilities.COOLRAY_STATUS_QUERIES. REFERENCE.
 */
class VehicleStatusQueryExecutor(
    private val vehicle: VehiclePropertyAccess,
    private val ecarx: EcarxFunctionPort? = null
) : VoiceCommandExecutor {
    override suspend fun execute(command: VoiceCommand): CommandExecutionResult {
        if (command !is VoiceCommand.QueryVehicleStatus) {
            return CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm đọc trạng thái xe.")
        }
        return try {
            when (command.query) {
                VehicleStatusQuery.SPEED -> readSpeed()
                VehicleStatusQuery.GEAR -> readGear()
                VehicleStatusQuery.CLIMATE_TEMPERATURE -> readClimateTemperature()
                VehicleStatusQuery.FAN_SPEED -> readFanSpeed()
                VehicleStatusQuery.FUEL_LEVEL -> readFuelLevel()
                VehicleStatusQuery.ODOMETER -> readOdometer()
                VehicleStatusQuery.OUTSIDE_TEMPERATURE -> readOutsideTemperature()
                VehicleStatusQuery.BATTERY_VOLTAGE -> readBatteryVoltage()
                VehicleStatusQuery.RANGE -> readRange()
                VehicleStatusQuery.DRIVE_MODE -> readDriveMode()
                VehicleStatusQuery.CAR_SUMMARY -> readCarSummary()
                else -> CommandExecutionResult.Rejected("Xe này chưa hỗ trợ đọc thông tin đó.")
            }
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(
                reason = t.message ?: "Không đọc được trạng thái xe.",
                throwableClass = t::class.java.name
            )
        }
    }

    private fun readSpeed(): CommandExecutionResult {
        val kmh = vehicle.getFloatProperty(CoolrayProperties.VEHICLE_SPEED, CoolrayProperties.VEHICLE_SPEED_AREA)
        if (!kmh.isFinite() || kmh < 0f) return unavailable("tốc độ xe")
        return confirmed("Tốc độ hiện tại là ${kmh.roundToInt()} ki lô mét một giờ")
    }

    private fun readGear(): CommandExecutionResult {
        val raw = vehicle.getIntPropertyOrNull(CoolrayProperties.GEAR, CoolrayProperties.GEAR_AREA)
            ?: return unavailable("vị trí số")
        val label = gearLabel(raw)
            ?: return CommandExecutionResult.Rejected("Giá trị số hiện tại chưa được ánh xạ: $raw.")
        return confirmed("Xe đang ở số $label")
    }

    private fun readClimateTemperature(): CommandExecutionResult {
        val value = vehicle.getFloatProperty(CoolrayProperties.HVAC_TEMP_SET, CoolrayProperties.HVAC_TEMP_AREA)
        if (!value.isFinite() || value <= 0f) return unavailable("nhiệt độ điều hòa")
        return confirmed("Nhiệt độ điều hòa hiện tại là ${formatFloat(value)} độ")
    }

    private fun readFanSpeed(): CommandExecutionResult {
        val value = vehicle.getIntPropertyOrNull(CoolrayProperties.HVAC_FAN_SPEED, CoolrayProperties.HVAC_FAN_AREA)
            ?: return unavailable("mức quạt")
        if (value !in CoolrayProperties.HVAC_MIN_FAN..CoolrayProperties.HVAC_MAX_FAN) {
            return CommandExecutionResult.Rejected(
                "Mức quạt đọc được nằm ngoài dải ${CoolrayProperties.HVAC_MIN_FAN} đến ${CoolrayProperties.HVAC_MAX_FAN}."
            )
        }
        return confirmed("Quạt đang ở mức $value")
    }

    /** FUEL_LEVEL (ml) / INFO_FUEL_CAPACITY (ml) → percent; FUEL_LEVEL_LOW adds a warning. */
    private fun fuelPercent(): Int? {
        val level = number(CoolrayProperties.FUEL_LEVEL)
        val capacity = number(CoolrayProperties.FUEL_CAPACITY)?.takeIf { it > 0f }
        if (level != null && capacity != null) {
            (level / capacity * 100f).roundToInt().takeIf { it in 0..100 }?.let { return it }
        }
        return mcuFuelPercent()
    }

    /** The byte carries percent, as decoded by Lunaris Sensors; do not announce it as litres. */
    private fun mcuFuelPercent(): Int? = (try { ecarx?.getBytesByProp(F.INFO_ID_REMAINING_FUEL) } catch (_: Throwable) { null })
        ?.takeIf { it.size == 1 }?.let { it[0].toInt() and 0xFF }?.takeIf { it in 0..100 }

    private fun fuelLitres(): Float? {
        val levelMl = number(CoolrayProperties.FUEL_LEVEL)?.takeIf { it in 0f..120_000f }
        if (levelMl != null) return levelMl / 1000f
        val percent = mcuFuelPercent()
        val capacityMl = number(CoolrayProperties.FUEL_CAPACITY)?.takeIf { it in 10_000f..120_000f }
        if (percent != null && capacityMl != null) return percent * capacityMl / 100_000f
        return null
    }

    /** 12 V battery: vendor MCU property INFO_ID_VPOWERINFO_BATTERY_VOL, bytes [volts, tenths]. */
    private fun readBatteryVoltage(): CommandExecutionResult {
        val bytes = (try { ecarx?.getBytesByProp(F.INFO_ID_BATTERY_VOLTAGE) } catch (_: Throwable) { null })
            ?.takeIf { it.size >= 2 } ?: return unavailable("điện áp ắc quy")
        val volts = (bytes[0].toInt() and 0xFF) + (bytes[1].toInt() and 0xFF) / 10f
        if (volts !in 6f..18f) return unavailable("điện áp ắc quy")
        val tenths = (volts * 10f).roundToInt()
        val spoken = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10} phẩy ${tenths % 10}"
        return confirmed("Điện áp ắc quy là $spoken vôn")
    }

    private fun readFuelLevel(): CommandExecutionResult {
        val litres = fuelLitres()?.let(::formatFloat)
        val percent = fuelPercent()
        val low = try {
            vehicle.getBoolPropertyOrNull(CoolrayProperties.FUEL_LEVEL_LOW, CoolrayProperties.GLOBAL_AREA)
        } catch (_: Throwable) { null }
        return when {
            percent != null && litres != null && low == true -> confirmed("Bình xăng còn $percent phần trăm, khoảng $litres lít, xăng sắp hết")
            percent != null && litres != null -> confirmed("Bình xăng còn $percent phần trăm, khoảng $litres lít")
            percent != null && low == true -> confirmed("Bình xăng còn $percent phần trăm, xăng sắp hết")
            percent != null -> confirmed("Bình xăng còn $percent phần trăm")
            litres != null -> confirmed("Bình xăng còn khoảng $litres lít")
            low == true -> confirmed("Xăng sắp hết")
            else -> unavailable("mức xăng")
        }
    }

    private fun readOdometer(): CommandExecutionResult {
        val km = number(CoolrayProperties.ODOMETER) ?: sensor(F.SENSOR_TYPE_ODOMETER, 0f..10_000_000f) ?: return unavailable("ODO")
        return confirmed("Tổng quãng đường xe đã đi là ${km.roundToInt()} ki lô mét")
    }

    private fun readRange(): CommandExecutionResult {
        val km = sensor(F.SENSOR_TYPE_FUEL_REMAIN_RANGE, 0f..3000f) ?: return unavailable("quãng đường còn lại")
        return confirmed("Quãng đường còn lại theo xe là ${km.roundToInt()} ki lô mét")
    }

    private fun readDriveMode(): CommandExecutionResult {
        val value = try { ecarx?.getInt(F.DM_FUNC_DRIVE_MODE_SELECT, F.GLOBAL) } catch (_: Throwable) { null }
        val mode = when (F.levelOf(F.DM_FUNC_DRIVE_MODE_SELECT, value)) {
            1 -> "tiết kiệm"; 2 -> "thoải mái"; 3 -> "thể thao"; else -> return unavailable("chế độ lái")
        }
        return confirmed("Xe đang ở chế độ lái $mode")
    }

    private fun sensor(id: Int, range: ClosedFloatingPointRange<Float>): Float? =
        (try { ecarx?.getSensorLatestValue(id) } catch (_: Throwable) { null })?.takeIf { it.isFinite() && it in range }

    private fun readOutsideTemperature(): CommandExecutionResult {
        val c = try {
            vehicle.getFloatProperty(CoolrayProperties.OUTSIDE_TEMPERATURE, CoolrayProperties.GLOBAL_AREA)
        } catch (_: Throwable) { Float.NaN }
        if (!c.isFinite() || c !in -40f..70f) return unavailable("nhiệt độ ngoài trời")
        return confirmed("Nhiệt độ ngoài trời là ${c.roundToInt()} độ")
    }

    /** "Tình trạng xe": fuel and open windows. A part the car does not answer is left out, never guessed. */
    private fun readCarSummary(): CommandExecutionResult {
        val parts = ArrayList<String>()
        fuelPercent()?.let { parts += "Xăng còn $it phần trăm" }
        val port = ecarx
        if (port != null) {
            val positions = WindowVoiceCommandExecutor.ALL_ZONES.associateWith { zone ->
                WindowVoiceCommandExecutor.readPosition(port, zone)?.toFloat()
            }.filterValues { it != null }
            if (positions.isNotEmpty()) {
                val open = positions.filterValues { (it ?: 0f) > WINDOW_OPEN_MIN }.keys.map { WindowVoiceCommandExecutor.zoneName(it) }
                if (open.isNotEmpty()) parts += "Đang mở ${open.joinToString(" và ")}"
                else if (positions.size == WindowVoiceCommandExecutor.ALL_ZONES.size) parts += "Kính đóng hết"
                if (positions.size < WindowVoiceCommandExecutor.ALL_ZONES.size) parts += "Chưa đọc đủ trạng thái các kính"
            }
        }
        if (parts.isEmpty()) return unavailable("tình trạng xe")
        return confirmed(parts.joinToString(". "))
    }

    /** Float read; falls back to an int read in case a property carries an INT32 type. */
    private fun number(property: Int): Float? {
        val f = try { vehicle.getFloatProperty(property, CoolrayProperties.GLOBAL_AREA) } catch (_: Throwable) { Float.NaN }
        if (f.isFinite() && f >= 0f) return f
        return (try { vehicle.getIntPropertyOrNull(property, CoolrayProperties.GLOBAL_AREA) } catch (_: Throwable) { null })
            ?.takeIf { it >= 0 }?.toFloat()
    }

    private fun confirmed(spokenText: String) = CommandExecutionResult.Confirmed(
        verificationSource = VerificationSource.VEHICLE_READ_BACK,
        actualValue = spokenText
    )

    private fun unavailable(name: String) = CommandExecutionResult.Rejected("Không đọc được $name từ xe.")

    private fun gearLabel(raw: Int): String? = when (raw) {
        1 -> "N"
        2 -> "R"
        4 -> "P"
        8 -> "D"
        16 -> "1"
        32 -> "2"
        64 -> "3"
        128 -> "4"
        256 -> "5"
        512 -> "6"
        1024 -> "7"
        2048 -> "8"
        4096 -> "9"
        else -> null
    }

    private fun formatFloat(value: Float): String = when {
        value % 1f == 0f -> value.toInt().toString()
        kotlin.math.abs(value % 1f - 0.5f) < 0.05f -> "${value.toInt()} rưỡi"
        else -> "%.1f".format(value)
    }

    private companion object {
        /** Window readings above this percent count as open (small values are sensor slack). */
        const val WINDOW_OPEN_MIN = 2f
    }
}
