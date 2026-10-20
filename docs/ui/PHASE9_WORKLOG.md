# Phase 9 — Android UI implementation worklog

Status: IN PROGRESS

Baseline: `docs/UI_BASELINE.md` + `docs/ui/POPUP_FINAL.md`.

## 9.1 Navigation shell
- [x] Exactly 6 main menu items.
- [x] Persistent left navigation rail.
- [x] Landscape-first content area.
- [x] Preserve activation/update/voice runtime actions.
- [x] No parser/SafetyGate/VHAL behavior changes.
- [x] CI green on feature and merged `main`.

## 9.2 Settings screen
- [x] Move active settings from modal entry point into dedicated Settings screen.
- [x] Keep existing voice/media/navigation preference behavior.
- [x] Two-column landscape layout without a Settings `ScrollView`.
- [x] Save current Voice / Media / Navigation values.
- [x] Restore approved defaults: Hey THT on, voice feedback on, normal mic, 700 ms endpoint silence, 12 s max listen, Ngoc Huyen voice, media AUTO, navigation AUTO.
- [x] Notify `VoiceAssistantService.ACTION_SETTINGS_CHANGED` after save/restore.
- [x] Popup preferences (enabled / position / auto-hide / draggable) wired to the overlay controller.
- [ ] Hardware-validate no-scroll layout on IHU689G 1920×1080.

## 9.3 Compact external popup
- [x] Implement against `POPUP_FINAL_APPROVED.jpg`.
- [x] Wire popup enabled / position / auto-hide / draggable settings to the real overlay controller.
- [x] Keep LISTENING / PROCESSING / SUCCESS / ERROR state mapping.
- [x] Persist dragged position only when draggable setting is enabled.
- [ ] Verify popup does not block critical navigation/media controls.

## 9.5 UI gaps vs baseline (29/09/2026)
- [x] Settings: live popup preview replaces the static note.
- [x] Assistant quick commands: added "Xe" (5 groups as in UI_BASELINE §4).
- [x] About: changelog and quick guide describe current features; no car-brand wording.

## 9.4 Responsive layout (29/09/2026)
- [x] `ResponsiveLayout` / `ResponsiveContext`: UI scale toward 1920×1080 (min 72%), rail width / icon-only rail, 1-column settings, 1–3 app columns, scroll on short or narrow screens, clamped fixed blocks; unchanged at 1920×1080.
- [x] JVM tests for 1920×1080, 2000×1200, 1280×720, 1024×600, 1920×720, portrait 1080×1920, 640×360.
- [ ] Visual check on real head units / emulator at those sizes.

Hardware validation remains required before UI stable label.
