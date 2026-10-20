# THT Voice — Current workflow

Tài liệu này là sơ đồ làm việc ngắn gọn cho giai đoạn hiện tại; `docs/REBUILD_PLAN.md` vẫn giữ lịch sử phase và checklist chi tiết.

## Trạng thái hiện tại

- Software baseline đã build/test xanh trên `main`.
- Baseline wake-profile hiện tại: `main@cae1e6f`.
- CI thường `Android build` chỉ validate/test/assemble, **không upload APK artifact**.
- APK để test xe chỉ được tạo từ workflow `IHU689G hardware test APK`.
- Hardware-test artifact có retention ngắn; nếu Actions artifact storage không khả dụng thì workflow dùng rolling private prerelease `hardware-test-latest` làm fallback.
- ASR production path: Sherpa-ONNX Vietnamese 65M INT8.
- Offline wake phrase có 3 profile chọn trong Settings: **Hey THT** (mặc định), **Hello Geely**, **Hey Geely**. Chỉ một profile KWS được active tại một thời điểm và có thể hot-swap an toàn.
- Production TTS assets `Ngọc Huyền` / `Diễm Trinh` được **defer**: chưa có model pinned/licensed đã xác minh thì không gắn model giả. Việc này không chặn test KWS/ASR/parser/VHAL bằng sound feedback hoặc TTS fallback hiện có.
- Phase hiện tại: **Phase 10 — Hardware validation**.
- Hardware-test run hiện tại: workflow run `36318249397`, trigger commit `b2fd81e1e26df5538dee82f8ae6a7f1657b5de8b`, build/preflight/delivery đều PASS.

## Quy tắc artifact

```text
Push / Pull Request
        ↓
Android build
        ↓
Validate PowerShell scripts
        ↓
Validate Phase 10 matrix
        ↓
Sherpa / ASR / KWS / TTS staging self-tests
        ↓
Unit tests
        ↓
assembleDebug
        ↓
PASS
        ↓
KHÔNG upload APK debug từ CI thường
```

Luồng hardware-test:

```text
hardware-test/** + .hardware-test-trigger
  ↓
IHU689G hardware test APK
  ↓
Stage Sherpa runtime
  ↓
Stage Vietnamese ASR 65M INT8
  ↓
Stage selectable KWS profiles
    - Hey THT
    - Hello Geely
    - Hey Geely
  ↓
Verify model assets
  ↓
Unit tests + assembleDebug
  ↓
Create provenance metadata
  ↓
Verify package preflight
  ↓
Actions artifact storage
  ↓ nếu storage không khả dụng
Rolling private prerelease: hardware-test-latest
```

Gói hardware-test phải giữ cùng một phiên:

```text
THTVoice-IHU689G-hardware-test.apk
SHA256.txt
BUILD_INFO.txt
ASSET_SHA256.txt
```

Không trộn APK của run này với metadata của run khác.

## Sơ đồ làm việc hiện tại

```text
[1] SOFTWARE BASELINE GREEN
    Voice core / parser / coordinator
    HVAC / windows / vehicle handlers
    Android / media / navigation
    ASR 65M / selectable KWS / mic ownership
    UI / Security / VIN telemetry
    VHAL diagnostics / test matrix / provenance
               │
               ▼
[2] CI THƯỜNG — KHÔNG DEBUG ARTIFACT
    PR + main chỉ test compile/regression
               │
               ▼
[3] BUILD IHU689G HARDWARE-TEST                 [DONE FOR CURRENT BASELINE]
    Run 36318249397 PASS
    Build/preflight/delivery PASS
               │
               ▼
[4] PRE-CHECK TRƯỚC KHI CÀI XE
    Đúng package/version
    ASR 65M có trong package
    3 KWS profile có trong package
    APK/hash/metadata cùng một workflow run
               │
               ▼
[5] TEST IHU689G TRÊN XE THẬT                  [CURRENT HARDWARE GATE]
    5A. Install / cold start / activation
    5B. UI 1920×1080 + overlay
    5C. Nút Voice + Hey THT + Hello Geely + Hey Geely + mic handoff
    5D. ASR 20 / 22 / hai hai / explicit HI-LO
    5E. HVAC
    5F. Windows
    5G. Drive mode / regen / status
    5H. Media / navigation / MediaSession
    5I. Security / offline restart / VIN
    5J. Stress: đổi app, media đang phát, sleep/wake
               │
               ▼
[6] THU EVIDENCE
    Collector bundle
    VHAL trace + GET_RANGE/backend status
    Mic/KWS/ASR diagnostics
    Crash/ANR/logcat
    Test matrix results
               │
               ▼
[7] PHÂN LOẠI LỖI
    Parser/normalizer?
    Mic ownership/KWS/ASR?
    Permission/VHAL backend?
    Property/area/range?
    MediaSession/package launch?
    UI/runtime/security?
               │
               ▼
[8] FIX THEO EVIDENCE
    Mỗi nhóm lỗi → branch/PR riêng
    Không đoán Property ID mới
    Không nói Confirmed nếu chưa read-back
    Không nới SafetyGate để né lỗi test
               │
               ▼
[9] REBUILD + RETEST ĐÚNG CASE FAIL
    CI xanh
    Build hardware-test mới
    Ghi provenance mới
    Test lại xe
               │
          ┌────┴────┐
          │         │
        FAIL       PASS
          │         │
          └─ về [7] ▼
               │
               ▼
[10] FULL VALIDATION PASS
     Không crash/ANR blocker
     Security không bypass
     Voice session ổn định
     3 wake profile hoạt động đúng profile được chọn
     20/22/hai hai không regress
     SafetyGate fail-closed
     Actuator chỉ hardware-confirmed khi đủ evidence
               │
               ▼
[11] backup/phase10-hardware-green
               │
               ▼
[12] VIN-REQUIRED BINDING
     Chỉ làm nếu INFO_VIN trên xe thật ổn định
               │
               ▼
[13] PHASE 11 — RELEASE HARDENING
     Release signing
     Customer updater/recovery
     Release candidate
     Rollback anchor
     stable/release-ready
```

## Thứ tự test trên xe đề xuất

### Pass A — không điều khiển actuator

1. Cài đúng package của run hiện tại + cold start.
2. Activation gate và các trạng thái invalid/expired/offline.
3. Kiểm tra 6 màn UI + overlay.
4. Test lần lượt `Hey THT`, `Hello Geely`, `Hey Geely`; mỗi lần đổi profile phải xác minh profile cũ không còn wake và profile mới wake được.
5. Test nút Voice ECARX khi `MainActivity` đang mở, ở background và đã đóng.
6. Xác minh KWS → nhả mic → ASR → processing → resume KWS, không double-owner.
7. ASR câu lệnh số: 20, 22, hai hai, `hi mươi`, HI, LO.
8. Media/navigation mở app và read-back.
9. VIN/status telemetry read-only.

Mục tiêu: bắt crash, permission, mic, KWS profile và UI trước khi thử actuator.

### Pass B — actuator khi xe đỗ an toàn

1. HVAC system power và A/C compressor riêng biệt.
2. Nhiệt độ 16–32°C, 20, 22, hai hai, HI/LO, fan, recirculation, defrost, hướng gió.
3. Windows từng cửa + ALL + phần trăm + STOP.
4. Drive mode / regeneration nếu backend cho phép khi trạng thái xe phù hợp.
5. Kiểm tra ACK → EXECUTE → VERIFY → RESULT cho từng lệnh.
6. So hành vi thật với VHAL read-back/log.

### Pass C — stress / recovery

1. App ở background rồi bấm nút Voice.
2. Maps/VietMap/media đang mở rồi gọi Voice.
3. Nhạc đang phát + KWS + ASR + sound feedback/TTS fallback.
4. Đổi wake profile nhiều lần, bảo đảm không còn KWS instance cũ giữ mic.
5. Sleep/wake hoặc restart head unit.
6. Mất backend/VHAL/permission giả định: hệ thống phải fail-safe, không báo thành công giả.

## Quy tắc sửa lỗi Phase 10

```text
Evidence trước
   ↓
Xác định đúng layer lỗi
   ↓
Backup main-green gần nhất
   ↓
Branch nhỏ
   ↓
Fix tối thiểu
   ↓
Regression test
   ↓
PR CI xanh
   ↓
Merge
   ↓
Hardware-test build mới
   ↓
Retest case fail
```

Không gộp nhiều lỗi không liên quan vào một PR khi có thể tách riêng.

## Điều kiện được gọi là hardware-green

Chỉ tạo `backup/phase10-hardware-green` khi đồng thời đạt:

- Không còn crash/ANR blocker trong full pass.
- Activation/security gate không bypass.
- Nút Voice ECARX mở session ổn định khi UI đóng/background.
- `Hey THT`, `Hello Geely`, `Hey Geely` hoạt động đúng khi được chọn; profile không được chọn không tự wake.
- KWS → ASR → processing → KWS không tranh mic.
- 20/22/`hai hai` không thành HI hoặc nhiệt độ sai.
- HVAC/windows/drive/regen có evidence đúng với mức `Confirmed` được dùng.
- SafetyGate vẫn fail-closed.
- Media/navigation/overlay đạt matrix cần thiết.
- Bundle/log/provenance truy ngược được đúng commit + model + APK đã test.

## Backup / rollback

- Không force-update các nhánh `backup/...` cũ.
- Trước thay đổi lớn: tạo backup mới từ `main` xanh.
- Sau milestone xanh: tạo backup milestone mới.
- Checkpoint wake-profile hiện tại: `backup/phase10-wakeword-profiles-main-green-20260927` → `cae1e6f`.

## Việc tiếp theo

```text
1. Dùng đúng package của hardware-test run 36318249397.
2. Giữ nguyên APK + SHA256 + BUILD_INFO + ASSET_SHA256 cùng phiên.
3. Thực hiện Pass A trên IHU689G.
4. Nếu Pass A sạch blocker → Pass B.
5. Sau đó Pass C.
6. Thu collector bundle.
7. Sửa đúng lỗi có evidence và rebuild đúng case fail.
8. Lặp đến Phase 10 hardware-green.
9. Production TTS Ngọc Huyền / Diễm Trinh bổ sung sau khi có model nguồn/license xác minh.
10. Sau hardware-green mới chuyển Phase 11.
```
