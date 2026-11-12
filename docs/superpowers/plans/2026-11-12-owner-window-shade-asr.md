# Owner window, shade and ASR corrections

> **For agentic workers:** Use superpowers:executing-plans; fresh final review.

**Goal:** Correct the owner's 1.0.10 reports without changing vehicle mappings or ASR engines.

**Architecture:** Contextual normalization and fail-closed window target parsing. Explicit shade commands must reach the MCU even if an untrusted pre-read already equals the requested position; unchanged target-matching reads cannot prove shade motion.

**Spec:** Owner reports “hạ kính sau bên lá” moves both rear windows; “quạt” becomes “hoạt”; close shade says completed while hardware does not move. Shade opening works.

## Tasks
- [x] Reproduce each case with failing parser/executor tests, including unrelated “hoạt động” and ambiguous window-side negatives.
- [x] Correct contextual fan/driver aliases, prevent side loss from broadening the command, and fix false shade confirmation/pre-read no-op.
- [x] Run regressions, independent review and signed candidate CI; document live hardware limits.

## Evidence and constraints
- `parseWindowTargets` does not recognize “bên lá”; rear-row fallback chooses both windows.
- Normalizer handles fan “phạt” but not “hoạt”. Avoid global replacement of the legitimate word “hoạt”.
- Shade `moveTo` previously returned CONFIRMED without sending a request when a single baseline read was within tolerance. Owner's close result can follow this exact no-write path. Real firmware telemetry freshness is unverified, so explicitly send then require observed position evidence before confirming.
- Existing Coolray2025 MCU byte6 = shade; individual rear byte2/3 remain unchanged, other bytes -1. No speculative mapping, automatic glass close, root, or OTA publication.
- Google recognition is a separate question: explain availability/network constraints using Android documentation; do not silently replace the offline ASR or start sending audio online.

## Ledger
- RED: 130 focused tests ran with five failures reproducing the owner issues, including bothrear writes for the exact transcript and zero MCU writes for stale shade zero.
- GREEN: all130 focused tests passed after changes; individual rear payload tests include open, close and50percent, checking other motors remain -1.
- Fresh read-only reviewer found no material defects.
- Explicit shade requests now always send byte6; stable targetmatching baseline reads cannot produce hardware confirmation. A later observed away-to-target position can confirm. No automatic sunroofglass operation is added.

## Approved scope additions
- Owner requested importing command/error phrases from THTVoice: compared sourcecommit424a7b9 and referenceCSV (3,230rows, no new phrase strings versus local). Added shared20-case CSV/tests, newer compatible ASR corrections and context-scoped scene pronunciation aliases; see docs/THTVOICE_SHARED_ASR_IMPORT.md.
- Owner requested newest Sherpa-ONNX: pinned officialAndroidAAR2.0.1 and SHA256a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7. Verify downloaded minSdk/ARM JNI/API classes in CI; modelweights remain unchanged. See docs/SHERPA_RUNTIME.md.
- CI firstattempt43150695491 compiled all source, then failed1of831 tests: coverage lost16 row-label phrases (2681vs2697). Reproduced locally, preserved recognized rowlabels, and all135tests including fullcorpus passed. No coverage threshold was lowered.
- SharedASR import RED2failures, then GREEN138focused tests including fullcorpus. Updated oldnormalizer expectation from “đóng cửa sổ” to sharedcanonical “đóng kính”, with an explicit ambiguity/clarification assertion.

- Expanded-scope final review found no material defect and independently matched official AAR2.0.1 checksum. Local138tests (including20shared cases and full phrasecorpus) green; newruntime archive checks pendingCI.

## Final verification
- Signed candidate1.0.11(versionCode12), buildcommit a687f98fdaccb18f6db93bd22514d1d50c3ffca7. Actionsrun43152664162/job130965284432: real AARverification, SQLite checks, completeAndroidunit suite, releaseassembly and certificate/package/version checks allpassed. OTA publication skipped.
- RealSherpa2.0.1 AAR: minSdk21; ARM32+ARM64 JNI and ASR/KWS/TTS/denoiser classes verified; allproductionKotlin callsites compiled.
- APK SHA256 31b8fc0ee2557f8e893c93909c4d5a406564c10838374116042aa33a93ca2e51. Artifact THTVoice-rc-1.0.11-12 /13914279307.
- Keep authorizedtestbranch; restore manual release trigger, retain runtimeverification in workflows. No nativevehicle execution or speechaccuracy benchmark claimed.
