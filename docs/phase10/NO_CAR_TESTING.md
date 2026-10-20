# Kiểm thử Phase 10 khi chưa có xe

Tài liệu này dùng cho PC, emulator/LDPlayer và điện thoại Android thường. Kết quả ở đây là bằng chứng phần mềm; không thay thế matrix IHU689G, cabin noise, VHAL, nút Voice hay quyền hệ thống trên head unit.

## 1. Chạy tự động trên PC

Yêu cầu: JDK 17, Gradle 8.13, Android SDK platform/build-tools 32 và quyền tải dependency. Workflow [Android build](../../.github/workflows/android-build.yml) là cấu hình tham chiếu. Từ thư mục gốc repository:

```bash
bash scripts/fetch-sherpa-android.sh
gradle :app:testDebugUnitTest --stacktrace
gradle :app:assembleDebug --stacktrace
```

Có thể chạy riêng luồng lệnh offline sau khi dependency đã sẵn sàng:

```bash
gradle :app:testDebugUnitTest --tests 'com.voicegeely.app.voice.OfflineCommandFlowTest'
```

Bài kiểm thử này chạy parser → typed command → vehicle router → read-back giả lập → coordinator/lời đáp. Nó kiểm tra 22°C từ “hai hai”, write được nhận nhưng chưa xác minh, write bị từ chối và phần trăm kính không hợp lệ. Fake property access ép nhánh kiểm tra read-back sản xuất, nhưng trạng thái property do test cung cấp; không chứng minh VHAL trên xe. CI còn chạy bộ unit test khác cho safety gate và activation.

### Security / Activation regression nhanh

Khi chỉ cần xác minh license gate và navigation shell mà không chạy toàn bộ test suite:

```bash
gradle :app:testDebugUnitTest --tests 'com.voicegeely.app.security.LicenseGateStateTest'
gradle :app:testDebugUnitTest --tests 'com.voicegeely.app.ui.MainDestinationTest'
```

`LicenseGateStateTest` khóa các trạng thái `UNLICENSED / ACTIVE / EXPIRED / REMOTE_BLOCKED / BINDING_MISMATCH / CLOCK_INVALID`, bao gồm biên expiry và clock-rollback tolerance. Kết quả JVM chỉ chứng minh evaluator deterministic; việc `ActivationActivity` thật sự chặn/mở Activity và dừng Voice service vẫn phải quan sát trên Android.

`MainDestinationTest` khóa đúng sáu menu theo thứ tự đã chốt: Trang chủ / Trợ lý / Ứng dụng / Cài đặt / Ủng hộ / Giới thiệu. Nó không chứng minh layout 1920×1080 không bị vỡ; việc đó vẫn thuộc test thiết bị.

Bản `assembleDebug` thông thường không stage ASR 65M/KWS model. Đừng dùng APK debug mặc định để kết luận chất lượng wake phrase, nhận dạng tiếng Việt hoặc TTS production. Gói hardware-test có model riêng và provenance riêng.

## 2. Runner UI/Activation trên emulator hoặc điện thoại

Sau khi có `app-debug.apk`, dùng runner:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-no-car-ui-validation.ps1 `
  -ApkPath ".\app\build\outputs\apk\debug\app-debug.apk" `
  -ZipBundle
```

Nếu có nhiều thiết bị ADB:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-no-car-ui-validation.ps1 `
  -ApkPath ".\app\build\outputs\apk\debug\app-debug.apk" `
  -Serial "<ADB_SERIAL>" `
  -ZipBundle
```

Nếu muốn giữ bản đang cài, có thể thêm `-SkipInstall`; runner vẫn pull `base.apk` và yêu cầu SHA-256 khớp đúng `-ApkPath`. Không dùng `-SkipInstall` để thu evidence từ một APK khác phiên.

Runner cố ý **không**:

- `pm clear` hoặc xóa dữ liệu ứng dụng;
- tạo/tiêm license;
- giả VIN, gear, speed hay trạng thái đỗ;
- tự cấp quyền OEM/VHAL;
- đánh dấu kết quả là hardware-confirmed.

Luồng runner:

```text
APK đầu vào
  ↓ SHA-256
adb install -r (hoặc SkipInstall + hash verify)
  ↓
pm path + pull base.apk + SHA-256 compare
  ↓
force-stop
  ↓
cold-start ActivationActivity
  ↓
dumpsys activity/window/package/services
  + security logcat
  + UI hierarchy
  + screenshot
  ↓
NO_CAR_UI_REPORT.txt
```

Evidence mặc định gồm:

- `NO_CAR_UI_REPORT.txt`
- `screen.png`
- `ui-hierarchy.xml` nếu `uiautomator` hoạt động trên thiết bị
- `dumpsys-activity.txt`
- `dumpsys-window.txt`
- `dumpsys-package.txt`
- `dumpsys-services.txt`
- `security-logcat.txt`
- `pm-path.txt`

Classification của runner:

| Classification | Ý nghĩa |
| --- | --- |
| `ANDROID_UI_OBSERVED_ACTIVATION_GATE` | `ActivationActivity` đang foreground |
| `ANDROID_UI_OBSERVED_LICENSE_ACTIVE_REDIRECT` | App đã chuyển sang `MainActivity`; chỉ hợp lệ nếu thiết bị vốn có license ACTIVE |
| `ANDROID_UI_OBSERVED_APP_FOREGROUND_UNKNOWN` | Package đang foreground nhưng Activity không khớp hai màn chính |
| `BLOCKED_PLATFORM_OR_UNEXPECTED_FOREGROUND` | Không quan sát được THT Voice foreground; xem dumpsys/screenshot |

Nếu Activation gate đang foreground mà `VoiceAssistantService` vẫn xuất hiện trong active service dump, report đặt `security_gate_consistent=false` để yêu cầu kiểm tra; runner không tự sửa hoặc bỏ qua gate.

## 3. Checklist quan sát thủ công trên Android không phải xe

| Kịch bản | Quan sát hợp lệ | Giới hạn |
| --- | --- | --- |
| Chưa kích hoạt | Chỉ màn Activation; mã sai hoặc thiếu không mở màn chính, Voice core không chạy | Không thử né license để vào UI |
| Đã có license hợp lệ | Có thể xem sáu menu, Settings, popup và bố cục trên màn hình/thiết bị đang dùng | Màn hình điện thoại không thay cho 1920×1080 trên head unit |
| Thiếu `android.car`, VIN hoặc quyền đặc quyền | VIN báo chưa khả dụng; lệnh xe không được coi là đã thực thi | Không suy ra mapping, read-back hay SafetyGate khi đang chạy xe |
| Cài app media/navigation thực | Kiểm tra mở app qua PackageManager/intent và thông báo lỗi | Không xác minh MediaSession/head-unit integration của IHU689G |
| Debug APK không stage model | Ghi nhận đường UI/lỗi của voice nếu thử | Không chấm điểm KWS/ASR/TTS |

Nếu chưa có license hợp lệ, dừng ở kiểm tra activation và dùng JVM/CI cho các luồng command phía sau. Không thay đổi license gate hay giả lập VIN/trạng thái đỗ xe để tuyên bố test UI/vehicle thành công.

Với license ACTIVE đã có hợp lệ trên thiết bị, kiểm tra thủ công tiếp:

1. đủ 6 menu: Trang chủ / Trợ lý / Ứng dụng / Cài đặt / Ủng hộ / Giới thiệu;
2. Settings mở, save/default restore không vỡ;
3. popup hiển thị và kéo được;
4. popup không che hỏng toàn bộ UI trên app khác;
5. không crash khi thiếu `android.car`/VIN/OEM permissions;
6. Main UI không được dùng để suy ra VHAL hoặc cabin voice đã hoạt động.

Ghi commit SHA, Android version, thiết bị/emulator, APK SHA256, kịch bản, kết quả và ảnh/log đã lược bỏ dữ liệu cá nhân. Phân loại kết quả là `JVM_PASS`, `ANDROID_UI_OBSERVED`, `BLOCKED_LICENSE`, `BLOCKED_PLATFORM` hoặc `NEEDS_IHU689G`; không gắn `HARDWARE_CONFIRMED`.

## 4. Chuyển lại luồng xe thật

Khi có IHU689G, dùng gói và matrix có provenance khớp nhau, xe đỗ an toàn, collector bundle cùng phiên. Chỉ khi có read-back/log thực cho từng command mới đánh dấu hardware-confirmed. Giữ Phase 10 hardware gate mở cho tới khi đủ matrix và các blocker được giải quyết.
