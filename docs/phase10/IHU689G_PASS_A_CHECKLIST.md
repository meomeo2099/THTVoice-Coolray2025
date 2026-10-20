# IHU689G Pass A — checklist

Mục tiêu của Pass A: xác minh package, security, UI, ECARX, KWS/ASR, media/navigation và telemetry read-only **trước khi chạy bất kỳ actuator test nào**.

Baseline tham chiếu:

- Source baseline: `main@cae1e6f`
- Hardware-test trigger commit: `b2fd81e1e26df5538dee82f8ae6a7f1657b5de8b`
- Workflow run: `36318249397`
- Delivery fallback: prerelease `hardware-test-latest`
- ASR: Sherpa-ONNX Vietnamese 65M INT8
- Wake profiles: `Hey THT`, `Hello Geely`, `Hey Geely`
- Production TTS Ngọc Huyền/Diễm Trinh: deferred; không dùng làm điều kiện PASS của Pass A.

## 0. Package pre-check

- [ ] Giữ nguyên một bộ package/provenance cùng run.
- [ ] Không trộn APK hoặc ZIP với `BUILD_INFO.txt`, `SHA256.txt`, `ASSET_SHA256.txt` từ run khác.
- [ ] Package preflight đã PASS trước khi cài.
- [ ] Ghi lại commit/build info vào test note.

## 1. Install / cold start / activation

- [ ] Cài sạch package hardware-test.
- [ ] Cold start không crash/ANR.
- [ ] Chưa kích hoạt: chỉ vào Activation screen; KWS/Voice core không chạy.
- [ ] License invalid/expired/blocked/binding mismatch không bypass được.
- [ ] License hợp lệ mở MainActivity bình thường.
- [ ] Offline restart với license hợp lệ vẫn theo policy đã thiết kế.

## 2. UI 1920×1080 + overlay

- [ ] Trang chủ.
- [ ] Trợ lý.
- [ ] Ứng dụng.
- [ ] Cài đặt.
- [ ] Ủng hộ.
- [ ] Giới thiệu.
- [ ] Không vỡ layout/no-scroll ở màn yêu cầu cố định.
- [ ] Popup Voice hiển thị đúng khi app khác đang foreground.
- [ ] Drag popup lưu vị trí.
- [ ] Auto-hide 1/2/3 giây hoạt động.
- [ ] Partial/final transcript dài không phá layout.

## 3. Wake profile selector

Test từng profile riêng. Mỗi thời điểm chỉ một profile được active.

### Hey THT
- [ ] Chọn `Hey THT`.
- [ ] `Hey THT` wake được.
- [ ] `Hello Geely` không wake.
- [ ] `Hey Geely` không wake.

### Hello Geely
- [ ] Đổi sang `Hello Geely` không cần restart app.
- [ ] `Hello Geely` wake được.
- [ ] `Hey THT` không wake.
- [ ] `Hey Geely` không wake.

### Hey Geely
- [ ] Đổi sang `Hey Geely` không cần restart app.
- [ ] `Hey Geely` wake được.
- [ ] `Hey THT` không wake.
- [ ] `Hello Geely` không wake.

### Hot-swap / mic ownership
- [ ] Đổi profile lặp lại nhiều lần không crash.
- [ ] KWS instance cũ nhả mic hoàn toàn.
- [ ] Không có hai KWS profile cùng active.
- [ ] Sau wake: KWS → IDLE → ASR → PROCESSING → IDLE → KWS.
- [ ] Không có KWS/ASR double-owner.

## 4. ECARX Voice key

- [ ] MainActivity foreground → nút Voice mở session.
- [ ] App background → nút Voice mở session.
- [ ] MainActivity đóng → nút Voice vẫn route được.
- [ ] License inactive → chỉ route Activation.
- [ ] Broadcast action khác/null không được chạm Voice core.
- [ ] Bấm nút liên tiếp trong lúc ASR/PROCESSING không tạo session thứ hai.

## 5. ASR / parser regression

Nói trong cabin ở trạng thái yên tĩnh trước, sau đó lặp lại khi media đang phát nhỏ.

- [ ] `20` → 20°C trong ngữ cảnh HVAC.
- [ ] `22` → 22°C.
- [ ] `hai hai` → 22°C.
- [ ] ASR corruption kiểu `hi mươi` không kích hoạt HI.
- [ ] ASR corruption kiểu `hi mươi hi` không kích hoạt HI.
- [ ] Explicit `HI` chỉ kích hoạt endpoint HI khi câu lệnh rõ ràng.
- [ ] Explicit `LO` chỉ kích hoạt endpoint LO khi câu lệnh rõ ràng.
- [ ] Câu mơ hồ fail-closed, không tự suy đoán actuator target.

## 6. Media / navigation

- [ ] Mở YouTube đúng package, không rơi sang browser ngoài policy.
- [ ] Test default media app đã chọn.
- [ ] Play/Pause/Toggle hoạt động qua MediaSession khi có session hợp lệ.
- [ ] Next/Previous chỉ nói Confirmed khi có evidence track đổi.
- [ ] Google Maps launch đúng package.
- [ ] VietMap Live launch đúng package nếu đã cài.
- [ ] Waze launch đúng package nếu đã cài.
- [ ] Voice overlay không che/break navigation UI.

## 7. Read-only vehicle telemetry

- [ ] Assistant VHAL health không tự đoán CONNECTED.
- [ ] `INFO_VIN` đọc được hoặc trả `Unavailable` fail-safe.
- [ ] VIN chỉ hiển thị khi validate đủ 17 ký tự.
- [ ] Ghi GET_RANGE/config/backend diagnostics để dùng cho Pass B.
- [ ] Không thử write actuator trong Pass A.

## 8. Evidence cần thu

- [ ] Collector bundle.
- [ ] Logcat/crash/ANR.
- [ ] KWS/ASR/mic lifecycle diagnostics.
- [ ] Media/navigation diagnostics.
- [ ] VIN/VHAL read-only diagnostics.
- [ ] Ghi kết quả từng case vào Phase 10 matrix.

## Điều kiện qua Pass A

Chỉ chuyển Pass B khi:

- Không có crash/ANR blocker.
- Security/activation không bypass.
- ECARX route ổn định.
- Cả 3 wake profile hoạt động đúng profile được chọn và profile không chọn không wake.
- KWS/ASR không tranh mic.
- 20/22/hai hai/HI/LO không regress.
- Media/navigation/overlay không có blocker.
- Evidence bundle truy ngược được đúng package/run.

Nếu một case fail: dừng đúng nhóm đó, thu evidence, phân loại layer lỗi và sửa bằng branch/PR riêng trước khi test actuator.
