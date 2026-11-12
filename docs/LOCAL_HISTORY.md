# Local command and API history

Implemented for the Coolray 2025 candidate after 1.0.9. Uses Android's built-in SQLite (`tht_voice_history.db`, schema version 1) in app-private storage. No new runtime dependency, network service, or root requirement.

- `command_history`: execution UUID, normalized supported command, typed outcome, formatted result and start timestamp. Every execution, including individual scenario steps, is separate. The coordinator records the result before TTS; an audio failure cannot replace the actuator result. Unparsed speech and safety clarification prompts are not execution records.
- `api_diagnostics`: UUID, timestamp and bounded API diagnostic message. Existing ECARX/VHAL result/error instrumentation feeds this table. Dates distinguish events from earlier sessions.
- On startup, old `PENDING` records become `INTERRUPTED` / “Phiên trước đã dừng; chưa xác nhận kết quả.” Stored requests are never replayed. Cached historical results do not restore engine readiness, live vehicle connection, motion, or actuator state.
- One background worker opens/reads/writes SQLite. The submission queue is bounded to 128 tasks with nonblocking rejection; SQLite/disk/queue failure leaves live command execution running and is reported as `ERROR:<class>` or `QUEUE_FULL` in vehicle diagnostics. Writes still waiting in memory can be lost on abrupt power loss; a completed SQLite transaction survives a normal restart. This is diagnostic history, not a guaranteed-delivery event bus.
- Retention: latest 1,000 executions and 2,000 API events. UI caches six executions and sixteen diagnostics; current screens show five/three executions. Completion updates use UUID independently of the display cache. Startup merging preserves newly recorded rows over disk rows with the same ID.
- No microphone recordings or license credentials are collected. Text is bounded, newlines collapsed and VIN-shaped identifiers redacted. Settings/default apps remain in their existing preferences. Source-defined vehicle mappings remain version-controlled constants, not a writable raw-ID command database.
- Clearing application data/uninstalling removes this database. No destructive migration fallback is used; an unknown future schema upgrade reports a storage error while voice can continue.

## Verification

`scripts/test-history-database.py` executes the literal production SQL against real SQLite, including close/reopen, interrupted rows, distinct repeated commands, typed completion updates, ordering and retention. JVM tests cover queue/disk failure, startup merge, cache eviction, independent repeated commands, no readiness restore, history observer isolation and archive-before-TTS ordering. Android hardware persistence/performance remains unverified until installed on the head unit.

This change preserves evidence; it does not itself fix a rejected ECARX actuator write or make database storage faster than RAM.
