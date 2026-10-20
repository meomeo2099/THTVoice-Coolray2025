# THT Voice — IHU689G hardware validation

Tài liệu này là checklist bắt buộc trước khi đánh dấu một tính năng là **REAL / hardware-confirmed** trên IHU689G.

Quickstart một lệnh: `docs/phase10/PHASE10_QUICKSTART.md`  
Ma trận kết quả: `docs/phase10/IHU689G_TEST_MATRIX.csv`

## 1. Nguyên tắc

- Parser test, MOCK backend và CI xanh chỉ chứng minh logic phần mềm; không chứng minh xe thật.
- Không thêm hoặc đổi raw VHAL Property ID / Area ID theo suy đoán.
- Với actuator, ưu tiên `WRITE -> READ-BACK -> RESULT`.
- Chỉ `Confirmed` mới được dùng câu phản hồi mang nghĩa thao tác đã hoàn tất.
- `AcceptedUnverified` phải giữ nguyên nếu xe nhận write nhưng chưa xác nhận được trạng thái mục tiêu.
- Khi SPEED/GEAR hoặc điều kiện an toàn không đọc được, SafetyGate phải fail-closed.
- VIN chỉ được dùng làm binding bắt buộc sau khi đọc ổn định qua nhiều cold boot trên xe thật.
- Không cài APK rời trong Phase 10. Runner phải nhận hardware package đã có provenance.

## 2. Chuẩn bị trên Windows

Kiểm tra ADB:

```powershell
adb devices
```

Trước khi nối xe, preflight hardware package:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -PreflightOnly
```

### Cách khuyên dùng: package đã verify + cold-start + collector

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -DurationSeconds 180 `
  -ZipBundle
```

Nếu đúng APK của package này đã cài và chỉ muốn thu evidence lại:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -SkipInstall `
  -DurationSeconds 180 `
  -ZipBundle
```

Hoặc chỉ chạy collector độc lập khi không cần gắn provenance package:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\collect-ihu689g-validation.ps1 -DurationSeconds 180 -ZipBundle
```

Nếu có nhiều thiết bị, thêm `-Serial <ADB_SERIAL>`.

Runner kiểm tra package trước khi cài và collector chép `SHA256.txt`, `BUILD_INFO.txt`, `ASSET_SHA256.txt` vào `hardware-package-provenance/` trong validation bundle. Collector lấy Logcat, ASR metrics, VHAL events, Security/VIN events, crash markers, package/appops/service state, display size/density, meminfo, MediaSession và car service nếu firmware cho phép.

> Bundle có thể chứa VIN, serial hoặc identifier từ `getprop`, `car_service` và log. Không upload bundle lên repo/issue public.

## 3. Security / Activation

Test trước mọi chức năng Voice:

1. Cold-start khi chưa có license: chỉ được thấy Security/Activation full-screen.
2. Không được có Home/Assistant/Apps/Settings phía sau để bypass.
3. Wake phrase đã chọn, nút Voice, mic/ASR và điều khiển xe không được chạy khi gate chưa mở.
4. Mã sai/expired/blocked/binding mismatch phải giữ app ở trạng thái locked.
5. License hợp lệ mới mở UI chính và cho phép Voice core khởi động.
6. Test restart offline với license hợp lệ theo đúng policy hiện tại.

## 4. VIN telemetry

- Chỉ chấp nhận VIN từ Android Car `INFO_VIN` hoặc, dự phòng, system property nền tảng `sys.ecarx.vin` (reference, chưa xác minh); màn kích hoạt ghi nguồn đã đọc.
- Ghi lại cả hai nguồn: `adb shell getprop sys.ecarx.vin` (chỉ lưu cục bộ, không upload).
- `VIN unavailable` là kết quả hợp lệ; không tự tạo/fallback sang chuỗi đoán.
- Cold boot ít nhất 3 lần và so sánh VIN.
- Chỉ sau khi VIN ổn định mới cân nhắc bật VIN-required license binding.

## 5. Wake word / KWS

### Cabin yên tĩnh

- Trong Settings, lần lượt chọn `Hey THT` (mặc định), `Hello Geely` và `Hey Geely`; lưu thay đổi.
- Với từng profile đang được chọn, nói đúng wake phrase đó ít nhất 10 lần ở khoảng cách sử dụng bình thường.
- Với từng profile đang được chọn, nói mỗi phrase không được chọn ít nhất 5 lần; không phrase nào ngoài profile active được mở Voice/ASR.
- Đổi profile đã lưu khi service đang chạy: phrase cũ phải ngừng đánh thức, phrase mới phải hoạt động mà không cần khởi động lại app.
- Ghi profile đang chọn, số wake đúng, wake hụt, false wake và wake lặp.
- Sau wake, ASR phải nhận lệnh tiếp theo; KWS phải nhả microphone trước khi ASR mở mic.

### Có nhiễu

Lặp lại với từng profile trong các điều kiện:

- quạt điều hòa mức thấp;
- quạt mức cao (ít nhất 5 lượt/profile, tương ứng matrix KWS-04);
- nhạc nhỏ;
- nhạc vừa (ít nhất 5 lượt/profile, tương ứng matrix KWS-05).

Ghi rõ profile đang chọn và điều kiện cabin. Không tune threshold từ một lần test; cần nhìn cả missed wake và false wake.

## 6. ASR 65M + VAD/endpoint

Test tối thiểu:

```text
đặt nhiệt độ 20 độ
đặt nhiệt độ 22 độ
đặt nhiệt độ hai hai độ
hai mươi độ
hai mươi hai độ
đặt nhiệt độ hi
đặt nhiệt độ lo
đặt nhiệt độ 25 độ
bật điều hòa
tắt điều hòa
quạt mức 3
hạ kính bên lái
đóng kính bên lái
mở youtube
chỉ đường về nhà
```

Kiểm tra `THTVoiceASR`:

- audio/session duration;
- first partial latency;
- finish latency;
- process CPU;
- PSS/RAM;
- computeRTF;
- VAD RMS / min speech / trailing silence / max listen snapshot.

**20 / 22 / hai mươi / hai mươi hai / hai hai không được kích hoạt HI.** Chỉ explicit HI/LO mới được chọn endpoint nhiệt độ.

Không chuyển sang 30M chỉ vì cảm giác chậm. Chỉ cân nhắc fallback nếu metrics xe thật cho thấy 65M không đạt realtime hoặc gây áp lực CPU/RAM không chấp nhận được.

## 7. HVAC

Test tối thiểu:

- temperature 16..32;
- HI / LO explicit;
- HVAC power;
- A/C compressor;
- fan 0..8;
- recirculation;
- front/rear defrost;
- face / foot / windshield và tổ hợp đã hỗ trợ.

Một case hợp lệ cần có khi backend cung cấp:

1. STT final;
2. typed command;
3. property/area/value mục tiêu;
4. write acceptance;
5. read-back;
6. kết quả `Confirmed`, `AcceptedUnverified`, `Rejected` hoặc `Failed`.

Nếu UI xe thay đổi nhưng read-back không khớp, không nới tolerance để ép thành `Confirmed`.

## 8. Windows

Area đã xác minh:

- driver front: `16`;
- passenger front: `64`;
- rear left: `256`;
- rear right: `1024`.

Property chính `322964416`; fallback `591405227` chỉ dùng theo logic hiện có.

Test:

- open / close;
- 25% / 50% / 75%;
- ALL khi xe ở trạng thái phù hợp;
- STOP chỉ best-effort nếu chưa có motor-stop state xác minh.

## 9. Drive mode / regeneration / status query

Test Eco / Comfort / Sport và Regen Low / Medium / High bằng read-back thật.

Đối chiếu status query với UI xe/công cụ chẩn đoán cho speed, gear, SOC, range, ODO/trip, outside temperature, charging, door/lock và passenger/seatbelt khi có.

Giá trị lạ phải giữ `Rejected`/unknown; không đoán enum.

## 10. Media / Navigation

- Play / Pause / Toggle: kiểm tra trạng thái phát thật.
- Volume Up / Down: kiểm tra stream-volume read-back.
- Next / Previous: chỉ `Confirmed` khi MediaSession chứng minh track identity đổi trên cùng package/session.
- Không có metadata/quyền -> `AcceptedUnverified`.
- YouTube: package đích có mặt thì không được rơi sang browser chung.
- Test Google Maps, Vietmap Live và Waze nếu cài; gọi app trực tiếp phải override default đúng parser/router.

## 11. UI / Overlay / Support

- Test đủ 6 màn ở 1920×1080 thật.
- Không control quan trọng nào bị cắt; mặc định không cần vertical scroll.
- Popup khi Activity chính đóng: wake phrase đang chọn / nút Voice -> listening -> partial/final -> processing -> result/error.
- Test kéo popup, persist vị trí và auto-hide 1/2/3 giây.
- Text dài không làm vỡ layout.
- QR MBBank phải quét được bằng điện thoại thật.
- Màn Giới thiệu phải hiển thị đúng version/build, Android/API, CPU ABI, RAM và độ phân giải runtime.

## 12. Điều kiện để đánh dấu GREEN

Chỉ tạo `backup/phase10-hardware-green` khi:

- unit tests + Android build xanh;
- không crash/ANR mới;
- Security không bypass được;
- SafetyGate vẫn fail-closed;
- KWS/ASR không tranh microphone;
- 20/22/`hai hai` không regress thành HI;
- lệnh xe được phân loại kết quả đúng theo read-back;
- log đủ để truy ngược typed command -> backend -> read-back;
- validation bundle truy ngược được đúng hardware package/commit/model/APK qua provenance;
- UI/popup đạt màn hình thật;
- không có Property ID mới được thêm theo suy đoán;
- VIN-required vẫn tắt nếu chưa xác minh VIN ổn định.

Sau khi đạt các điều kiện trên mới tạo backup green và chuyển sang release hardening.
