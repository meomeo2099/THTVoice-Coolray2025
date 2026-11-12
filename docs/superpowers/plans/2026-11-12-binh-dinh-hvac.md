# Unified regional speech corrections and HVAC activation

> **For agentic workers:** Use superpowers:executing-plans with one fresh final review.

**Goal:** Understand the owner's reported regional ASR errors, expose the intended AUTO commands, and request system power/A/C before valid temperature adjustments.

**Spec:** Owner confirms the recognized text itself says “lấy gió treo”; tài->tay, sau->seo, sấy->sái/sáy. AUTO and inside/outside circulation fail; other HVAC modes have not yet been tested. Temperature changes must turn A/C on first, default adjustment0.5°C.

**Architecture:** Context-scoped language normalization; canonical OEM non-zoned function overloads for global functions; explicit HVAC prerequisites followed by combined power/A/C/setpoint read-back. Keep Sherpa1.13.8, modelweights and microphone settings unchanged.

## Evidence
- Temperature executor currently writes only setpoint.
- AUTO parser accepts “điều hòa auto” but misses “bật chế độ auto”.
- Existing verified Lunaris CarClimat reference uses getFunctionValue(id), setFunctionValue(id,value) for AUTO, A/C, circulation and climate power, while the reflection adapter currently only resolves zoned overloads. Global calls must honor the canonical non-zoned contract when available. Preserve zoned signatures for explicit zones and legacy fallback when the non-zoned method is absent; never retry a rejected global request through another zone.

## Tasks
- [x] Write failing tests for reported dialect phrases/negative text, global-vs-zoned overload contracts, AUTO parsing/prerequisites and temperature write ordering/partial confirmation.
- [x] Implement narrowly scoped corrections and HVAC execution changes; preserve denied/unknown distinctions and invalid-input no-side-effect behavior.
- [x] Run focused/full corpus tests, independent review and signed candidate CI; record verified Git/artifact state and restore manual build workflow.

## Constraints
- Language rules apply only to explicit vehicle-command contexts; preserve app names, destinations and unrelated ordinary words.
- No claim of acoustic-model retraining or full regional-accent accuracy without audio evaluation.
- Only Confirmed may claim full completion; temperature confirmation now requires requested setpoint plus system/A/C on. Unknown A/C state stays AcceptedUnverified.
- No new vehicle function IDs, unsupported heating features, root, firmware changes or OTA publishing.

## Owner steering and ledger
- Owner requested AUTO variants aoto/aotu and broader regional coverage, then explicitly requested one merged pipeline rather than a BinhDinh mode. All rules are in VoiceTextNormalizer; no selectable accent/profile UI or new acoustic model.
- Initial RED163focusedtests/12failures reproduced missing aliases, missing A/C prerequisites and wrong global-overload selection. A test-car connect stub was corrected before recording the contract failures.
- GREEN163 after implementation and regression updates. The old null expectation for bare “bật auto” was deliberately updated to HVAC AUTO per owner; engine/stability/AutoHold boundaries remain tested. A negative place-name fixture stopped expecting Vietnamese-number “tư” to remain unnormalized.
- Existing unrestricted quạc alias was moved into command context after a real negative test exposed app-name corruption.
- Added a failing unsupported-AUTO test, then checked support before activating power. AUTO-off and unknown-power read-back tests preserve no-side-effects and no false completion.
- Detailed behavior and limits: docs/HVAC_REGIONAL_UPDATES.md. Sherpa1.13.8 and model/mic settings remain unchanged.

- Final focused regression: OK (166 tests), build/regional113-final.log. Fresh independent review found no material defects; signed Android CI remains pending.

- Android CI run43192587965 compiled production and ran853 tests:5 failures were stale cooling-state fixtures in CoolrayTemperatureRangeTest and OfflineCommandFlowTest. Updated only their fake power/A/C read-back; all existing assertions retained. Expanded local regression now OK (175 tests), build/regional113-final2.log.

- Follow-up independent review of the two fixture changes found no inappropriate assertion weakening. Production code was unchanged by the CI follow-up.

- Final signed candidate:1.0.13 (versionCode14), source278793f82908c2f75060fd155f523e2d72f5d793; CI43193453866/job131098307209 succeeded (unit tests, SQLite, runtime integrity, release assembly and signature/package/version verification). Artifact13932675596, THTVoice-rc-1.0.13-14, expires2026-11-13T09:45:28Z. APK SHA2569237c5efb13cc7bdea5a3344b6218f12de92bf793928a2c66b6472e7a0c326bb. OTA publication skipped; manual workflow restored in final documentation commit. Hardware behavior remains UNVERIFIED.
