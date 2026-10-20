package com.voicegeely.app.voice

/**
 * Which typed vehicle command families the Coolray 2025 build (head unit SX11-LA / IHU730P) routes
 * to the car.
 *
 * HVAC, steering-wheel heat and status use standard VHAL properties from the head unit's own config
 * dump (car/CoolrayProperties.kt). Windows, sunroof glass, seat heating/ventilation and lights use
 * the ECARX AdaptAPI function IDs from the head unit's AdapterAPI.jar (car/Coolray2025Functions.kt).
 * Families with no mapping on this head unit (EV drive/regen, charging, front trunk, door lock,
 * ambient/convenience settings, tailgate…) are NOT routed. Table: docs/COOLRAY2025_PROPERTIES.md.
 */
object CoolrayCapabilities {

    enum class Family {
        HVAC_CORE,
        HVAC_VENDOR_EXTRAS,
        WINDOWS,
        SUNROOF,
        LIGHTS,
        SEAT_CLIMATE,
        STEERING_HEAT,
        TAILGATE,
        STATUS_QUERY,
        STATUS_QUERY_NO_MAPPING,
        HVAC_NO_MAPPING,
        DRIVE_REGEN,
        BRAKE_SETTINGS,
        CHARGING,
        FRONT_TRUNK,
        DOOR_LOCK,
        AMBIENT_LIGHT,
        CONVENIENCE_SETTINGS,
        PARKING_SETTINGS,
        HEADLIGHT_HEIGHT,
        INTELLIGENT_HIGH_BEAM
    }

    private val ENABLED = setOf(
        Family.HVAC_CORE,
        Family.WINDOWS,
        Family.SUNROOF,
        Family.LIGHTS,
        Family.SEAT_CLIMATE,
        Family.STEERING_HEAT,
        Family.STATUS_QUERY
    )

    /**
     * Status queries with a Coolray 2025 mapping (standard VHAL properties declared by the head
     * unit: speed, current gear, HVAC temperature/fan, fuel level/capacity, odometer, outside
     * temperature; windows through the ECARX AdaptAPI for the summary). Range, trip, coolant,
     * 12 V battery, tyre pressure and service have no property on this head unit.
     */
    val COOLRAY_STATUS_QUERIES: Set<VehicleStatusQuery> = setOf(
        VehicleStatusQuery.SPEED,
        VehicleStatusQuery.GEAR,
        VehicleStatusQuery.CLIMATE_TEMPERATURE,
        VehicleStatusQuery.FAN_SPEED,
        VehicleStatusQuery.FUEL_LEVEL,
        VehicleStatusQuery.ODOMETER,
        VehicleStatusQuery.OUTSIDE_TEMPERATURE,
        VehicleStatusQuery.CAR_SUMMARY
    )

    fun isEnabled(family: Family): Boolean = family in ENABLED

    fun familyOf(command: VoiceCommand): Family? = when (command) {
        is VoiceCommand.SetTemperature,
        is VoiceCommand.AdjustTemperature,
        VoiceCommand.TemperatureHigh,
        VoiceCommand.TemperatureLow,
        is VoiceCommand.AcPower,
        is VoiceCommand.SetFanSpeed,
        is VoiceCommand.AdjustFanSpeed,
        is VoiceCommand.FanDirection -> Family.HVAC_CORE
        is VoiceCommand.Defrost -> Family.HVAC_CORE
        is VoiceCommand.Recirculation,
        is VoiceCommand.MaxAc -> Family.HVAC_CORE
        is VoiceCommand.HvacEco -> Family.HVAC_VENDOR_EXTRAS
        // Coolray has no car-side airflow cycle: every "đảo gió" command is THT Voice's periodic
        // face <-> feet swing on the Galaxy fan-direction property.
        is VoiceCommand.HvacAirflowAutoCycle -> Family.HVAC_CORE
        is VoiceCommand.Window -> Family.WINDOWS
        is VoiceCommand.Sunroof -> Family.SUNROOF
        is VoiceCommand.CabinLight,
        is VoiceCommand.ExteriorLight,
        is VoiceCommand.FogLight -> Family.LIGHTS
        is VoiceCommand.SeatClimate -> Family.SEAT_CLIMATE
        is VoiceCommand.SteeringWheelHeat -> Family.STEERING_HEAT
        VoiceCommand.UnlockRearTrunk -> Family.TAILGATE
        is VoiceCommand.QueryVehicleStatus ->
            if (command.query in COOLRAY_STATUS_QUERIES) Family.STATUS_QUERY else Family.STATUS_QUERY_NO_MAPPING
        is VoiceCommand.SetDriveMode,
        is VoiceCommand.SetRegenLevel -> Family.DRIVE_REGEN
        is VoiceCommand.SetBrakePedalFeel,
        is VoiceCommand.ComfortableStop -> Family.BRAKE_SETTINGS
        is VoiceCommand.SetChargeCurrentLimit -> Family.CHARGING
        VoiceCommand.OpenFrontTrunk -> Family.FRONT_TRUNK
        is VoiceCommand.SetDoorLock -> Family.DOOR_LOCK
        is VoiceCommand.AmbientLightPower,
        is VoiceCommand.SetAmbientBrightness,
        is VoiceCommand.SetAmbientColor -> Family.AMBIENT_LIGHT
        is VoiceCommand.InteriorLightOnDoorOpen,
        is VoiceCommand.SetFollowMeHome,
        is VoiceCommand.WirelessChargerPower,
        is VoiceCommand.SetDisplayTheme -> Family.CONVENIENCE_SETTINGS
        is VoiceCommand.ParkingWiperAutoReduction,
        is VoiceCommand.SetParkingModeDuration -> Family.PARKING_SETTINGS
        is VoiceCommand.SetHeadlightHeight -> Family.HEADLIGHT_HEIGHT
        is VoiceCommand.SetIntelligentHighBeam -> Family.INTELLIGENT_HIGH_BEAM
        else -> null
    }

    fun unsupportedReason(family: Family): String = when (family) {
        Family.DRIVE_REGEN -> "Xe này không hỗ trợ điều chỉnh chế độ lái hoặc phanh tái sinh bằng giọng nói."
        Family.CHARGING -> "Xe này không có chức năng sạc điện."
        Family.FRONT_TRUNK -> "Xe này không có cốp trước."
        Family.STATUS_QUERY_NO_MAPPING -> "Xe này chưa hỗ trợ đọc thông tin đó."
        else -> "Chức năng này chưa hỗ trợ trên xe này."
    }
}
