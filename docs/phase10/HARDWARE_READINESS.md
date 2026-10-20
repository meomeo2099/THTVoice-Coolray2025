# Phase 10 — IHU689G hardware readiness gate

Baseline: current green `main`; software/package readiness is not the same as hardware confirmation.

## Standard PR CI gate
PR CI is a source/build gate, not the hardware APK delivery path. A PR is code-green only when all of these pass:

- [x] PowerShell diagnostic scripts parse successfully.
- [x] Phase 10 hardware test matrix schema is valid.
- [x] Sherpa Android runtime fetch/verification succeeds.
- [x] ASR 65M staging test succeeds.
- [x] Offline TTS staging test succeeds.
- [x] Hardware package provenance writer self-test succeeds.
- [x] Hardware package preflight/runner self-tests succeed.
- [x] Unit tests pass.
- [x] `assembleDebug` succeeds.

Normal PR CI intentionally does **not** upload an APK artifact. Artifact quota must not turn successful tests/builds into a false red PR.

## Hardware-test build gate
- [x] Sherpa Android runtime fetch script exists and verifies pinned SHA-256.
- [x] Vietnamese 65M INT8 ASR is staged from a pinned revision with model checks.
- [x] Pinned KWS model and all three selectable keyword files (Hey THT / Hello Geely / Hey Geely) are staged with SHA-256 verification.
- [x] Unit tests run before APK assembly.
- [x] APK checksum and `BUILD_INFO.txt` are generated.
- [x] `ASSET_SHA256.txt` records SHA-256 for Sherpa runtime, ASR, KWS, staged TTS assets and every packaged Phase 10 matrix/evidence input.
- [x] The general `IHU689G_TEST_MATRIX.csv` is copied into the artifact and its packaged bytes are hash-bound to `BUILD_INFO.txt`.
- [x] AVAS/ADAS and Lighting focused matrices plus AVAS/ADAS and Scene read-only probes are provenance-bound in the artifact.
- [ ] Production Ngoc Huyen TTS assets have a pinned/licensed source.
- [ ] Production Diem Trinh TTS assets have a pinned/licensed source.

The `IHU689G hardware test APK` workflow publishes the package to Actions artifact storage. If Actions artifact storage is unavailable, the rolling private prerelease fallback must contain the same required files and pass the same local preflight.

The hardware-test artifact must contain these 10 files:

- `THTVoice-IHU689G-hardware-test.apk`
- `SHA256.txt`
- `BUILD_INFO.txt`
- `ASSET_SHA256.txt`
- `IHU689G_TEST_MATRIX.csv`
- `AVAS_ADAS_HARDWARE_TESTS.csv`
- `LIGHTING_HARDWARE_TESTS.csv`
- `AVAS_ADAS_HARDWARE_EVIDENCE.md`
- `probe-avas-adas-evidence.ps1`
- `probe-scene-contract.ps1`

`SHA256.txt` verifies the delivered APK. `BUILD_INFO.txt` pins the hardware-test branch commit and `source_main_sha`. `ASSET_SHA256.txt` plus the individual `*_sha256` fields bind the packaged matrices/guides/probes to the same artifact. These hashes prove provenance only; they do not prove the feature works on IHU689G.

TTS remains BLOCKED until verified production model sources are supplied. Do not silently substitute another voice or download an unverified model.

## Runtime gate
- [ ] Activation cannot be bypassed after cold start.
- [ ] ECARX voice button reaches VoiceSession.
- [ ] The selected wake profile releases KWS mic ownership before ASR acquires it; inactive phrase profiles do not wake.
- [ ] ASR 65M runs within acceptable CPU/RAM/RTF on IHU689G.
- [ ] 20/22/`hai hai` degree commands never normalize to HI or an incorrect numeric value.
- [ ] SafetyGate remains fail-closed when required vehicle state is unavailable.
- [ ] HVAC write/read-back is classified correctly.
- [ ] Window write/read-back is classified correctly.
- [ ] Media and navigation launch only the selected target app.
- [ ] Overlay is usable at 1920x1080 and returns to KWS after result/error.
- [ ] AVAS API/privilege/get-after-set behavior is confirmed on the target build before any production routing.
- [ ] ADAS remains read-only unless a separate safety design and hardware validation explicitly approves an actuator path.
- [ ] Scene provider raw state mapping is confirmed by stable OEM-UI before/after evidence before any actuator mapping.
- [ ] Lighting focused cases match physical/OEM UI behavior and independent read-back.

## Evidence rule
A command is hardware PASS only when observed vehicle/app behavior and corresponding read-back/log evidence agree. SET accepted alone is never PASS. Do not add guessed Property IDs, Area IDs, tolerances, aliases, or safety-system actuator mappings to force a green result.

Use the `IHU689G_TEST_MATRIX.csv` that is inside the exact hardware package being tested. Do not substitute a newer repo-local matrix while reviewing an older vehicle bundle.

## Required bundle
Run `scripts/run-ihu689g-test.ps1` with `-ZipBundle`, then analyze with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\review-ihu689g-bundle.ps1 -BundlePath <bundle.zip>
```

The individual analyzers remain available for focused review. Keep the package provenance files with the private test bundle.

Do not upload a real vehicle bundle to a public issue/repository because it can contain VIN, serials, package/service state, or other vehicle identifiers.

## Green checkpoint
Only after all required hardware cases are PASS (or explicitly documented non-blocking items) create `backup/phase10-hardware-green`. Until then, use incremental `backup/phase10-*` checkpoints only.
