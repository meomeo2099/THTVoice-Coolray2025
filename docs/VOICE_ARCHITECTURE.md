# VoiceGeely Architecture

## Required command flow

```text
ECARX key / wake word
        ↓
Voice input
        ↓
STT
        ↓
TextNormalizer
        ↓
Alias + synonym normalization
        ↓
DynamicCommandParser
        ↓
Typed VoiceCommand
        ↓
CommandRouter
        ├── HVAC
        ├── Window / Door
        ├── Driving / Regen / AVAS
        ├── Media
        ├── Apps
        ├── Navigation
        └── Vehicle queries
                ↓
VehicleAccess or Android subsystem
                ↓
Read-back / verification
                ↓
TTS response
```

Speech text never calls a vehicle property directly. Parser output must be typed, for example:

```text
SetTemperature(zone=DRIVER, celsius=22.0)
SetWindowPosition(window=FRONT_LEFT, percent=30)
SetFanSpeed(level=5)
SetDriveMode(mode=SPORT)
OpenApp(app=YOUTUBE)
Navigate(provider=WAZE, destination="...")
```

## HVAC number handling
`HI` and `LO` are endpoint commands, not number aliases. Numbers `16..32` are parsed before endpoint keywords. Ambiguous ASR fragments must not silently become HI/LO.

## Backend rule
Production access goes through one VehicleAccess facade. Every attempt should log logical command, backend, property/function, area, requested value, write acceptance, read-back and final status. `ACCEPTED` and `CONFIRMED` are distinct.

## Implementation order
1. HVAC
2. Windows
3. Door/lock
4. Drive mode
5. Regeneration
6. AVAS
7. Vehicle status queries
8. Media
9. App launcher
10. Navigation/search
11. Wi-Fi/display/overlay
12. keep-alive/watchdog/restore

Vehicle-specific IDs stay behind adapters/configuration, not in parser code.
