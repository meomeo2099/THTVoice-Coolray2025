# ASR runtime recovery candidate

> **For agentic workers:** Use superpowers:executing-plans; one final independent review.

**Goal:** Supply an install-over recovery candidate to isolate the new speech runtime after the owner reports a listening freeze/process exit.

**Evidence:** The owner reports 1.0.11 hangs after speaking a command, through both the Voice button and Hey Geely. Photos show listening with no recognized text, plus VHAL CONNECTED/PROPERTY_MANAGER_READY, Database READY and granted car permissions. The API event list in the photo contains older timestamps and is not a crash stack. No native crash/tombstone/ANR log is supplied.

**Hypothesis:** Sherpa2.0.1/native speech processing is a candidate regression. The 1.0.10-to-1.0.11 comparison has no ASR adapter, microphone, voice service, watchdog or model-weight source change; the speech runtime changed1.13.8->2.0.1. Text normalization and vehicle commands also changed but are downstream of speech decoding. Neither packaging checks nor JVM tests prove native decoding on this head unit.

**Architecture:** Controlled rollback of the complete Sherpa AAR/metadata/probe pin to1.13.8 using its prior verified SHA256. Keep 1.0.11 feature code, ASR adapters, mic settings, modelweights, database and actuator behavior byte-for-byte. Increase app version to1.0.12(13), retain package/signature so the owner can install over1.0.11. The comparison changes one runtime variable and does not claim the root cause or hardware recovery is confirmed.

## Tasks
- [x] Verify diff and restore coherent runtime pins/checksum from the previous release.
- [x] Independently review the scoped rollback and run archive/API/ABI/minSdk checks plus full Android tests and signed build in CI.
- [x] Record artifact and Git recovery points, restore manual workflow, report how to compare on the car.

## Constraints
- No generic git revert/reset, schema downgrade, settings wipe, model replacement or new microphone tuning.
- Keep the existing runtime archive verifier; it reads the pinned version and checksum dynamically.
- No root/firmware/OTA publication or installation on the vehicle.
- If runtime rollback still fails on hardware, collect crash/native/ANR evidence before claiming a specific native failure mode. Screenshots alone cannot distinguish native crash, OOM, Java crash, ANR or OEM process termination.

## Ledger
- Restored SHA from Git commit00f03cb9, not a guessed download. Runtime/probe/metadata fixture pins now consistently1.13.8.
- `git diff -- app/src/main/java app/src/test/java app/src/main/assets scripts/fetch-gipformer65m-int8.sh` is empty: feature implementation, ASR/mic adapters, tests and model staging unchanged. Database schema unchanged.

- Independent fresh review found no material issue: active pins/checksum coherent; source/assets unchanged; no causal/hardware-fix claim. Real AAR/ABI/API validation pending CI.

## Candidate verification
- Buildcommit8b7138f41e0c03351077c1498b29d437d9f3b904, Actionsrun43156229526. Attempt1 ended before jobsteps with no downloadable logs; authorized failed-job rerun started normally. Attempt2/job131025768346 passed runtime archive/API/ABI/minSdk verification, SQLite checks, complete Androidunit tests, signed release assembly and certificate/package/version verification.
- Sherpa1.13.8 archiveSHA633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96, minSdk21 and both ARM JNI architectures verified.
- Candidate1.0.12(13), packagecom.tht.voice.coolray2025. APKSHA2566015cc451f3762afc85b30c37f8f24bbfafb8f8063f656305236f29c7a1cc9fd. ArtifactTHTVoice-rc-1.0.12-13/13922158111. OTApublication skipped.
- Retain testbranch/Git recovery points; workflow restored manual-only. Hardware comparison and exact crashcause remain unverified until the owner tests this APK or supplies crashlogs. No claim that the fault is definitively fixed.
