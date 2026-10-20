# THT Voice — Phase 10 IHU689G quickstart

Mục tiêu: test **xe thật** theo cùng một quy trình và thu đủ log để phân biệt lỗi UI / Security / KWS / ASR / VHAL / media. CI xanh hoặc MOCK không được coi là hardware-confirmed.

## 1. Chuẩn bị

Trên Windows cần:

- Android platform-tools (`adb` trong PATH)
- hardware-test artifact vừa build từ GitHub Actions (ZIP hoặc thư mục đã giải nén)
- repo THTVoice đã checkout hoặc riêng thư mục `scripts/`

Kiểm tra kết nối:

```powershell
adb devices
```

Nếu có nhiều thiết bị, ghi lại serial để dùng `-Serial`.

## 2. Build APK hardware-test có model offline

Có hai cách kích hoạt workflow **IHU689G hardware test APK**:

1. Thủ công: Actions → `IHU689G hardware test APK` → `Run workflow`.
2. Có kiểm soát: push file `.hardware-test-trigger` trên branch `hardware-test/**`. Push khác hoặc branch khác không tạo hardware artifact.

Chờ workflow xanh rồi tải artifact `THTVoice-IHU689G-hardware-test` từ Actions. Nếu Actions artifact storage không khả dụng, tải ZIP và `BUILD_INFO.txt` từ private rolling prerelease [`hardware-test-latest`](https://github.com/meomeo2099/THTVoice/releases/tag/hardware-test-latest); tag được cập nhật theo build nên luôn xác minh provenance bằng preflight.

Artifact gồm:

- `THTVoice-IHU689G-hardware-test.apk`
- `SHA256.txt`
- `BUILD_INFO.txt`
- `ASSET_SHA256.txt`

Workflow luôn stage Sherpa Android runtime, Gipformer Vietnamese 65M INT8 offline ASR và đủ ba wake phrase KWS profiles: `Hey THT` (mặc định), `Hello Geely`, `Hey Geely`. `BUILD_INFO.txt` ghi profile mặc định và danh sách profile, cùng commit/model source/backend, APK SHA-256, Sherpa runtime SHA-256, test-matrix SHA-256, asset-manifest SHA-256 và trạng thái TTS production assets.

`ASSET_SHA256.txt` ghi SHA-256 của Sherpa AAR, toàn bộ ASR/KWS, TTS assets đã được stage và file test matrix. Đây là bằng chứng provenance của gói build, **không phải** bằng chứng hardware-confirmed.

Nếu `tts_voice_assets=not_staged`, APK vẫn dùng được để test KWS/ASR/parser/VHAL nhưng **không được đánh dấu TTS production là hardware-confirmed**. Không tự thay model Ngọc Huyền/Diễm Trinh bằng model khác chưa xác minh.

Trước khi nối xe, chạy preflight trên chính ZIP artifact:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -ExpectedSourceMainSha <source_main_sha_trong_BUILD_INFO> `
  -PreflightOnly
```

Preflight phải PASS. Không tách APK ra rồi dùng metadata của run khác.

**Gói run #10 hiện đã phát hành** dùng workflow commit `b2d7ee66e5c72886c4e27ff61fc287672e194689`, source main `f28c51e03035d1606dedfe71ca1e3a6c0dc88479` và backup `backup/phase10-wake-profiles-f28c51e-20260927`. Tải ZIP từ private prerelease, rồi chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha b2d7ee66e5c72886c4e27ff61fc287672e194689 `
  -ExpectedSourceMainSha f28c51e03035d1606dedfe71ca1e3a6c0dc88479 `
  -PreflightOnly
```

Private rolling prerelease có thể được thay bằng gói build sau; nếu hash/SHA không khớp, lấy đúng gói run #10 hoặc kiểm tra lại BUILD_INFO của gói mới trước khi cài.

## 3. Cài package đã verify + cold-start + collector

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -ExpectedSourceMainSha <source_main_sha_trong_BUILD_INFO> `
  -DurationSeconds 180 `
  -ZipBundle
```

Nếu APK đúng package này đã cài sẵn và chỉ muốn thu lại evidence, `-SkipInstall` sẽ đọc `base.apk` từ thiết bị và so SHA-256 với APK trong ZIP đã verify. Nếu khác hash hoặc firmware chặn đọc APK, runner dừng trước khi thu log; dùng lệnh cài package ở trên rồi chạy lại.

Lệnh `-SkipInstall`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -ExpectedSourceMainSha <source_main_sha_trong_BUILD_INFO> `
  -SkipInstall `
  -DurationSeconds 180 `
  -ZipBundle
```

Nhiều thiết bị ADB:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-ihu689g-test.ps1 `
  -Serial <ADB_SERIAL> `
  -HardwarePackagePath .\THTVoice-IHU689G-hardware-test.zip `
  -ExpectedGitSha <commit_sha_cua_workflow> `
  -ExpectedSourceMainSha <source_main_sha_trong_BUILD_INFO> `
  -DurationSeconds 180 `
  -ZipBundle
```

Matrix gồm nhiều nhóm và ba profile; 180 giây mặc định chỉ dành cho **một phiên/nhóm case**. Chạy runner/collector lặp lại cho từng nhóm hoặc tăng `-DurationSeconds` phù hợp, giữ riêng từng bundle theo timestamp. Ghi profile đang chọn, số lần thử, missed/false wake và các case ID trong ghi chú matrix; không cộng gộp log của nhiều phiên rồi suy đoán một case đã PASS.

Runner **không nhận APK rời qua `-ApkPath` nữa**. Script không tự cấp quyền mic/overlay và không bypass activation. Collector sẽ chép `SHA256.txt`, `BUILD_INFO.txt`, `ASSET_SHA256.txt` của đúng package đã test vào `hardware-package-provenance/` trong bundle.

## 4. Thứ tự test ưu tiên

### A. Security trước

1. Chưa có license: cold-start chỉ được thấy Activation/Security.
2. Không được có Home/Assistant/Apps/Settings phía sau để bấm vòng.
3. Voice core / wake phrase đang chọn / nút Voice không được chạy khi gate chưa mở.
4. Mã sai phải giữ trạng thái locked.
5. License hợp lệ mới được mở UI chính và khởi động Voice core.
6. Khởi động lại offline với license hợp lệ để xác minh policy cache hiện tại.

### B. UI / popup

1. Mở đủ 6 màn ở 1920×1080.
2. Không control nào bị cắt; màn mặc định yêu cầu no-scroll không được cần vertical scroll.
3. Popup: listening → partial/final → processing → result/error.
4. Thử kéo và auto-hide 1/2/3 giây.
5. Quét QR MBBank bằng điện thoại thật.

### C. Nút Voice / KWS / ASR / VAD

- Khi `MainActivity` đang đóng, bấm **nút Voice trên xe** và xác minh chuỗi ECARX broadcast → wake bridge → Voice session.
- Trong Settings, chọn lần lượt `Hey THT` (mặc định), `Hello Geely`, `Hey Geely`; kiểm tra mỗi phrase được chọn 10 lần trong cabin yên.
- Với mỗi profile đang được chọn, nói từng phrase còn lại 5 lần để xác nhận app không mở Voice/ASR.
- Sau khi lưu đổi profile, xác nhận profile cũ không wake và profile mới wake mà không cần restart app.
- Lặp lại wake với profile đang chọn khi quạt cao và khi nhạc mức vừa; ghi rõ profile và số wake/missed/false wake.
- Kiểm tra chuỗi KWS → **nhả mic** → ASR → processing → resume KWS; không được có `MIC_BUSY`/open failure bất thường.
- Bắt buộc test các câu:
  - `đặt nhiệt độ 20 độ`
  - `đặt nhiệt độ 22 độ`
  - `đặt nhiệt độ hai mươi độ`
  - `đặt nhiệt độ hai mươi hai độ`
  - `đặt nhiệt độ hai hai độ`
  - case ASR nghe `hai` thành `hi`, ví dụ `hi mươi` / `hi mươi hi`
  - `đặt nhiệt độ hi`
  - `đặt nhiệt độ lo`
- `hai hai` phải ra đúng **22°C**, không được thành 4°C hoặc HI.
- Chỉ câu HI/LO rõ ràng mới chọn endpoint; không trường hợp số 2 nào được nhảy sang HI.
- Ghi latency/CPU/PSS/computeRTF từ `asr-metrics.txt`.

### D. Vehicle commands / VHAL

Chỉ test actuator khi xe ở trạng thái an toàn. Ghi rõ runtime result:

- `Confirmed`
- `AcceptedUnverified`
- `Rejected`
- `Failed`

Với mỗi lệnh HVAC/kính, đối chiếu `vhal-io-events.txt`:

```text
SET_* property=<id> area=<id> value=<requested> result=ACCEPTED
GET_* property=<same id> area=<same id> value=<actual> result=OK
```

`SET ... ACCEPTED` **không phải** bằng chứng xe đã làm xong. Typed executor vẫn phải đọc lại và trả `Confirmed` mới được phát câu khẳng định thành công.

Nếu runtime truy vấn range/config, kiểm tra `GET_RANGE` và phân biệt rõ:

- `result=OK source=VHAL_CONFIG`
- `result=NO_CONFIG`
- `result=INVALID_RANGE`
- `result=FAILED status=SECURITY_DENIED|BACKEND_ERROR`

Không lấy `NO_CONFIG` làm lý do để tự đoán Property ID/Area ID. Fallback chỉ dùng mapping/range đã có bằng chứng trong code.

Sau analyzer, mở `VHAL_IO_SUMMARY.csv`, `VHAL_COMMAND_SUMMARY.csv`, `VHAL_EVIDENCE_SUMMARY.csv` và `VHAL_RANGE_ANALYSIS.txt` để đối chiếu property/area, SET/GET/read-back và mức bằng chứng. Mismatch cuối của kính có thể chỉ do motor đang chạy; không tự kết luận mapping sai.

Không đổi/tăng tolerance hoặc thêm Property ID chỉ để ép test thành công.

### E. Media / Navigation

- YouTube phải mở app đích, không rơi sang browser chung khi package hợp lệ có mặt.
- Next/Previous chỉ `Confirmed` khi MediaSession chứng minh track đổi.
- Test Google Maps, Vietmap Live, Waze nếu được cài.

## 5. Điền test matrix

Sau phiên test, mở:

`docs/phase10/IHU689G_TEST_MATRIX.csv`

Matrix mới bắt đầu bằng `NOT_TESTED`. Cột `result` chính thức chỉ dùng:

- `PASS`
- `FAIL`
- `BLOCKED`
- `NOT_TESTED`

`PENDING`, `REVIEW_FAIL`, `PENDING_HARDWARE_CONFIRMATION` chỉ là **evidence suggestion** do helper tạo trong file review; không ghi các giá trị đó vào cột `result` của matrix chuẩn.

Cột `notes` ghi timestamp/log hoặc hành vi nhìn thấy trên xe. Với actuator, `PASS` cần cả hành vi thật và read-back/result phù hợp.

## 6. Bundle collector

Collector tạo các file chính:

- `thtvoice-logcat.txt`
- `asr-metrics.txt`
- `mic-handoff-events.txt`
- `vehicle-events.txt`
- `vhal-io-events.txt`
- `wake-events.txt`
- `overlay-events.txt`
- `security-vin-events.txt`
- `ecarx-events.txt`
- `crash-events.txt`
- `package-info-*.txt`
- `appops-*.txt`
- `services-*.txt`
- `meminfo-*.txt`
- `display-size.txt`
- `display-density.txt`
- `media-session-*.txt`
- `car-service-*.txt`
- `hardware-package-provenance/SHA256.txt`
- `hardware-package-provenance/BUILD_INFO.txt`
- `hardware-package-provenance/ASSET_SHA256.txt`

Nếu chạy với `-ZipBundle`, collector tạo thêm ZIP cùng tên thư mục để gửi riêng khi cần phân tích.

## 7. Phân tích bundle tự động

Chạy một lệnh trên ZIP collector. Lệnh này giải nén **một lần** vào thư mục `-analysis` cạnh ZIP, chạy các analyzer trên cùng dữ liệu rồi tạo matrix review:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\review-ihu689g-bundle.ps1 `
  -BundlePath .\thtvoice-validation\<timestamp>.zip
```

Nếu chỉ có thư mục collector đã giải nén, truyền đường dẫn thư mục vào `-BundlePath`; kết quả sẽ được ghi ngay trong thư mục đó. Thư mục phân tích ZIP đã tồn tại sẽ không bị ghi đè; dùng `-OutputDirectory` mới cho lần review khác. Khi chạy riêng từng analyzer, truyền **cùng thư mục đã giải nén** thay vì truyền ZIP lặp lại.

Không có command-scoped VHAL trace thì các báo cáo khác vẫn được tạo, còn matrix review tự động được bỏ qua. Giữ thư mục phân tích riêng tư như bundle gốc.

Các output quan trọng:

- `ANALYSIS_SUMMARY.txt`
- `VHAL_IO_SUMMARY.csv`
- `VHAL_COMMAND_TRACE.csv`
- `VHAL_COMMAND_SUMMARY.csv`
- `VHAL_EVIDENCE_SUMMARY.csv`
- `VHAL_RANGE_ANALYSIS.txt`
- `MEDIA_NAVIGATION_SUMMARY.txt`
- `IHU689G_TEST_MATRIX_REVIEW.csv`

Đây chỉ là **triage tự động**, không phải bằng chứng hardware-confirmed. Nếu báo có cả HI và 20/22 thì phải đối chiếu timestamp/typed command trước khi kết luận regression.

## 8. Riêng tư

Bundle có thể chứa VIN, serial hoặc identifier của xe/head unit từ `getprop`, `car_service` hoặc log Security. Structured VHAL trace chỉ ghi numeric/bool property traffic của app nhưng vẫn là dữ liệu kỹ thuật của xe. **Không upload bundle xe thật lên repo/issue public.**

## 9. Điều kiện Phase 10 green

Chỉ tạo `backup/phase10-hardware-green` sau khi:

- Security không bypass được.
- Không crash/ANR blocker mới.
- Nút Voice/ECARX và cả ba wake phrase profile đã chọn đi được vào Voice session.
- KWS/ASR không tranh mic.
- 20/22/`hai hai` không regress thành HI hoặc giá trị sai.
- SafetyGate vẫn fail-closed.
- Các actuator được phân loại đúng theo read-back thực tế.
- VHAL SET/GET/range correlation đủ để xác định property/area đang được dùng, nhưng không dùng correlation để tự suy đoán mapping mới.
- Media/navigation và popup/UI đạt matrix trên 1920×1080 thật.
- VIN chỉ được dùng để bind license bắt buộc nếu đọc được ổn định qua nhiều cold boot.

Sau khi có bundle và matrix, phân tích log trước rồi mới tune KWS/VAD/VHAL.
