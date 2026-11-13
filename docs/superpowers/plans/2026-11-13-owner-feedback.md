# Owner feedback: demist words, uncertain window row and AUTO diagnostics

> For agentic workers: execute inline with superpowers:executing-plans; obtain a fresh final review.

Goal: Correct reported sai/sấy and handle lost sau/xe without selecting the wrong window. Preserve working sunshade close, runtime1.13.8 and voice lifecycle. Add bounded AUTO read-back diagnostics and the owner-evidenced manual-fan fallback described below. Correct VHAL connection ownership.

Spec: Owner reports AUTO on works, off remains sent/unconfirmed; sai replaces sấy; sau becomes xe in đóng kính xe bên lái. Sunshade closes successfully;10 button activations did not freeze. These are owner-reported hardware results, not universal verification.

Evidence: OEM reference CarClimat uses AUTO1/0; current executor agrees. XCHvac.apk contains only R.java and its executable code is in compact DEX inside XCHvac.vdex; ordinary JADX APK output cannot establish original AUTO button logic. REA provider is unconfigured; standalone JADX exists. No new raw IDs or guessed zone/value retries.

- [x] Add failing tests for sai kính, uncertain row/no actuator, clarification side/percent and read-back diagnostics.
- [x] Implement contextual normalization, guarded clarification and diagnostic records; keep unknown/rejected results honest.
- [x] Run regressions, independent review, commit backup and signed test candidate; record unresolved AUTO root cause and hardware test steps.

Files: VoiceTextNormalizer, VoiceCommandParser, WindowClarification, EcarxCommandSupport and focused owner regression test. No model/mic tuning, sunshade change, raw-ID guessing or OTA publication. The only added actuator request is the bounded same-level manual-fan fallback described below.

Verification ledger: RED185tests/4failures reproduced missing sai normalization, unsafe front fallback for damaged row, missing clarification and absent exact AUTO mismatch evidence. GREEN185 tests in build/owner114-green.log. Owner screenshots later show1.0.13/code14, current VHAL CONNECTED/PROPERTY_MANAGER_READY and Database READY with archived13/11 logs. Visible entries include fan0x10020100, front defrost0x10040100 and MCU window/shade; no AUTO0x10010200 entry is visible, so OFF root cause remains unresolved. Old CONNECT timeout records are not current connection status.

Fresh independent final review: no actionable material findings. Preparing1.0.14/code15 signed diagnostic/language candidate; AUTO OFF actuator path is unchanged and must not be described as fixed.

Later owner hypothesis: changing manual fan may exit AUTO; not confirmed yet. New photo shows current VHAL DISCONNECTED with archived logs, no AUTO write entry. Asked for one manual OEM-interface experiment (AUTOon then fan+1); do not infer that the OFF API must be replaced until evidence arrives.

Scope extension: owner confirmed OEM AUTO indicator extinguishes when fan increased one step. Add one bounded manual-fan fallback after accepted/unconfirmed OFF only when live AUTO remains ON; request the existing valid fan level (no deliberate speed change), then verify AUTO OFF and fan read-back. Same-level override is REFERENCE/UNVERIFIED until tested. Never fallback after OFF denial/failure, unknown AUTO, unsupported/invalid fan.

Connection investigation found a source-level bug: reconnect creates/marks fresh VHAL connected, then old.disconnect unconditionally marks the global registry disconnected. Old API28 callbacks do the same. Bind telemetry writes to connection ownership so retired objects cannot overwrite fresh status; real active disconnect still appears. Label the home row VHAL rather than all car connectivity. Keep ECARX independent.
- [x] RED/GREEN manual-fan fallback and telemetry ownership tests, fresh review and rebuild candidate.

Extended verification: local API scaffolding initially did not compile (new owner argument absent). With signatures introduced but old behavior unchanged, RED198tests/3failures reproduced absent manual fallback and retired telemetry overwrite. GREEN199tests after behavior fixes and adding overlapping temporary-probe coverage, build/owner114-manual-green.log. MainActivity update/notice safety checks also open then disconnect temporary Car objects; owner aggregation protects the still-live service connection.

Initial language/diagnostic candidate1.0.14/code15 source045566c62b040ca24f8937d7798b47b18054d349 passed signedCI43370705915 attempt3 (attempts1/2failed before steps). Owner made repo public before attempt3 ran. It is superseded by the extended1.0.15/code16 candidate, retaining manual-fan behavior as REFERENCE/UNVERIFIED until same-level hardware test.

Final review found/fixedP2: transient API28 service loss must retain ownership rather than retire it. Added markServiceDisconnected and recovery/stale-callback test: RED4/1 then GREEN4. Full final focused regression GREEN200tests, build/owner114-final-green.log. Final reviewer recheck found no remaining material issues. Extended signed candidate1.0.15/code16 pending.

Final cancellation regression: interrupted primary OFF verification previously still started manual fan fallback. RED5tests/1failure; added interrupt guards at fallback entry and immediately before actuator write; GREEN5 plus final GREEN201 focused tests (build/owner114-final2-green.log). Reviewer recheck found no actionable issue. 1.0.15/code16/source8f0ba7c1470a9bf3f078e7500345b5c586ffdec6 passed signedCI43372443683 but is superseded by final1.0.16/code17 with cancellation fix.

Final owner decision supersedes same-level design: when direct OFF remains accepted/unconfirmed and live AUTO isON, request fan current+1 capped7 (never decrease or write8), then require AUTO OFF and requested fan read-back. Owner explicitly requested this after confirming OEM fan+1 extinguishes AUTO. Tests RED5/1 thenGREEN6 with3→4andmax7, finalGREEN202focusedtests (build/owner114-owner-final-green.log). Reviewer found no actionable issues. In-progress preliminary1.0.16/code17 has not assembled an APK; superseding it through temporarycancel-in-progress=true, then restore manual workflow in cleanup.

## Final delivered behavior and verification
- AUTO OFF: retain canonical OFF request; if accepted/unconfirmed and liveAUTO remainsON, request exactly one manual fan step current+1, capped7. Positive support and valid current level required. Denied/failed/unknown requests and interrupted sessions do not start the fallback. Only AUTO0 plus matching requested fan read-back confirms completion. User confirmed physical OEM fan+1 extinguishes AUTO; this exact app sequence still needs a vehicle test.
- VHAL status is per live connection. Closing an update/notice safety probe or retiring an old reconnect wrapper cannot overwrite another live connection. Transient service loss retains ownership; actual successful I/O restores health. Explicitly retired wrappers cannot revive through stale callbacks/I/O. Home label now says Kết nối VHAL, independently of ECARX controls.
- sai/sấy is normalized only in demist context. A damaged kính xe bên lái request asks front/rear, preserves side/percentage and never defaults to the wrong front window. Sunshade and Sherpa1.13.8/model/mic paths unchanged.
- Final local regression202tests passed. Fresh review P2 was fixed and rechecked; cancellation and owner-directed fan-step changes also rechecked with no remaining actionable findings.
- Signed1.0.16/code17: source257b0aa0c80a1cb3e57a933a3f51debbc6947bfa, CI43373563761/job131714819143 passed runtime integrity, SQLite, full Android tests, release assembly and THT_SIGN certificate/package/version verification.
- Artifact14013558336 THTVoice-rc-1.0.16-17: https://github.com/meomeo2099/THTVoice-Coolray2025/actions/runs/43373563761/artifacts/14013558336 . Expires2026-11-14T05:46:25Z. APK SHA256 f1ef53f33e55b15dce15defdb1a91172b354c9bb45a910cc6d673593dea67e40 . Artifact ZIP digest cf1eca2c9d1b0c636a3576e5d354bc04d4e81bb05e31dbe60fdaf41eeee62ac7 .
- Preliminary same-level1.0.16 run43373206273 was cancelled before APK assembly/upload and has no artifact. Final run above contains the owner's +1 decision. Manual-only workflow restored; temporary push trigger/concurrency override removed. No OTA published.
- Vehicle retest: install over1.0.13; AUTOon then AUTOoff, observeAUTO and one fan step; sai kính; damaged rear-driver phrase/row reply; reopen app/wait after update check and observe VHAL status. Vehicle can remain off during software builds.
