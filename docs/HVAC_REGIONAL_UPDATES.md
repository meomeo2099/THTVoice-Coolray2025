# Shared regional command understanding and HVAC

The owner's 12 November2026 requests are integrated into the existing voice pipeline, with no separate BinhDinh profile, switch or screen. Recognition still uses the same Vietnamese65M model and Sherpa1.13.8; these changes normalize supported command words after ASR, rather than retraining audio or claiming universal accent accuracy.

## Regional words

- Inside circulation: “lấy gió treo/chong/trông” -> “lấy gió trong”; outside pronunciation variants are normalized only in a complete circulation request.
- Qualified window positions recognize tài/tay, common lái/phụ and trái/phải variants, plus sau/seo/xau and trước/chước variants. These rules require an explicit window-command prefix. Long passenger forms such as “tay phụ” are resolved before driver forms. An unknown side is never expanded into both rear windows.
- Sấy/sái/sáy corrections require a kính/gương object; fan quạc/quặc corrections require a fan-command context. The older global quạc replacement was narrowed so app names remain intact.
- Ordinary app names/destinations are preserved. “Đóng kính lại” still needs a target; “lại” is not silently treated as the driver window. Existing Vietnamese-number normalization remains in place.
- AUTO accepts auto/aoto/aotu/ao tô/au tô/au tu/a tô and related bounded spellings. “Ô tô” needs explicit điều hòa or chế độ context; “bật ô tô” does not become HVAC AUTO. Auto Hold remains a separate command.

## HVAC behavior

Valid temperature commands first request system power then A/C, unless already on, then write the setpoint. Default relative change remains0.5°C; direct requested values outside16..32 or non-finite values are rejected before any activation. A refused prerequisite stops the temperature write. A completed temperature change requires matching live read-back of system power, A/C and temperature; unknown A/C remains AcceptedUnverified.

AUTO enable checks AUTO support first, requests system power, then sets AUTO. AUTO off does not enable system power or A/C. Unknown power state cannot be hidden by a matching AUTO value.

The canonical OEM reference `CarClimat` uses `getFunctionValue(id)` and `setFunctionValue(id,value)` for global functions. The AdaptAPI contract also exposes non-zoned `isFunctionSupported(id)`. The reflection bridge now prefers those non-zoned contracts when available. Facade GLOBAL0 is a sentinel; explicit window/seat zones retain zoned signatures. A rejected global write is never retried through another zone; older API shapes lacking non-zoned methods retain the existing zoned fallback. No new function/property IDs were introduced.

The owner had tested AUTO and inside/outside circulation; other climate modes were not reported as tested. No statement here upgrades reference mappings to hardware-confirmed behavior. Vehicle testing is still required for actual indicators and actuator response.

## Tests

`BinhDinhHvacTest`, `RegionalVehiclePhrasesTest` and `EcarxGlobalContractTest` cover regional positives/negatives, AUTO spellings, side/row/percentage preservation, global/zoned method selection, prerequisite order, refusals, invalid-input no side effects and partial read-back. Existing full phrase corpus and HVAC/vehicle regressions remain enabled.

## Signed test candidate

Version1.0.13/code14 passed local175-test regression and full Android CI [43193453866](https://github.com/meomeo2099/THTVoice-Coolray2025/actions/runs/43193453866). [Artifact13932675596](https://github.com/meomeo2099/THTVoice-Coolray2025/actions/runs/43193453866/artifacts/13932675596), expires2026-11-13T09:45:28Z. THT_SIGN release certificate/package/version verified. APK SHA256: `9237c5efb13cc7bdea5a3344b6218f12de92bf793928a2c66b6472e7a0c326bb`. Source commit: `278793f82908c2f75060fd155f523e2d72f5d793`. No OTA published.
