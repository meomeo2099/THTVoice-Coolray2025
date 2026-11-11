package com.voicegeely.app.voice

data class LegacyPredictionPayload(
    val originalText: String,
    val normalizedText: String,
    val intent: String,
    val entities: Map<String, Any>
)

/** Compatibility bridge while old handler families are migrated one by one. */
object LegacyPredictionBridge {
    fun toPayload(parsed: ParsedVoiceCommand): LegacyPredictionPayload {
        val entities = mutableMapOf<String, Any>()
        val intent = when (val command = parsed.command) {
            is VoiceCommand.SetTemperature -> { entities["action"] = "set"; entities["value"] = command.celsius.toInt(); entities["unit"] = "degree"; "change_ac_temp" }
            is VoiceCommand.AdjustTemperature -> { entities["action"] = if (command.deltaCelsius >= 0) "increase" else "decrease"; entities["value"] = kotlin.math.abs(command.deltaCelsius).toInt(); entities["unit"] = "degree"; "change_ac_temp" }
            VoiceCommand.TemperatureHigh -> { entities["action"] = "set"; entities["value"] = 100; entities["unit"] = "percent"; "change_ac_temp" }
            VoiceCommand.TemperatureLow -> { entities["action"] = "set"; entities["value"] = 1; entities["unit"] = "percent"; "change_ac_temp" }
            is VoiceCommand.AcPower -> { entities["action"] = if (command.on) "set" else "unset"; "ac_control" }
            is VoiceCommand.SetFanSpeed -> { entities["action"] = "set"; entities["value"] = command.level; entities["unit"] = "number"; "change_fan_speed" }
            is VoiceCommand.AdjustFanSpeed -> { entities["action"] = if (command.delta >= 0) "increase" else "decrease"; entities["value"] = kotlin.math.abs(command.delta); entities["unit"] = "number"; "change_fan_speed" }
            is VoiceCommand.Recirculation, is VoiceCommand.Defrost -> "hvac_air"
            is VoiceCommand.FanDirection -> "change_fan_speed"
            is VoiceCommand.HvacEco -> { entities["action"] = if (command.on) "set" else "unset"; "hvac_eco" }
            // New OEM controls execute only through typed handlers, never an older generic action.
            is VoiceCommand.HvacAuto -> {
                entities["family"] = "hvac_auto"; entities["action"] = if (command.on) "set" else "unset"
                "contract_only_vehicle_command"
            }
            is VoiceCommand.SetAutoFanProfile -> {
                entities["family"] = "hvac_auto_fan_profile"; entities["profile"] = command.profile.name.lowercase()
                "contract_only_vehicle_command"
            }
            is VoiceCommand.OemSwitch -> {
                entities["family"] = "oem_switch"; entities["feature"] = command.feature.name.lowercase()
                entities["action"] = if (command.on) "set" else "unset"
                "contract_only_vehicle_command"
            }
            is VoiceCommand.QueryOemSwitch -> {
                entities["family"] = "oem_switch_query"; entities["feature"] = command.feature.name.lowercase()
                "contract_only_vehicle_command"
            }
            is VoiceCommand.SetSunroofTilt -> {
                entities["family"] = "sunroof_tilt"; entities["action"] = if (command.on) "set" else "unset"
                "contract_only_vehicle_command"
            }
            is VoiceCommand.HvacAirflowAutoCycle -> { entities["action"] = if (command.on) "set" else "unset"; "hvac_airflow_auto_cycle" }
            is VoiceCommand.SetDriveMode -> { entities["mode"] = command.mode.name.lowercase(); "drive_mode" }
            is VoiceCommand.SetRegenLevel -> { entities["level"] = command.level.name.lowercase(); "regen_level" }
            is VoiceCommand.SetBrakePedalFeel -> { entities["mode"] = command.mode.name.lowercase(); "brake_pedal_feel" }
            is VoiceCommand.ComfortableStop -> { entities["action"] = if (command.on) "set" else "unset"; "comfortable_stop" }
            is VoiceCommand.SetChargeCurrentLimit -> {
                if (command.control != null) {
                    entities["action"] = command.control.name.lowercase()
                    "charge_control"
                } else {
                    entities["value"] = command.amps
                    entities["unit"] = "ampere"
                    "charge_current_limit"
                }
            }
            is VoiceCommand.AmbientLightPower -> { entities["action"] = if (command.on) "set" else "unset"; "ambient_light" }
            is VoiceCommand.SetAmbientBrightness -> { entities["action"] = "set"; entities["value"] = command.level; entities["unit"] = "number"; "ambient_brightness" }
            is VoiceCommand.SetAmbientColor -> { entities["action"] = "set"; entities["color"] = command.color.name.lowercase(); "ambient_color" }
            is VoiceCommand.InteriorLightOnDoorOpen -> { entities["action"] = if (command.on) "set" else "unset"; "interior_light_on_door" }
            is VoiceCommand.SetFollowMeHome -> { entities["duration"] = command.duration.name.lowercase(); "follow_me_home" }
            is VoiceCommand.WirelessChargerPower -> { entities["action"] = if (command.on) "set" else "unset"; "wireless_charger" }
            is VoiceCommand.SetDisplayTheme -> { entities["mode"] = command.mode.name.lowercase(); "display_theme" }
            is VoiceCommand.ParkingWiperAutoReduction -> { entities["action"] = if (command.enabled) "set" else "unset"; "parking_wiper_reduction" }
            is VoiceCommand.SetParkingModeDuration -> { entities["duration"] = command.duration.name.lowercase(); "parking_mode_duration" }
            is VoiceCommand.QueryVehicleStatus -> { entities["query"] = command.query.name.lowercase(); "vehicle_query" }
            is VoiceCommand.QuerySceneCapability -> { entities["scene"] = command.scene.name.lowercase(); "scene_capability_query" }
            is VoiceCommand.OpenSceneScreen -> { entities["scene"] = command.scene.name.lowercase(); "scene_open_screen" }
            VoiceCommand.CloseAllApps -> "go_home"
            is VoiceCommand.ScreenBlackout -> { entities["action"] = if (command.on) "set" else "unset"; "screen_blackout" }
            is VoiceCommand.QueryLocalInfo -> { entities["kind"] = command.kind.name.lowercase(); "local_info_query" }
            is VoiceCommand.QueryWindowingCapability -> { entities["mode"] = command.mode.name.lowercase(); command.target?.let { entities["app"] = it.name.lowercase() }; "windowing_capability_query" }
            is VoiceCommand.SplitScreen -> {
                entities["action"] = command.action.name.lowercase()
                command.left?.let { entities["left"] = it.name.lowercase() }
                command.right?.let { entities["right"] = it.name.lowercase() }
                command.side?.let { entities["side"] = it.name.lowercase() }
                command.app?.let { entities["app"] = it.name.lowercase() }
                command.ratio?.let { entities["value"] = it; entities["unit"] = "percent" }
                "set_split_screen"
            }
            VoiceCommand.QueryWifiState -> "wifi_state_query"
            VoiceCommand.QueryWifiIpAddress -> "wifi_ip_query"
            VoiceCommand.QueryWifiSsid -> "wifi_ssid_query"
            VoiceCommand.OpenWifiSettings -> "wifi_settings_open"
            VoiceCommand.QueryDisplayBrightness -> "display_brightness_query"
            is VoiceCommand.SetManualDisplayBrightness -> {
                if (command.mode != null) {
                    entities["mode"] = command.mode.name.lowercase()
                    "display_brightness_mode"
                } else {
                    entities["percent"] = command.percent
                    "display_brightness_manual"
                }
            }
            is VoiceCommand.SeatClimate -> {
                command.level?.let { entities["value"] = it }
                command.delta?.let { entities["action"] = if (it > 0) "increase" else "decrease" }
                if (command.kind == SeatClimateKind.HEAT) "seat_heat" else "seat_ventilation"
            }
            is VoiceCommand.MaxAc -> { entities["action"] = if (command.on) "set" else "unset"; "hvac_max_ac" }
            is VoiceCommand.SteeringWheelHeat -> { entities["action"] = if (command.on) "set" else "unset"; "steering_wheel_heat" }
            is VoiceCommand.CabinLight -> { entities["action"] = if (command.on) "set" else "unset"; "light_control" }
            is VoiceCommand.ExteriorLight -> { entities["mode"] = command.mode.name.lowercase(); "exterior_light_control" }
            is VoiceCommand.FogLight -> { entities["action"] = if (command.on) "set" else "unset"; "exterior_light_control" }
            is VoiceCommand.Sunroof -> {
                entities["part"] = command.part.name.lowercase()
                command.percent?.let { entities["value"] = it }
                "sunroof_control"
            }
            is VoiceCommand.Window -> {
                when (command.operation) {
                    WindowOperation.OPEN -> entities["action"] = "set"
                    WindowOperation.CLOSE -> entities["action"] = "unset"
                    WindowOperation.SET_PERCENT -> { entities["action"] = "set"; command.percent?.let { entities["value"] = it }; entities["unit"] = "percent" }
                    WindowOperation.STOP -> entities["action"] = "stop"
                }
                addWindowTargets(entities, command.targets)
                "window_control"
            }
            is VoiceCommand.SetDoorLock -> {
                entities["locked"] = command.locked
                "contract_only_vehicle_command"
            }
            VoiceCommand.OpenFrontTrunk -> "contract_only_vehicle_command"
            VoiceCommand.UnlockRearTrunk -> "contract_only_vehicle_command"
            is VoiceCommand.SetHeadlightHeight -> {
                entities["level"] = command.level
                "contract_only_vehicle_command"
            }
            is VoiceCommand.SetIntelligentHighBeam -> {
                entities["enabled"] = command.enabled
                "contract_only_vehicle_command"
            }
            is VoiceCommand.OpenApp -> { entities["app"] = command.target.name.lowercase(); "launch_app" }
            is VoiceCommand.OpenInstalledApp -> { entities["app"] = command.spokenName; "launch_app" }
            is VoiceCommand.OpenMedia -> {
                entities["provider"] = command.provider.name.lowercase()
                entities["kind"] = command.kind.name.lowercase()
                entities["action"] = "open"
                "play_media"
            }
            is VoiceCommand.SearchMedia -> {
                entities["provider"] = command.provider.name.lowercase()
                entities["query"] = command.query
                entities["kind"] = command.kind.name.lowercase()
                "play_media"
            }
            is VoiceCommand.MediaControl -> { entities["action"] = command.action.name.lowercase(); "media_control" }
            is VoiceCommand.SetMediaVolume -> { entities["action"] = "set"; entities["value"] = command.value; entities["unit"] = if (command.unit == VolumeUnit.PERCENT) "percent" else "number"; "change_volume" }
            is VoiceCommand.AdjustMediaVolume -> { entities["action"] = if (command.steps >= 0) "increase" else "decrease"; entities["value"] = kotlin.math.abs(command.steps); entities["unit"] = "number"; "change_volume" }
            is VoiceCommand.Navigate -> { entities["provider"] = command.provider.name.lowercase(); entities["destination"] = command.destination; "navigation" }
        }
        return LegacyPredictionPayload(parsed.originalText, parsed.normalizedText, intent, entities)
    }

    private fun addWindowTargets(entities: MutableMap<String, Any>, targets: Set<WindowTarget>) {
        if (WindowTarget.ALL in targets) { entities["position"] = "both"; entities["direction"] = "both"; return }
        when (targets) {
            setOf(WindowTarget.FRONT_LEFT) -> { entities["position"] = "front"; entities["direction"] = "left" }
            setOf(WindowTarget.FRONT_RIGHT) -> { entities["position"] = "front"; entities["direction"] = "right" }
            setOf(WindowTarget.REAR_LEFT) -> { entities["position"] = "rear"; entities["direction"] = "left" }
            setOf(WindowTarget.REAR_RIGHT) -> { entities["position"] = "rear"; entities["direction"] = "right" }
            setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT) -> { entities["position"] = "front"; entities["direction"] = "both" }
            setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT) -> { entities["position"] = "rear"; entities["direction"] = "both" }
            else -> { entities["position"] = "both"; entities["direction"] = "both" }
        }
    }
}
