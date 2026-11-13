# Owner feedback: demist words, uncertain window row and AUTO diagnostics

> For agentic workers: execute inline with superpowers:executing-plans; obtain a fresh final review.

Goal: Correct reported sai/sấy and handle lost sau/xe without selecting the wrong window. Preserve working sunshade close, runtime1.13.8 and voice lifecycle. Add bounded AUTO read-back diagnostics; do not invent an OFF workaround.

Spec: Owner reports AUTO on works, off remains sent/unconfirmed; sai replaces sấy; sau becomes xe in đóng kính xe bên lái. Sunshade closes successfully;10 button activations did not freeze. These are owner-reported hardware results, not universal verification.

Evidence: OEM reference CarClimat uses AUTO1/0; current executor agrees. XCHvac.apk contains only R.java and its executable code is in compact DEX inside XCHvac.vdex; ordinary JADX APK output cannot establish original AUTO button logic. REA provider is unconfigured; standalone JADX exists. No new raw IDs or guessed zone/value retries.

- [x] Add failing tests for sai kính, uncertain row/no actuator, clarification side/percent and read-back diagnostics.
- [x] Implement contextual normalization, guarded clarification and diagnostic records; keep unknown/rejected results honest.
- [ ] Run regressions, independent review, commit backup and signed test candidate; record unresolved AUTO root cause and hardware test steps.

Files: VoiceTextNormalizer, VoiceCommandParser, WindowClarification, EcarxCommandSupport and focused owner regression test. No model/mic tuning, sunshade change, extra actuator writes or OTA publication.

Verification ledger: RED185tests/4failures reproduced missing sai normalization, unsafe front fallback for damaged row, missing clarification and absent exact AUTO mismatch evidence. GREEN185 tests in build/owner114-green.log. Owner screenshots later show1.0.13/code14, current VHAL CONNECTED/PROPERTY_MANAGER_READY and Database READY with archived13/11 logs. Visible entries include fan0x10020100, front defrost0x10040100 and MCU window/shade; no AUTO0x10010200 entry is visible, so OFF root cause remains unresolved. Old CONNECT timeout records are not current connection status.

Fresh independent final review: no actionable material findings. Preparing1.0.14/code15 signed diagnostic/language candidate; AUTO OFF actuator path is unchanged and must not be described as fixed.
