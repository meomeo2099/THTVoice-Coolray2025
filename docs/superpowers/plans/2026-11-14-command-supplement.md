# Shared command-language supplement

> Execute inline with superpowers:executing-plans; one fresh-context final review.

Goal: import useful missing utterances from owner THTVoice into Coolray2025 existing typed commands, without importing hardware mappings, optional AI, TTS format migration or new actuator families.
Spec: owner requested auditing commands and supplementing this app. Source THTVoice0c48c6e17a7bb37bca636472593f2ee915392bce, baselineCoolray7aecfccfdacc51ba916a9221d1d21b281d1f1468. Existing3230-row corpus already includes all upstream phrases; adding duplicate CSV is unnecessary. Parser/test-file comparison found78 exact-shared files and differing reference/platform features. Initial corpus execution:2473 parsed into enabled families/local commands,257 disabled families,500 unparsed. These counts are parser/capability coverage, not hardware confirmation; unparsed includes intended questions, context-only phrases and obsolete/ambiguous corpus labels.

Design: add whole-utterance natural aliases for supported climate, media/apps and read-only queries; apply Vietmap ASR corrections only in app-name slots before generic map selection; preserve destination text. Preserve0.5°C defaults,16..32°C/1..7 fan, current OEM controller/AUTO fallback and runtime1.13.8. Expand existing crack-window grammar and missing-target clarification; no guessed side/row.
Files: SharedCommandLanguage (whole-request alias table), NaturalPhraseAliasParser, InstalledAppMatcher, VoiceCommandParser, SpokenTerms, WindowClarification; one reference TSV and focused test.
- [x] RED focused tests from upstream CSV/tests plus ambiguity/name/capability guards.
- [x] Implement supported aliases and run full corpus/regressions; audit before/after counts.
- [ ] Fresh final review, signed candidate and Git backup with manual workflow restored.
Exclusions: charging/regen/front trunk/door-lock/heating/unknown vehicle apps; Wi-Fi toggle is intentionally distinct from OpenWifiSettings; context-only follow-ups use existing FollowUpContext; timer/schedule/split recovery need separate feature handlers and are not mere aliases. Do not copy ambiguous ASR examples anh nhiệt độ/cảm giác độ/tắt âm hơn as actuator requests. Generic temperature/fan endpoints retain owner-specific semantics rather than source17°C/fan8.

Ledger: initial RED fixture included a nonexistent InstalledAppMatcherTest filename; corrected before recording RED206tests/3actual assertion failures. GREEN206tests then repeated after added real parse/app-slot guards, build/upstream-command-final-green.log. After corpus audit2553enabled/local,257disabled,420unparsed (80more parsed supported samples). Source intent/params comparison also confirmed0differences across3230rows. Fresh independent review found no actionable issues. Full report docs/THTVOICE_COMMAND_AUDIT_2026-11-14.md. Signed1.0.17/code18 candidate pending.
