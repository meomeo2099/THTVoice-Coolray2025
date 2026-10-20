package com.voicegeely.app.voice

/** Canonical typed commands used between speech parsing and execution. */
sealed class VoiceCommand {
    data class SetTemperature(val celsius: Float) : VoiceCommand()
    data class AdjustTemperature(val deltaCelsius: Float) : VoiceCommand()
    object TemperatureHigh : VoiceCommand()
    object TemperatureLow : VoiceCommand()

    /**
     * HVAC power control. Generic "điều hòa" keeps COMBINED for backward compatibility;
     * explicit HVAC-system or A/C-compressor utterances can target one property only.
     */
    data class AcPower(
        val on: Boolean,
        val target: HvacPowerTarget = HvacPowerTarget.COMBINED
    ) : VoiceCommand()
    data class SetFanSpeed(val level: Int) : VoiceCommand()
    data class AdjustFanSpeed(val delta: Int) : VoiceCommand()
    data class Recirculation(val enabled: Boolean) : VoiceCommand()
    data class Defrost(val zone: DefrostZone, val on: Boolean) : VoiceCommand()
    data class FanDirection(val directions: Set<FanDirectionTarget>) : VoiceCommand()
    /** HVAC Eco (air-conditioning economy). Not the drive-mode Eco. REFERENCE/UNVERIFIED. */
    data class HvacEco(val on: Boolean) : VoiceCommand()
    /** Automatic airflow cycle (tự động đảo gió). Not recirculation. REFERENCE/UNVERIFIED. */
    /**
     * [periodicMinutes] null: the car's own automatic airflow cycle. Non-null (with on = true):
     * THT Voice swings the direction face ↔ feet every that many minutes (0 = Settings value).
     */
    data class HvacAirflowAutoCycle(val on: Boolean, val periodicMinutes: Int? = null) : VoiceCommand()
    data class SetDriveMode(val mode: DriveMode) : VoiceCommand()
    data class SetRegenLevel(val level: RegenLevel) : VoiceCommand()
    data class SetBrakePedalFeel(val mode: BrakePedalFeel) : VoiceCommand()
    data class ComfortableStop(val on: Boolean) : VoiceCommand()
    /**
     * Charge-current limit when [control] is null; guarded STOP/START_NOW request
     * when [control] is set. Keeping one sealed subtype preserves existing routing
     * while charge control is migrated through the same parked-only safety policy.
     */
    data class SetChargeCurrentLimit(
        val amps: Int,
        val control: ChargeControlAction? = null
    ) : VoiceCommand()

    data class AmbientLightPower(val on: Boolean) : VoiceCommand()
    data class SetAmbientBrightness(val level: Int) : VoiceCommand()
    data class SetAmbientColor(val color: AmbientColor) : VoiceCommand()

    data class InteriorLightOnDoorOpen(val on: Boolean) : VoiceCommand()
    data class SetFollowMeHome(val duration: FollowMeHomeDuration) : VoiceCommand()
    data class WirelessChargerPower(val on: Boolean) : VoiceCommand()
    data class SetDisplayTheme(val mode: DisplayThemeMode) : VoiceCommand()
    data class ParkingWiperAutoReduction(val enabled: Boolean) : VoiceCommand()
    data class SetParkingModeDuration(val duration: ParkingModeDuration) : VoiceCommand()

    data class QueryVehicleStatus(val query: VehicleStatusQuery) : VoiceCommand()
    /** Read-only Android scene component availability; does not activate a scene. */
    data class QuerySceneCapability(val scene: SceneType) : VoiceCommand()
    /** Opens the OEM rest/camping screen only; never claims the scene was activated. */
    data class OpenSceneScreen(val scene: SceneType) : VoiceCommand()
    /** Goes to the home screen; a normal app cannot force-stop other apps. */
    object CloseAllApps : VoiceCommand()
    /** Black full-screen cover on/off. */
    data class ScreenBlackout(val on: Boolean) : VoiceCommand()
    /** Answered locally from the device, no vehicle or network access. */
    data class QueryLocalInfo(val kind: LocalInfoKind) : VoiceCommand()
    /**
     * Read-only windowing probe. `target` is used only for an app-specific PiP
     * precondition query; it never means the app/window may be launched.
     */
    data class QueryWindowingCapability(
        val mode: WindowingMode,
        val target: AppTarget? = null
    ) : VoiceCommand()
    /** Read-only Android Wi-Fi client radio state. */
    object QueryWifiState : VoiceCommand()
    /** IPv4 of the active default Wi-Fi network, if independently readable. */
    object QueryWifiIpAddress : VoiceCommand()
    /** SSID of the active default Wi-Fi link, if location access permits. */
    object QueryWifiSsid : VoiceCommand()
    /** Opens Android Wi-Fi settings only; never means the radio was toggled. */
    object OpenWifiSettings : VoiceCommand()
    /** Android system brightness preference, distinct from vehicle display theme. */
    object QueryDisplayBrightness : VoiceCommand()
    /**
     * Existing manual brightness command, extended without adding a new sealed subtype.
     * `mode == null` keeps the existing 1..100% level behavior. When `mode` is set,
     * this is a mode-only request and `percent` is ignored (callers use 0).
     */
    data class SetManualDisplayBrightness(
        val percent: Int,
        val mode: DisplayBrightnessMode? = null
    ) : VoiceCommand()

    /**
     * Coolray front-seat heating / ventilation (356517131 / 356517139, area 1 driver, 4 passenger).
     * Exactly one of [level] (0 off, 1..3) or [delta] (+1 / -1) is set. REFERENCE.
     */
    data class SeatClimate(
        val kind: SeatClimateKind,
        val seats: Set<SeatTarget>,
        val level: Int? = null,
        val delta: Int? = null
    ) : VoiceCommand()

    /** Max A/C — làm lạnh nhanh (354419974 area 117, boolean). REFERENCE (REF-SYSUI). */
    data class MaxAc(val on: Boolean) : VoiceCommand()

    /** Steering wheel heat (289408269 area 0: 1 on / 0 off). REFERENCE (REF-SYSUI / REF-WIDGET). */
    data class SteeringWheelHeat(val on: Boolean) : VoiceCommand()

    /** Coolray cabin / reading lights (356519684, areas 1,2,4,16,64, int 1/0). REFERENCE. */
    data class CabinLight(val on: Boolean) : VoiceCommand()
    /** Coolray exterior lights (557871126 area 0: 0 off, 1 position, 3 low beam). REFERENCE. */
    data class ExteriorLight(val mode: ExteriorLightMode) : VoiceCommand()
    /** Coolray fog lights (289410578 area 0, int 1/0). REFERENCE. */
    data class FogLight(val on: Boolean) : VoiceCommand()

    /**
     * Coolray sunroof glass or its sunshade (window property 591405227, areas 65536 / 131072).
     * [percent] is the open position 0..100; OPEN = 100, CLOSE = 0. REFERENCE.
     */
    data class Sunroof(
        val part: SunroofPart,
        val operation: WindowOperation,
        val percent: Int? = null
    ) : VoiceCommand()

    data class Window(
        val targets: Set<WindowTarget>,
        val operation: WindowOperation,
        val percent: Int? = null
    ) : VoiceCommand()

    /** Parked-only door lock/unlock command. Runtime routing is added in a later slice. */
    data class SetDoorLock(val locked: Boolean) : VoiceCommand()

    /** Parked-only front-trunk latch-release command. Runtime routing is added later. */
    object OpenFrontTrunk : VoiceCommand()

    /**
     * Parked-only rear-trunk access contract. The retained base-platform reference
     * unlocks all four door-lock areas and still expects the user to press the
     * physical rear-tailgate button; this command never means the latch opened.
     */
    object UnlockRearTrunk : VoiceCommand()

    /** Parked-only headlight-height contract. Parser/router exposure is added later. */
    data class SetHeadlightHeight(val level: Int) : VoiceCommand()

    /** Parked-only Intelligent High Beam contract. Parser/router exposure is added later. */
    data class SetIntelligentHighBeam(val enabled: Boolean) : VoiceCommand()

    data class OpenApp(val target: AppTarget) : VoiceCommand()
    /**
     * Split screen through the separate THT Split app (API v2). [left]/[right] are set for START
     * with a named pair; [side] for REPLACE/FULLSCREEN; [app] for REPLACE or FULLSCREEN by app name;
     * [ratio] is the left share in percent for RATIO.
     */
    data class SplitScreen(
        val action: SplitAction,
        val left: AppTarget? = null,
        val right: AppTarget? = null,
        val side: SplitSide? = null,
        val app: AppTarget? = null,
        val ratio: Int? = null
    ) : VoiceCommand()
    /** Any launchable app installed on the head unit, resolved by its label at execution time. */
    data class OpenInstalledApp(val spokenName: String) : VoiceCommand()
    data class OpenMedia(
        val kind: MediaContentType,
        val provider: MediaProvider = MediaProvider.DEFAULT
    ) : VoiceCommand()
    data class SearchMedia(
        val provider: MediaProvider,
        val query: String,
        val kind: MediaContentType = MediaContentType.MUSIC
    ) : VoiceCommand()
    data class MediaControl(val action: MediaAction) : VoiceCommand()
    /** "âm lượng 15" (head-unit media volume level) or "âm lượng 50 phần trăm". */
    data class SetMediaVolume(val value: Int, val unit: VolumeUnit = VolumeUnit.LEVEL) : VoiceCommand()
    /** "tăng âm lượng 3 nấc": [steps] > 0 louder, < 0 quieter. */
    data class AdjustMediaVolume(val steps: Int) : VoiceCommand()
    data class Navigate(val provider: NavigationProvider, val destination: String) : VoiceCommand()
}

enum class HvacPowerTarget { COMBINED, SYSTEM, COMPRESSOR }
/** Parser-only on Coolray: charging commands are recognised but always rejected. */
enum class ChargeControlAction { STOP, RESTART, START_NOW }
enum class DefrostZone { FRONT, REAR }
enum class FanDirectionTarget { FACE, FEET, WINDSHIELD }
enum class DriveMode { ECO, COMFORT, SPORT }
enum class RegenLevel { LOW, MEDIUM, HIGH }
enum class BrakePedalFeel { COMFORT, SPORT }
enum class AmbientColor { RED, GREEN, BLUE, WHITE, YELLOW, PURPLE, PINK, ORANGE, CYAN }
enum class FollowMeHomeDuration { OFF, SECONDS_30, SECONDS_60, SECONDS_90 }
enum class DisplayThemeMode { LIGHT, DARK, AUTO }
enum class DisplayBrightnessMode { MANUAL, AUTOMATIC }
enum class ParkingModeDuration { OFF, MINUTES_30, HOURS_1, HOURS_2, HOURS_3, HOURS_4, HOURS_5, CONTINUOUS }
enum class VehicleStatusQuery {
    SPEED,
    GEAR,
    BATTERY_SOC,
    RANGE,
    ODOMETER,
    TRIP_DISTANCE,
    OUTSIDE_TEMPERATURE,
    DOOR_STATUS,
    DOOR_LOCK_STATUS,
    CHARGE_STATUS,
    CHARGE_CURRENT,
    CHARGE_VOLTAGE,
    PASSENGER_OCCUPANCY,
    DRIVER_SEATBELT,
    PASSENGER_SEATBELT,
    CLIMATE_TEMPERATURE,
    FAN_SPEED,
    DRIVE_MODE,
    REGEN_LEVEL,
    CRUISE_STATUS,
    BRAKE_PEDAL_FEEL,
    COMFORTABLE_STOP_STATUS,
    CHARGE_CURRENT_LIMIT,
    PARKING_WIPER_AUTO_REDUCTION,
    PARKING_MODE_DURATION,
    AMBIENT_LIGHT_POWER,
    AMBIENT_BRIGHTNESS,
    AMBIENT_COLOR,
    INTERIOR_LIGHT_ON_DOOR_OPEN,
    FOLLOW_ME_HOME,
    WIRELESS_CHARGER,
    DISPLAY_THEME,
    CABIN_TEMPERATURE,
    AUTO_CLOSE_WINDOWS_ON_LOCK,
    FUEL_LEVEL,
    AVERAGE_FUEL_CONSUMPTION,
    COOLANT_TEMPERATURE,
    BATTERY_VOLTAGE,
    TIRE_PRESSURE,
    SERVICE_DUE,
    /** Owner 16/10/2026 "tình trạng xe": battery, range, doors, locks and windows in one answer. */
    CAR_SUMMARY
}

enum class SceneType { REST, CAMPING }
enum class LocalInfoKind { TIME, DATE, ASSISTANT_STATUS }
enum class WindowingMode { PIP, FREEFORM, SPLIT_SCREEN }
enum class SplitAction { START, SWAP, RATIO, REPLACE, FULLSCREEN, RESTORE, HIDE, SHOW, STOP }
enum class SplitSide { LEFT, RIGHT }

enum class SunroofPart { GLASS, SHADE }
enum class SeatClimateKind { HEAT, VENT }
enum class SeatTarget { DRIVER, PASSENGER }
enum class ExteriorLightMode { OFF, POSITION, LOW_BEAM }
enum class WindowTarget { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, ALL }
enum class WindowOperation { OPEN, CLOSE, SET_PERCENT, STOP }

enum class AppTarget {
    YOUTUBE,
    VANCED,
    REVANCED,
    MORPHE,
    YOUTUBE_MUSIC,
    ZING_MP3,
    SPOTIFY,
    SMARTTUBE,
    MAPS,
    /** "mở bản đồ" / "mở map": the user's default map app (Settings / Apps), then fallbacks. */
    DEFAULT_MAPS,
    WAZE,
    VIETMAP,
    CHROME,
    NETFLIX,
    TIKTOK,
    VLC,
    KIKI
}

enum class MediaContentType { MUSIC, VIDEO }
enum class MediaProvider {
    DEFAULT,
    YOUTUBE,
    VANCED,
    REVANCED,
    MORPHE,
    YOUTUBE_MUSIC,
    ZING_MP3,
    SPOTIFY,
    SMARTTUBE
}
enum class VolumeUnit { LEVEL, PERCENT }
enum class MediaAction { PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE, UNMUTE }
enum class NavigationProvider { AUTO, GOOGLE_MAPS, WAZE, VIETMAP }

data class ParsedVoiceCommand(
    val originalText: String,
    val normalizedText: String,
    val command: VoiceCommand
)