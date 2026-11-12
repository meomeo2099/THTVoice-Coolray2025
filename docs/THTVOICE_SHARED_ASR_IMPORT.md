# Shared THTVoice language corrections

Source: owner repository [THTVoice at 424a7b959fdc6857d642ff1701dd699b2ea4e3f3](https://github.com/meomeo2099/THTVoice/tree/424a7b959fdc6857d642ff1701dd699b2ea4e3f3).

Compared `VoiceTextNormalizer.kt`, `OwnerAsrSlipRegressionTest.kt`, `Ex2CommandAliasRegressionTest.kt`, `Ref16RegionalSlipsTest.kt` and `docs/reference/voice/IHU629G_VOICE_PHRASES.csv` with this project. Both reference CSVs contain 3,230 rows and every source phrase already exists in `docs/reference/voice/VOICE_PHRASES.csv`; duplicating the vehicle-specific file adds no new phrase.

Transferred compatible newer corrections: contextual “quảng” -> “quạt”, “xây kinh/kính” -> “sấy kính”, “khe kính” -> “hé kính”, “chế độ lái sơn” -> Sport, and “cửa sổ” -> door-window noun while preserving sunroof/freeform contexts. Added scoped rest/camping pronunciation variants to the existing open-OEM-screen command. They do not activate a scene or add a vehicle API route.

The shared 20-case regression CSV is `docs/reference/voice/THTVOICE_SHARED_ASR_CASES.csv`; `ThtVoiceSharedAsrTest` compares every phrase against a supported canonical typed command. Negative checks preserve unrelated advertising, construction, navigation destinations and normal “hoạt động”. The source's broad corrections have been narrowed where necessary to avoid turning unrelated utterances into actuator commands.

Battery/EV/regen semantics and the source's timed HVAC/ambient scheduling features are not imported as new vehicle functions. Coolray temperature steps remain 0.5°C, fan limits remain the vehicle's 1..7, and existing backend/read-back safety stays in this repository.

During integration, the new unresolved-side guard initially rejected sixteen valid “hàng ghế trước/sau” phrases. The existing full corpus check caught this (2,681 versus baseline2,697). Recognized row labels are now excluded from the damaged-side check; row-label regressions and full coverage pass locally. The original coverage threshold is unchanged.
