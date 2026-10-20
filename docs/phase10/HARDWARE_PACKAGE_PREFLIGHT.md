# Phase 10 — Hardware package preflight

Mục tiêu: trước khi cài APK lên IHU689G, xác minh APK và metadata từ Actions artifact hoặc private rolling prerelease cùng thuộc một hardware-test run và chưa bị trộn/thay đổi.

## Gói bắt buộc

Package `THTVoice-IHU689G-hardware-test` từ Actions hoặc prerelease fallback phải chứa đủ:

```text
THTVoice-IHU689G-hardware-test.apk
SHA256.txt
BUILD_INFO.txt
ASSET_SHA256.txt
```

Không copy riêng APK từ run khác vào thư mục này.

## Kiểm tra trên Windows

Nếu đã giải nén artifact:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-hardware-package.ps1 `
  -PackagePath .\THTVoice-IHU689G-hardware-test `
  -ExpectedGitSha <commit_sha_cua_run> `
  -ExpectedSourceMainSha <source_main_sha_trong_BUILD_INFO>
```

Nếu giữ nguyên file ZIP artifact:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-hardware-package.ps1 `
  -PackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_run> `
  -ExpectedSourceMainSha <source_main_sha_trong_BUILD_INFO>
```

## Verifier kiểm tra

- đủ 4 file bắt buộc;
- SHA-256 APK khớp `SHA256.txt`;
- `apk_sha256` và `apk_size_bytes` khớp `BUILD_INFO.txt`;
- SHA-256 của `ASSET_SHA256.txt` khớp `asset_manifest_sha256`;
- `git_sha` và `source_main_sha` khớp commit dự kiến nếu truyền `-ExpectedGitSha` / `-ExpectedSourceMainSha`;
- `test_matrix_sha256` trong `BUILD_INFO.txt` khớp hash matrix trong asset manifest;
- ASR backend là `offline-transducer`;
- wake phrase mặc định là `Hey THT`;
- `BUILD_INFO.txt` khai báo đủ profiles `Hey THT|Hello Geely|Hey Geely`;
- asset manifest có hash của `keywords.txt`, `keywords-hello-geely.txt` và `keywords-hey-geely.txt`, ngoài Sherpa runtime, ASR 65M INT8 và `IHU689G_TEST_MATRIX.csv`.

Nếu bất kỳ mục nào sai, verifier trả lỗi và **không dùng APK đó để test xe**.

## Trong GitHub Actions

Workflow `IHU689G hardware test APK` chạy verifier sau khi tạo metadata trước bước giao package. Cùng một verifier áp dụng cho Actions artifact và ZIP trong private rolling prerelease. Chỉ dùng package khi `git_sha` và `source_main_sha` khớp build dự kiến.

## TTS

`tts_voice_assets=not_staged` không làm preflight fail ở Phase 10 hiện tại vì production voice assets Ngọc Huyền / Diễm Trinh vẫn là blocker riêng. Không được đổi trạng thái này thành READY nếu chưa có model pinned/licensed và đủ ONNX + `tokens.txt` + `espeak-ng-data`.
