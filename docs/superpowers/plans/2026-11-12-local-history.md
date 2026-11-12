# Local history implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, with one final independent review.

**Goal:** Keep command outcomes and API diagnostics across app/car restarts without delaying control.

**Architecture:** Built-in SQLiteOpenHelper, one bounded background worker, small process-local display caches. Command coordinator records typed outcomes before TTS. Restored history never supplies actuator values or connection state.

**Tech stack:** Kotlin, Android 9 SQLite, JUnit, Python SQLite schema verification.

**Spec:** User approved local durable history with asynchronous writes in this chat.

## Constraints and review focus
- Preserve CONFIRMED versus ACCEPTED_UNVERIFIED and never replay stored commands.
- Each execution has a UUID; repeated text creates separate rows.
- Startup merges with commands arriving during database reads; interrupted pending rows remain unconfirmed.
- Database errors/queue saturation cannot block or fail vehicle control.
- Private database stores bounded text metadata, no audio/license secrets. Retain 1,000 commands and 2,000 diagnostic events.

## Tasks
- [x] Write failing tests for repeat commands, restart merge, errors, and typed outcomes before speech.
- [x] Implement journal/cache, SQLite schema/store, process startup and telemetry sink.
- [x] Connect coordinator and cached UI history; document limits and storage health.
- [x] Run local regression and actual SQLite restart/retention checks; review; build signed test APK.

## Ledger
- Existing isolated test branch retained; no changes to main or OTA publication.
- Ruling: archive parsed executions at the coordinator boundary, including each scenario step; unparsed speech and safety prompts are not recorded as completed commands. Avoid saving arbitrary conversation/audio.

- Verification: 124 focused JVM tests and real SQLite close/reopen/retention test passed locally. Android storage and adapter sources compiled against API 32 in the JVM harness.
- Fresh review found completion could be lost after display cache eviction. Reproduced failing regression, then fixed by UUID-based database UPDATE; regression now passes.

- Final reviewer verified the UUID completion fix and independently passed the SQL test; no remaining material finding.
- Signed candidate 1.0.10 (11): build commit bcd0a1bb2923a8f900f8f5e961a7a8cde4bda1af; Actions run 43139946635 / job 130924237173. Full Android unit tests, SQLite test, release assembly, certificate/package/version checks all passed. OTA publishing skipped.
- APK SHA256: 00e5a2981e7b605620ce4f595d4daa33267d5b67e2331dc5108dcbbbc39a0b94. Artifact THTVoice-rc-1.0.10-11, ID13909621319.
- Integration: retain existing test branch as authorized; restore workflow manual trigger while keeping permanent SQLite verification step. Hardware persistence/performance still unverified.
