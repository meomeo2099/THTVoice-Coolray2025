# Phase 10 — Chạy validation IHU689G từ hardware package

Runner Phase 10 không cài APK rời. Đầu vào chuẩn là artifact `THTVoice-IHU689G-hardware-test` tải từ workflow cùng tên.

## 1. Preflight trước khi nối xe

Có thể dùng ZIP tải trực tiếp từ GitHub Actions:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -PreflightOnly
```

PASS nghĩa là runner đã:
- tìm đủ APK + `SHA256.txt` + `BUILD_INFO.txt` + `ASSET_SHA256.txt`;
- chạy `verify-hardware-package.ps1`;
- xác minh APK/hash/provenance/ASR backend, metadata có đủ ba wake profiles và asset manifest có hash của cả ba keyword files;
- chọn đúng APK bên trong package.

Preflight-only không cần ADB và không cài gì lên xe.

## 2. Cài và chạy phiên validation

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -DurationSeconds 300 `
  -ZipBundle
```

Luồng:

```text
Hardware package
  -> provenance preflight
  -> adb install -r đúng APK đã verify
  -> pm path xác nhận package
  -> force-stop + cold start ActivationActivity
  -> người test kiểm tra activation/UI
  -> collector
  -> validation bundle
```

## 3. APK đã cài sẵn

Nếu không muốn cài lại nhưng vẫn muốn bundle mang provenance của hardware package đang đối chiếu:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -SkipInstall `
  -DurationSeconds 300 `
  -ZipBundle
```

Chỉ dùng `-SkipInstall` khi bạn chắc APK đang cài đúng với package đang truyền vào. Nếu không chắc, cài lại bằng luồng chuẩn.

## 4. Provenance trong validation bundle

Khi runner nhận hardware package, collector chép ba file sau vào:

```text
hardware-package-provenance/
  SHA256.txt
  BUILD_INFO.txt
  ASSET_SHA256.txt
```

Không copy APK vào bundle để tránh phình dung lượng; `BUILD_INFO.txt` đã chứa `apk_sha256` và kích thước APK để đối chiếu.

Điều này cho phép bundle test truy ngược chính xác commit/runtime/model/matrix/APK đã dùng mà không dựa vào tên file thủ công.

## 5. Quy tắc

- Không dùng lại tham số `-ApkPath` cũ.
- Không cài APK rời nếu chưa qua preflight.
- Không trộn metadata của run khác.
- Không upload validation bundle công khai vì log có thể chứa VIN/serial/identifier.
- Hardware PASS vẫn cần hành vi xe thật + read-back/log; provenance đúng không đồng nghĩa actuator đã được xác nhận.
