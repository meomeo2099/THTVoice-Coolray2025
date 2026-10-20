# THT Voice Safety Architecture

## Goal

Vehicle voice commands and maintenance actions must pass a deterministic safety boundary before execution. Safety decisions use live IHU689G SPEED and GEAR telemetry and fail closed when required telemetry is unavailable.

## Motion state

- SPEED: IHU689G property `291504647`, already scaled as km/h by the Geely adaptation layer.
- GEAR: property `289408001`.
- Parked means `gear == P (4)` and `speed < 1.0 km/h`.

## Command policy

### SAFE

Normal low-risk commands execute immediately and keep the existing execute/read-back/result pipeline.

### CONFIRM_WHEN_MOVING

Current commands:
- window control
- drive mode
- regeneration level

When not parked:
1. SafetyGate reads fresh SPEED + GEAR.
2. Command is stored as a short-lived pending command.
3. THT Voice asks for explicit confirmation.
4. Pending command expires after 8 seconds.
5. Voice service automatically opens the microphone for the confirmation response.
6. On confirmation, SafetyGate re-reads SPEED + GEAR before execution.
7. Cancellation or expiry prevents execution.

### PARKED_ONLY

Cannot be overridden by a spoken confirmation. Typed non-voice actions are defined by `ParkedAction`:
- `OTA_INSTALL`
- `APK_INSTALL`
- `DEV_TOOLS`
- `SYSTEM_MAINTENANCE`
- `RESET_APP_DATA`
- `TECHNICAL_CONFIGURATION`

Use `ParkedActionGate` for these actions.

## Deferred install boundary

`SafeInstallGate` separates download from installation:

- Download may complete while the vehicle is moving.
- If installation is requested while not safely parked, only the action type is stored as pending.
- No executable callback is persisted.
- The caller must retry with a fresh install callback.
- Retry always re-checks live SPEED + GEAR.
- Installation callback runs only after the fresh check returns parked.

This supports the target OTA flow:

```
download -> verify artifact -> request install
                            |
                       moving/not P
                            |
                          defer
                            |
                    vehicle returns to P
                            |
                       fresh re-check
                            |
                         install
```

## Audit trail

`SafetyAuditRegistry` keeps a bounded process-local history. It stores no microphone audio.

Recorded fields include:
- command/action name
- policy requirement
- decision
- SPEED
- GEAR
- phase
- confirmation/result when available
- detail/reason

`SafetyAuditFormatter` renders the latest entries for future debug UI/log export.

Execution results are appended by `VoiceCommandCoordinator` as one of:
- `CONFIRMED`
- `ACCEPTED_UNVERIFIED`
- `REJECTED`
- `FAILED`

## Fail-closed rules

Sensitive and parked-only actions are blocked when SPEED or GEAR cannot be read. Do not infer that the vehicle is parked from missing telemetry.

## Next integration

When the production updater/installer module is restored, it must call `SafeInstallGate` immediately before PackageInstaller/installer execution. Hash/signature/package/version verification should occur before the parked-only install request.
