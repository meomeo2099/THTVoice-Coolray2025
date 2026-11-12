# Owner window, shade and ASR corrections

> **For agentic workers:** Use superpowers:executing-plans; fresh final review.

**Goal:** Correct the owner's 1.0.10 reports without changing vehicle mappings or ASR engines.

**Architecture:** Contextual normalization and fail-closed window target parsing. Explicit shade commands must reach the MCU even if an untrusted pre-read already equals the requested position; unchanged target-matching reads cannot prove shade motion.

**Spec:** Owner reports “hạ kính sau bên lá” moves both rear windows; “quạt” becomes “hoạt”; close shade says completed while hardware does not move. Shade opening works.

## Tasks
- [x] Reproduce each case with failing parser/executor tests, including unrelated “hoạt động” and ambiguous window-side negatives.
- [x] Correct contextual fan/driver aliases, prevent side loss from broadening the command, and fix false shade confirmation/pre-read no-op.
- [ ] Run regressions, independent review and signed candidate CI; document live hardware limits.

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
