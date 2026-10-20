package com.voicegeely.app.car

/**
 * VHAL property/area constants for the Coolray 2025 head unit (ECARX `SX11-LA` / `IHU730P`,
 * Android 9, platform `ecarxp`).
 *
 * Source: the head unit's own `dumpsys car_service` (property config list with access, area IDs
 * and min/max), saved in docs/COOLRAY2025_PROPERTIES.md. Only properties that this head unit
 * declares are listed here; anything the dump does not contain is not used.
 *
 * Families the head unit does not expose as standard VHAL properties (door windows, sunroof,
 * seat heating/ventilation, lights) go through the ECARX AdaptAPI instead, see
 * [Coolray2025Functions] and [EcarxFunctionPort].
 *
 * Status of every value: REFERENCE (from the head unit's config dump, not yet confirmed by a
 * voice write + read-back on the car).
 *
 * A constant being present here does NOT make it voice-writable: safety, privilege and read-back
 * gates are enforced by the command layer.
 */
object CoolrayProperties {
    // ---------------------------------------------------------------------
    // Driving telemetry (read-only, safety gates)
    // ---------------------------------------------------------------------
    const val VEHICLE_SPEED = 291504647 // 0x11600207 PERF_VEHICLE_SPEED, global
    const val VEHICLE_SPEED_AREA = 0

    const val GEAR = 289408001 // 0x11400401 CURRENT_GEAR, global
    const val GEAR_AREA = 0
    const val GEAR_SELECTION = 289408000 // 0x11400400 GEAR_SELECTION, global
    const val GEAR_N = 1
    const val GEAR_R = 2
    const val GEAR_P = 4
    const val GEAR_D = 8

    const val PARKING_BRAKE = 287310850 // 0x11200402 PARKING_BRAKE_ON
    const val NIGHT_MODE = 287310855 // 0x11200407 NIGHT_MODE

    // ---------------------------------------------------------------------
    // HVAC — every HVAC property on this head unit uses area 75, temperature uses 31 / 44.
    // ---------------------------------------------------------------------
    const val HVAC_AREA = 75

    const val HVAC_POWER = 354419984 // 0x15200510 HVAC_POWER_ON
    const val HVAC_POWER_AREA = HVAC_AREA
    const val HVAC_AC = 354419973 // 0x15200505 HVAC_AC_ON
    const val HVAC_AC_AREA = HVAC_AREA
    const val HVAC_AUTO = 354419978 // 0x1520050A HVAC_AUTO_ON
    const val HVAC_AUTO_AREA = HVAC_AREA
    const val HVAC_DUAL = 354419977 // 0x15200509 HVAC_DUAL_ON
    const val HVAC_DUAL_AREA = HVAC_AREA
    const val HVAC_RECIRC = 354419976 // 0x15200508 HVAC_RECIRC_ON (true = gió trong)
    const val HVAC_RECIRC_AREA = HVAC_AREA
    const val HVAC_AUTO_RECIRC = 354419986 // 0x15200512 HVAC_AUTO_RECIRC_ON
    const val HVAC_MAX_AC = 354419974 // 0x15200506 HVAC_MAX_AC_ON
    const val HVAC_MAX_AC_AREA = HVAC_AREA
    const val HVAC_MAX_DEFROST = 354419975 // 0x15200507 HVAC_MAX_DEFROST_ON
    const val HVAC_MAX_DEFROST_AREA = HVAC_AREA

    /** HVAC_TEMPERATURE_SET 0x15600503, float °C. Areas from the dump: 31 (driver side), 44 (passenger side). */
    const val HVAC_TEMP_SET = 358614275
    const val HVAC_TEMP_AREA = 31
    const val HVAC_TEMP_AREA_PASSENGER = 44
    const val HVAC_MIN_TEMP = 16.0f
    const val HVAC_MAX_TEMP = 32.0f
    const val HVAC_TEMP_STEP = 0.5f

    /** HVAC_FAN_SPEED 0x15400500, area 75, int 1..7 (dump min/max). */
    const val HVAC_FAN_SPEED = 356517120
    const val HVAC_FAN_AREA = HVAC_AREA
    const val HVAC_MIN_FAN = 1
    const val HVAC_MAX_FAN = 7

    /** HVAC_FAN_DIRECTION 0x15400501, area 75, AOSP bitmask 1 face / 2 floor / 4 defrost. */
    const val HVAC_FAN_DIRECTION = 356517121
    const val HVAC_FAN_DIRECTION_AREA = HVAC_AREA
    const val HVAC_DIRECTION_FACE = 1
    const val HVAC_DIRECTION_FLOOR = 2
    const val HVAC_DIRECTION_FACE_FLOOR = 3
    const val HVAC_DIRECTION_DEFROST = 4
    const val HVAC_DIRECTION_DEFROST_FLOOR = 6

    /** HVAC_DEFROSTER 0x13200504, window areas: 1 = front windshield, 2 = rear windshield. */
    const val HVAC_DEFROSTER = 320865540
    const val HVAC_DEFROST_FRONT_AREA = 1
    const val HVAC_DEFROST_REAR_AREA = 2

    /** HVAC_STEERING_WHEEL_HEAT 0x1140050D, area 0, int -2..2 (dump). Positive = heating level, 0 = off. */
    const val STEERING_WHEEL_HEAT = 289408269
    const val STEERING_WHEEL_HEAT_AREA = 0
    const val STEERING_WHEEL_HEAT_ON = 2
    const val STEERING_WHEEL_HEAT_OFF = 0
    const val STEERING_WHEEL_HEAT_MAX = 2

    // ---------------------------------------------------------------------
    // Read-only vehicle information (standard AOSP properties declared by this head unit)
    // ---------------------------------------------------------------------
    const val FUEL_LEVEL = 291504903 // 0x11600307 FUEL_LEVEL, float millilitres
    const val FUEL_CAPACITY = 291504388 // 0x11600104 INFO_FUEL_CAPACITY, float millilitres
    const val FUEL_LEVEL_LOW = 287310853 // 0x11200405 FUEL_LEVEL_LOW, bool
    const val FUEL_DOOR = 287310600 // 0x11200308 FUEL_DOOR_OPEN, bool (read-only)
    const val ODOMETER = 291504644 // 0x11600204 PERF_ODOMETER, float km
    const val OUTSIDE_TEMPERATURE = 291505923 // 0x11600703 ENV_OUTSIDE_TEMPERATURE, float °C
    const val ENGINE_RPM = 291504901 // 0x11600305 ENGINE_RPM
    const val GLOBAL_AREA = 0

    /** DISPLAY_BRIGHTNESS 0x11400A03, area 0, int 0..100. Catalogued only, not voice-routed. */
    const val DISPLAY_BRIGHTNESS = 289409539
    const val DISPLAY_BRIGHTNESS_AREA = 0
}
