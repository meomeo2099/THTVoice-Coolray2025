package com.voicegeely.app.car

/**
 * ECARX AdaptAPI (`com.ecarx.xui.adaptapi`, version 1.1.18.9) function IDs, zones and values used
 * by the Coolray 2025 build.
 *
 * Source: `AdapterAPI.jar` from the head unit's own /system/framework (boot classpath), interfaces
 * `car.hvac.IHvac`, `car.vehicle.IBcm`, `vehicle.VehicleZone`, `vehicle.VehicleWindow`. The full
 * catalogue is in docs/COOLRAY2025_VOICE_SPEC.md (appendix A).
 *
 * These are AdaptAPI *function* IDs used through `ICarFunction` ([EcarxFunctionPort]); they are not
 * VHAL property IDs and are never written through CarPropertyManager.
 *
 * Value convention of AdaptAPI: switches use [ON] / [OFF]; enumerated values are `FUNCTION_ID + n`
 * (see [level]).
 *
 * Status: REFERENCE. Whether a function is active on this car is checked at runtime with
 * `isFunctionSupported` before any write.
 */
object Coolray2025Functions {
    const val OFF = 0
    const val ON = 1

    /** Enumerated value `functionId + n` (AdaptAPI convention), e.g. seat heating level 2. */
    fun level(functionId: Int, n: Int): Int = if (n <= 0) OFF else functionId + n

    /** Inverse of [level]: 0 → 0, `functionId + n` → n, anything else → null. */
    fun levelOf(functionId: Int, value: Int?): Int? = when {
        value == null -> null
        value == OFF -> 0
        value in (functionId + 1)..(functionId + 0xFF) -> value - functionId
        value in 1..15 -> value // some firmware returns the plain level
        else -> null
    }

    // ---- Zones (vehicle.VehicleZone / VehicleWindow) ----
    const val ZONE_ALL = Int.MIN_VALUE
    const val ZONE_ROW_1_LEFT = 1
    const val ZONE_ROW_1_RIGHT = 4

    const val WINDOW_ROW_1_LEFT = 16
    const val WINDOW_ROW_1_RIGHT = 32
    const val WINDOW_ROW_2_LEFT = 256
    const val WINDOW_ROW_2_RIGHT = 512
    const val WINDOW_ROOF_TOP = 4

    // ---- car.vehicle.IBcm ----
    /** Window move: ON open / OFF close, or `+1` pause, `+2` half. */
    const val BCM_FUNC_WINDOW = 553844992
    const val WINDOW_PAUSE = BCM_FUNC_WINDOW + 1
    /** Window target position, customize (float) value 0..100 %. */
    const val BCM_FUNC_WINDOW_POS = 553845504
    /** Window current position, customize (float) value 0..100 % (read-only). */
    const val BCM_FUNC_WINDOW_CURRENT_POS = 553846272
    const val BCM_FUNC_SUNROOF_TILT = 553845760

    const val BCM_FUNC_LIGHT_DIPPED_BEAM = 553976064
    const val BCM_FUNC_LIGHT_FRONT_FOG_LAMPS = 553976832
    const val BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS = 553977856
    const val BCM_FUNC_LIGHT_READING_LIGHT = 553980672

    // ---- Vendor MCU properties (0x287xxxxx), present in the head unit's car_service dump ----
    /**
     * Window group command: 8 bytes, index 0 FL, 1 FR, 2 RL, 3 RR, 4 sunroof glass, 6 sunshade;
     * value 0..100 % open, -1 (0xFF) = leave unchanged. Sent with CarImpl.sendBytesToMcuByPropID.
     */
    const val INFO_ID_WINDOW_GROUP = 678428834 // 0x287000A2
    const val WINDOW_GROUP_SIZE = 8
    const val GROUP_FL = 0
    const val GROUP_FR = 1
    const val GROUP_RL = 2
    const val GROUP_RR = 3
    const val GROUP_SUNROOF = 4
    const val GROUP_SUNSHADE = 6
    /** Per-window position state (0..100 %), read with CarImpl.getIntByPropID(prop, BCM_FUNC_WINDOW_POS). */
    const val INFO_ID_WINDOW_SUNSHADE = 678428828 // 0x2870009C INFO_ID_WINDOW_ROLLSHADE
    const val INFO_ID_WINDOW_SUNROOF = 678428827 // 0x2870009B
    const val INFO_ID_WINDOW_FL = 678428829 // 0x2870009D
    const val INFO_ID_WINDOW_FR = 678428830
    const val INFO_ID_WINDOW_RL = 678428831
    const val INFO_ID_WINDOW_RR = 678428832
    /** 12 V battery voltage: bytes [volts, tenths]. */
    const val INFO_ID_BATTERY_VOLTAGE = 678428900 // INFO_ID_VPOWERINFO_BATTERY_VOL
    /** Fuel remaining in litres: 1 byte. */
    const val INFO_ID_REMAINING_FUEL = 678429191 // INFO_ID_REMAINING_OIL

    fun windowStateProp(groupIndex: Int): Int? = when (groupIndex) {
        GROUP_FL -> INFO_ID_WINDOW_FL
        GROUP_FR -> INFO_ID_WINDOW_FR
        GROUP_RL -> INFO_ID_WINDOW_RL
        GROUP_RR -> INFO_ID_WINDOW_RR
        GROUP_SUNROOF -> INFO_ID_WINDOW_SUNROOF
        GROUP_SUNSHADE -> INFO_ID_WINDOW_SUNSHADE
        else -> null
    }

    /** Window group payload moving only [groupIndex] to [percent]. */
    fun windowGroupPayload(groupIndex: Int, percent: Int): ByteArray =
        ByteArray(WINDOW_GROUP_SIZE) { -1 }.also { it[groupIndex] = percent.coerceIn(0, 100).toByte() }

    // ---- car.hvac.IHvac ----
    /** Seat ventilation, zone ROW_1_LEFT / ROW_1_RIGHT, value OFF or level(…, 1..3). */
    const val HVAC_FUNC_SEAT_VENTILATION = 268763392
    /** Seat heating, zone ROW_1_LEFT / ROW_1_RIGHT, value OFF or level(…, 1..3). */
    const val HVAC_FUNC_SEAT_HEATING = 268763648
    const val SEAT_LEVEL_MIN = 1
    const val SEAT_LEVEL_MAX = 3
}
