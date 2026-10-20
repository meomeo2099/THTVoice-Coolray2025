# Phase 10 — Hardware package delivery fallback

Hardware workflow luôn ưu tiên GitHub Actions artifact `THTVoice-IHU689G-hardware-test` với retention 3 ngày.

Nếu Actions artifact storage tạm hết quota, build **không cần chạy lại từ đầu chỉ vì delivery**. Sau khi APK + metadata đã build và `verify-hardware-package.ps1` PASS, workflow dùng fallback:

```text
hardware-test-output
  ↓
ZIP đúng 4 file
  ↓
rolling private prerelease: hardware-test-latest
```

Release fallback chỉ dùng trong private repo và được ghi đè, không tạo một release mới cho mỗi build.

## File fallback

Asset chính:

`THTVoice-IHU689G-hardware-test.zip`

ZIP chứa:

- `THTVoice-IHU689G-hardware-test.apk`
- `SHA256.txt`
- `BUILD_INFO.txt`
- `ASSET_SHA256.txt`

Release còn upload `BUILD_INFO.txt` riêng để xem nhanh provenance.

## Quy tắc tin cậy

`hardware-test-latest` là tag/release **rolling**, vì vậy không dùng tên tag để xác định build. Luôn dùng:

- `git_sha` trong `BUILD_INFO.txt`;
- SHA APK;
- asset manifest;
- `run-ihu689g-test.ps1 -ExpectedGitSha ...`.

Trước khi cài xe vẫn phải chạy package preflight.

## Khi artifact storage hồi phục

Nếu `actions/upload-artifact` thành công, fallback release không chạy. Workflow trở lại đường delivery chính tự động.

Fallback không thay đổi parser, VHAL, SafetyGate, model hay APK; nó chỉ thay kênh tải package sau khi package đã được verify.
