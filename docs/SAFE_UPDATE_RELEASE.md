# THT Voice safe update / release flow

## Runtime flow on the car

0. **No OTA while driving** (owner decision 29/09/2026): before contacting the Worker, `OtaParkedPolicy` requires gear P and speed below 1 km/h; missing SPEED/GEAR blocks. During download the parked state is re-checked about every 1 MiB and the download stops (temporary file deleted) if the car is no longer parked.
1. `GET /api/update?current_code=<installedVersionCode>` through the configured Cloudflare Worker.
2. The app sends the existing license headers (`X-Geely-Request`, `X-Geely-License`).
3. The Worker reads the latest private GitHub Release server-side. The car never receives a GitHub PAT.
4. The Worker returns `versionCode`, `versionName`, `sha256`, `downloadUrl`, changelog and size.
5. THT Voice accepts only HTTPS download URLs on the configured Worker origin and `/api/update/...` path.
6. The APK is downloaded to `cache/updates/*.apk.part`, with a 512 MB hard limit.
7. SHA-256 is checked before `.part` is atomically renamed to `.apk`.
8. `UpdatePackageVerifier` then checks the APK again: SHA-256, package name, exact metadata versionCode, newer-than-installed versionCode and signing lineage.
9. `ParkedActionGate` requires live IHU689G telemetry: gear P and speed below 1 km/h. Missing SPEED/GEAR fails closed.
10. If the car is not safely parked, the verified APK stays deferred in app-private storage. Every retry re-verifies both APK and vehicle state.
11. When allowed, Android's normal Package Installer is opened through FileProvider. THT Voice does not perform root/silent installation.

## Cloudflare Worker

The update endpoints are served by the license Worker in `worker/` (`worker/src/update.js`, deploy guide `worker/README.md`). Protocol:

- `/api/update`
- `/api/update/apk`
- private GitHub access only from the Worker
- `update.json` in the latest GitHub Release

For THT Voice set the Worker environment variable:

```text
GITHUB_REPO=meomeo2099/THTVoice
```

Keep `GITHUB_TOKEN`, license secrets and admin secrets only in Cloudflare secrets/variables. Never put them in the Android app or repository.

## Required `update.json`

```json
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "apk": "THTVoice-1.0.1.apk",
  "sha256": "64_HEX_CHARACTERS",
  "forceUpdate": false,
  "changelog": "..."
}
```

`versionCode` must strictly increase for every customer update.

## Stable Android signing is mandatory

Android only installs an update over an existing package when it is signed by the accepted signing lineage. Do not ship customer builds with throwaway CI debug keys.

`app/build.gradle.kts` accepts these environment variables for release builds:

```text
THTVOICE_KEYSTORE_FILE
THTVOICE_STORE_PASSWORD
THTVOICE_KEY_ALIAS
THTVOICE_KEY_PASSWORD
THTVOICE_VERSION_CODE
THTVOICE_VERSION_NAME
```

The keystore itself and passwords must be stored outside Git (for CI, use GitHub Actions Secrets). Back up the release keystore securely: losing it can prevent future in-place updates to installed cars.

## Full-offline release gate

Do not publish a customer Release merely because `assembleRelease` succeeds. A release called full-offline must also contain the verified runtime assets required by that build, including the selected Sherpa ASR model and actual TTS voice model(s). The current model staging scripts and tests remain the gate for those assets.

## Rollback policy

Git rollback and Android app downgrade are different things:

- Git backup branches can restore source instantly.
- Android normally rejects installing a lower `versionCode` over a newer installed version.
- To ship a code rollback to customers, rebuild the older-good source using a *higher* new versionCode and the same release signing key.

## Phase 10: chuẩn bị release khi chưa có xe (dự thảo)

- Khóa nguồn: lưu commit/tag source đã kiểm thử, APK SHA-256, manifest asset hash, versionCode/versionName, matrix và kết quả CI. Git backup là anchor của source; không coi backup source là bản APK khách hàng.
- Khóa signing: chọn và sao lưu keystore release ngoài Git; chỉ pipeline được cấp secrets mới ký bản release. Ghi fingerprint chứng chỉ và kiểm tra chữ ký của APK trước khi đưa vào Worker. Debug key của CI không được dùng cho nâng cấp customer.
- Kiểm tra update trên PC/test Android trước: metadata ↔ APK hash/package/versionCode/signing lineage phải khớp; thử SHA sai, gói sai, version không tăng và chữ ký khác đều bị từ chối. Thử tải gián đoạn rồi chạy lại và deferred update với safety telemetry thiếu; trạng thái thiếu phải fail-closed.
- Rollback candidate: lưu source, asset manifest và APK release đã ký của bản tốt gần nhất. Nếu cần khôi phục code, build source tốt bằng cùng lineage ký với versionCode **mới cao hơn** bản đã cài, rồi kiểm tra lại hash và gate; không cài đè APK cũ với versionCode thấp.
- Trên IHU689G sau này: xác minh gear P/speed thực trước install, Android Package Installer, khởi động lại, activation và các command theo matrix. Nếu fail, dừng phát hành và điều tra bundle/log; không suy ra từ emulator.
- VIN-required binding chỉ được chốt sau khi `INFO_VIN` ổn định trên xe và policy re-bind/uninstall được thử. Production TTS còn chờ model pinned/licensed. Không tạo stable/customer release hay `backup/phase10-hardware-green` ở bước chuẩn bị này.
