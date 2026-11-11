# Coolray OEM command integration plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Connect supported Vietnamese voice commands to the Coolray 2025 OEM APIs identified in the user's framework and Lunaris reference APK, and document every command group.

**Architecture:** Keep the existing typed parser → capability gate → family executor → ECARX/VHAL → read-back pipeline. Implement functional behavior independently from reference API identifiers and observed calls. THT Voice has no runtime dependency on Lunaris, Magisk or LSPosed for these API calls.

**Tech Stack:** Kotlin, Android 9, reflection-only ECARX AdaptAPI, JUnit4, existing signed GitHub candidate workflow.

**Spec:** `docs/COOLRAY2025_VOICE_SPEC.md`, `docs/VOICE_WORKFLOW.md`, user's request to use Lunaris command groups and original vehicle data; existing local reference report `../voice/evidence/lunaris-20260918/KET_QUA_DOI_CHIEU.md`.

## Global constraints

- Package `com.tht.voice.coolray2025`; head unit SX11-LA/IHU730P.
- Temperature 16..32 °C, step 0.5; fan 1..7, preserve owner's hardware limits.
- Keep seat/steering/nozzle/windshield heating excluded as previously requested; ordinary HVAC demisting remains separate.
- Function IDs go through ICarFunction, MCU byte groups through CarImpl, sensor IDs through ISensor, standard properties through VHAL.
- No EV/charging/regen/front trunk features. No installer, root/firmware changes or direct car actuation during development.
- Hardware support checked at runtime; new routes are REFERENCE/UNVERIFIED. Accepted writes never imply Confirmed without matching read-back.

## Review focus

- Unknown support and missing API must reject new controls without writing.
- Drive ECO and HVAC ECO must stay distinct in parsing and execution.
- ESC Sport is its specific OEM mode, not a generic command to disable all stability control; new driving/brake switches require parked/P policy.
- Sensor/function/MCU/global IDs and window payload indexes must not be interchanged.
- Unknown sensor values and incomplete window readings must never produce a fabricated complete vehicle status.

### Task 1: Audit and expose OEM command metadata

Files: `car/Coolray2025Functions.kt`, new `docs/COOLRAY2025_LUNARIS_COMMAND_MATRIX.md`.
- [x] Inspect remaining reference function classes, sensor reads and bytecode for concrete actions.
- [x] Record all Lunaris command families, mapping, backend, THT route and exclusions.

### Task 2: Add typed OEM controls and Vietnamese parsing

Files: `voice/VoiceCommand.kt`, new `voice/CoolrayOemCommandParser.kt`, new `voice/CoolrayOemCommandExecutor.kt`, `voice/VoiceCommandParser.kt`, `voice/CoolrayCapabilities.kt`, `voice/VehicleCommandRouter.kt`, `voice/VoiceResponseFormatter.kt`, `voice/safety/SafetyPolicy.kt`.
- [x] Write parser, read-back, unavailable/refused, OEM enum and safety tests; establish failures.
- [x] Implement AUTO/ECO HVAC, automatic fan profile, three drive modes, DRL, Start/Stop, Auto Hold and explicit ESC Sport controls and read-only switch status.
- [x] Reuse established commands where possible; split drive-mode capability from unsupported regen. Keep heating out of routing.
- [x] Run targeted tests and full parser/router regression checks.

### Task 3: Complete read paths and sunroof consistency

Files: `car/EcarxFunctionAccess.kt`, `car/DeferredEcarxFunctionPort.kt`, `voice/VehicleStatusQueryExecutor.kt`, `voice/SunroofVoiceCommandExecutor.kt`, their tests.
- [x] Test sensor range access, OEM fallback for unavailable standard readings, complete/incomplete MCU window summary and integer sunroof position fallback.
- [x] Add a read-only ISensor port with actual method names from reference, preserving unknown/null.
- [x] Use correct MCU state for window status; require all four readable before saying all windows closed.
- [x] Preserve correct sunroof/rèm payload indexes; add integer position fallback consistently with door windows.

### Task 4: Validate and deliver

- [ ] Run targeted JVM tests, full Android unit tests and release compilation.
- [ ] Build candidate 1.0.9 (10) using configured THT_SIGN_*; verify package/version/signature.
- [ ] Restore manual-only workflow, synchronize local test branch, deliver APK with sample commands and hardware verification limits.


## Execution ledger

- Audit: inventoried all47 advertised Lunaris actions; verified new function IDs/enum calls, sensor backend, roof tilt and fuel decoder directly from APK bytecode. Original vehicle config remains authoritative for16..32 °C and fan1..7.
- Implementation: added OEM family executor and explicit parser; API availability/readback and safety policies retained. Added ISensor read path and completed MCU status/roof handling. Heating removed from production capability routing.
- Review: an independent code review found ambiguous/negated drive-mode parsing and zero-percent roof commands bypassing tilt closure. Both were reproduced in tests and fixed.
- Ruling: include the fuel-unit correction and MCU empty-payload guard in this change because these directly affect truthful voice vehicle status. Standard FUEL_LEVEL ml takes precedence; MCU percent needs known capacity for litres. Shipped byteToInt returns-1 for null but0 for empty; the bridge now preserves both as unknown.
- Local validation: full production parse chain, response formatter and targeted regression suite pass; final CI/release-signature verification pending below.
