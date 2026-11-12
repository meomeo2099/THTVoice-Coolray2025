package com.voicegeely.app.car

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.CoolrayProperties as P

/** HVAC through the OEM function API. Other properties retain the Android Car backend. */
class EcarxHvacPropertyAccess(
    private val vhal: VehiclePropertyAccess,
    private val oem: EcarxFunctionPort
) : VehiclePropertyAccess by vhal {
    override fun getFloatProperty(propertyId: Int, areaId: Int): Float {
        if (propertyId != P.HVAC_TEMP_SET) return vhal.getFloatProperty(propertyId, areaId)
        val zone = temperatureZone(areaId) ?: return Float.NaN
        return oem.getFloat(F.HVAC_FUNC_TEMP, zone)?.takeIf { it in P.HVAC_MIN_TEMP..P.HVAC_MAX_TEMP } ?: Float.NaN
    }

    override fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean {
        if (propertyId != P.HVAC_TEMP_SET) return vhal.setFloatProperty(propertyId, areaId, value)
        val zone = temperatureZone(areaId) ?: return false
        return supported(F.HVAC_FUNC_TEMP, zone) && oem.setFloat(F.HVAC_FUNC_TEMP, zone, value)
    }

    override fun getIntProperty(propertyId: Int, areaId: Int): Int {
        val function = integerFunction(propertyId, areaId) ?: return vhal.getIntProperty(propertyId, areaId)
        // Lunaris Sensors observes front-row fan/direction at zone1. Facade GLOBAL0 dispatches
        // to the OEM non-zoned command overload when present, not an invented physical zone.
        return F.levelOf(function, oem.getInt(function, F.ZONE_ROW_1_LEFT))?.takeIf { it in 1..7 }
            ?: F.levelOf(function, oem.getInt(function, F.GLOBAL))?.takeIf { it in 1..7 } ?: -1
    }

    override fun getIntPropertyOrNull(propertyId: Int, areaId: Int): Int? =
        if (integerFunction(propertyId, areaId) != null) getIntProperty(propertyId, areaId).takeUnless { it == -1 }
        else vhal.getIntPropertyOrNull(propertyId, areaId)

    override fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean {
        val function = integerFunction(propertyId, areaId) ?: return vhal.setIntProperty(propertyId, areaId, value)
        return value in 1..7 && supported(function, 0) && oem.setInt(function, 0, F.level(function, value))
    }

    override fun getBoolProperty(propertyId: Int, areaId: Int): Boolean = getBoolPropertyOrNull(propertyId, areaId) ?: false

    override fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? {
        val function = booleanFunction(propertyId, areaId) ?: return vhal.getBoolPropertyOrNull(propertyId, areaId)
        val value = oem.getInt(function, 0) ?: return null
        return if (function == F.HVAC_FUNC_CIRCULATION) {
            when (value) { F.level(function, 1) -> true; F.level(function, 2) -> false; else -> null }
        } else when (value) { F.ON -> true; F.OFF -> false; else -> null }
    }

    override fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean {
        val function = booleanFunction(propertyId, areaId) ?: return vhal.setBoolProperty(propertyId, areaId, value)
        val requested = if (function == F.HVAC_FUNC_CIRCULATION) F.level(function, if (value) 1 else 2)
            else if (value) F.ON else F.OFF
        return supported(function, 0) && oem.setInt(function, 0, requested)
    }

    private fun supported(function: Int, zone: Int): Boolean = oem.isSupported(function, zone) != false

    private fun temperatureZone(areaId: Int): Int? = when (areaId) {
        P.HVAC_TEMP_AREA -> F.ZONE_ROW_1_LEFT
        P.HVAC_TEMP_AREA_PASSENGER -> F.ZONE_ROW_1_RIGHT
        else -> null
    }

    private fun integerFunction(propertyId: Int, areaId: Int): Int? {
        if (areaId != P.HVAC_AREA) return null
        return when (propertyId) {
            P.HVAC_FAN_SPEED -> F.HVAC_FUNC_FAN_SPEED
            P.HVAC_FAN_DIRECTION -> F.HVAC_FUNC_BLOWING_MODE
            else -> null
        }
    }

    private fun booleanFunction(propertyId: Int, areaId: Int): Int? {
        if (propertyId == P.HVAC_DEFROSTER) return when (areaId) {
            P.HVAC_DEFROST_FRONT_AREA -> F.HVAC_FUNC_DEFROST_FRONT
            P.HVAC_DEFROST_REAR_AREA -> F.HVAC_FUNC_DEFROST_REAR
            else -> null
        }
        if (areaId != P.HVAC_AREA) return null
        return when (propertyId) {
            P.HVAC_POWER -> F.HVAC_FUNC_POWER
            P.HVAC_AC -> F.HVAC_FUNC_AC
            P.HVAC_AUTO -> F.HVAC_FUNC_AUTO
            P.HVAC_DUAL -> F.HVAC_FUNC_TEMP_DUAL
            P.HVAC_MAX_AC -> F.HVAC_FUNC_AC_MAX
            P.HVAC_MAX_DEFROST -> F.HVAC_FUNC_DEFROST_FRONT_MAX
            P.HVAC_RECIRC -> F.HVAC_FUNC_CIRCULATION
            else -> null
        }
    }
}
