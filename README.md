# THT Voice — bản Coolray 2025

Trợ lý giọng nói offline tiếng Việt cho Geely Coolray 2025 (đầu xe ECARX `SX11-LA` / `IHU730P`, Android 9),
dựng từ nền THT Voice bản Coolray. Mã xe viết riêng theo dữ liệu lấy từ chính đầu xe 2025.

Đang hỗ trợ: điều hoà (bật/tắt, A/C, lạnh nhanh, nhiệt độ 16–32 °C, quạt 1–7, hướng gió, lấy gió, sấy kính trước/sau),
sưởi vô lăng, kính cửa, cửa sổ trời, sưởi và thông gió ghế trước, đèn trong xe / đèn ngoài / đèn sương mù,
hỏi tốc độ / số / nhiệt độ / quạt / xăng / ODO / nhiệt độ ngoài trời / tình trạng xe, cùng các lệnh Android/media chung.

- **Bảng thuộc tính xe:** [docs/COOLRAY2025_PROPERTIES.md](docs/COOLRAY2025_PROPERTIES.md)
- **Đặc tả lệnh giọng nói + danh mục hàm ECARX:** [docs/COOLRAY2025_VOICE_SPEC.md](docs/COOLRAY2025_VOICE_SPEC.md)
- **Ký APK:** các workflow dùng secrets `THT_SIGN_KEYSTORE_BASE64`, `THT_SIGN_STORE_PASSWORD`, `THT_SIGN_KEY_ALIAS`,
  `THT_SIGN_KEY_PASSWORD` (khoá trùng chứng chỉ platform của đầu xe → app được cấp quyền hệ thống khi `adb install`).
- **APK test mới nhất:** Release `hardware-test-latest` (Actions → *Coolray 2025 hardware test APK* → Run workflow).

---

# THT Voice

THT Voice là trợ lý giọng nói tiếng Việt cho màn hình xe chạy Android, **ưu tiên chạy offline**:
nghe lệnh, điều khiển xe, nhạc, bản đồ, ứng dụng và trả lời bằng giọng nói ngay trên máy, không
cần Internet.

## Hướng dẫn nhanh

### Cho chủ repo / người bán

| Việc cần làm | Hướng dẫn |
|---|---|
| Thêm từ app hay nghe nhầm (ví dụ "you tụ bay" → YouTube) | [`docs/HUONG_DAN_THEM_TU_NGHE_NHAM.md`](docs/HUONG_DAN_THEM_TU_NGHE_NHAM.md) |
| Máy chủ bản quyền: deploy, secrets, trang quản trị, gói giọng đọc, chặn bản bị ký lại | [`worker/README.md`](worker/README.md) |
| Phát hành bản chính thức (keystore, secrets, workflow "Release candidate APK", OTA) | [`docs/phase11/RELEASE_PLAN.md`](docs/phase11/RELEASE_PLAN.md) |
| Luồng cập nhật an toàn trên xe | [`docs/SAFE_UPDATE_RELEASE.md`](docs/SAFE_UPDATE_RELEASE.md) |
| Chọn ứng dụng nhạc/video mặc định | [`docs/MEDIA_DEFAULT_APP.md`](docs/MEDIA_DEFAULT_APP.md) |
| Từ đánh thức (Hey THT…) | [`docs/WAKE_WORD.md`](docs/WAKE_WORD.md) |

**Tải APK test mới nhất:** Release
[`hardware-test-latest`](https://github.com/meomeo2099/THTVoice-Coolray2025/releases/tag/hardware-test-latest)
(file `.apk` cài thẳng). Muốn build lại: **Actions → IHU689G hardware test APK → Run workflow**.

### Thử trên xe

| Việc cần làm | Hướng dẫn |
|---|---|
| Bắt đầu nhanh với bản test | [`docs/phase10/PHASE10_QUICKSTART.md`](docs/phase10/PHASE10_QUICKSTART.md) |
| Chạy kiểm tra trên IHU689G | [`docs/phase10/RUN_IHU689G_VALIDATION.md`](docs/phase10/RUN_IHU689G_VALIDATION.md) |
| Bảng ca test trên xe | [`docs/phase10/IHU689G_TEST_MATRIX.csv`](docs/phase10/IHU689G_TEST_MATRIX.csv) |
| Thử khi không có xe | [`docs/phase10/NO_CAR_TESTING.md`](docs/phase10/NO_CAR_TESTING.md) |
| Quy tắc ghi nhận bằng chứng phần cứng | [`docs/phase10/HARDWARE_EVIDENCE_RULES.md`](docs/phase10/HARDWARE_EVIDENCE_RULES.md) |

### Cho lập trình viên / AI agent

| Nội dung | Tài liệu |
|---|---|
| **Đọc đầu tiên:** quy tắc, quy trình một lát cắt, backlog, việc tiếp theo | [`docs/HANDOFF_WORKFLOW.md`](docs/HANDOFF_WORKFLOW.md) |
| Prompt dán sẵn cho agent | [`docs/AGENT_PROMPT.md`](docs/AGENT_PROMPT.md) |
| Quy tắc bắt buộc cho agent | [`AGENTS.md`](AGENTS.md) |
| Luồng xử lý lệnh và phản hồi (ACK → EXECUTE → VERIFY → RESULT) | [`docs/VOICE_WORKFLOW.md`](docs/VOICE_WORKFLOW.md) |
| Kiến trúc giọng nói | [`docs/VOICE_ARCHITECTURE.md`](docs/VOICE_ARCHITECTURE.md) |
| An toàn khi xe chạy | [`docs/SAFETY_ARCHITECTURE.md`](docs/SAFETY_ARCHITECTURE.md) |
| Thuộc tính xe Coolray (property/area) | [`docs/COOLRAY_IHU624G_PROPERTIES.md`](docs/COOLRAY_IHU624G_PROPERTIES.md) |
| Kế hoạch và sơ đồ triển khai | [`docs/COOLRAY_DEPLOYMENT_PLAN.md`](docs/COOLRAY_DEPLOYMENT_PLAN.md) |
| Đóng gói model offline | [`docs/OFFLINE_MODEL_PACKAGING.md`](docs/OFFLINE_MODEL_PACKAGING.md) |
| Popup giọng nói | [`docs/VOICE_OVERLAY.md`](docs/VOICE_OVERLAY.md) |
| Giao diện chuẩn | [`docs/UI_BASELINE.md`](docs/UI_BASELINE.md) |
| Lịch sử và kế hoạch tổng | [`docs/REBUILD_PLAN.md`](docs/REBUILD_PLAN.md) |

Khi chỉ nói "tiếp tục" hoặc "làm tiếp", agent làm theo mục 0 của [`AGENTS.md`](AGENTS.md).

## Thành phần chính

- Nhận giọng nói tiếng Việt offline (Sherpa-ONNX), từ đánh thức và phát hiện giọng nói offline.
- Bộ phân tích lệnh cục bộ, cho kết quả cố định: chuẩn hoá chữ → alias → lệnh có kiểu (typed command).
- Đọc/ghi thuộc tính xe qua adapter đã xác minh, có đọc lại để xác nhận trước khi báo thành công.
- Điều hướng lệnh nhạc, bản đồ, mở ứng dụng.
- Giọng đọc offline và âm báo; phản hồi theo chế độ FULL / SHORT / SILENT.
- Cổng an toàn dựa trên tốc độ (SPEED) và số (GEAR) thực tế.
- Bản quyền có chữ ký từ máy chủ, gắn với thiết bị; bản release tự kiểm tra chữ ký APK.

## Mô hình an toàn

| Mức | Hành vi |
|---|---|
| `SAFE` | Thực hiện bình thường |
| `CONFIRM_WHEN_MOVING` | Khi xe chạy phải xác nhận bằng lời; lệnh chờ hết hạn sau 8 giây, rồi kiểm tra lại trạng thái xe |
| `PARKED_ONLY` | Không thể bỏ qua bằng xác nhận giọng nói; chỉ chạy khi xe ở số P và tốc độ < 1 km/h |

Các thao tác không qua giọng nói nhưng chỉ được làm khi đỗ xe dùng `ParkedActionGate` với enum
`ParkedAction` (`OTA_INSTALL`, `APK_INSTALL`, `DEV_TOOLS`, `SYSTEM_MAINTENANCE`, `RESET_APP_DATA`,
`TECHNICAL_CONFIGURATION`). Mọi quyết định an toàn được ghi vào audit log trong tiến trình và có thể
hiển thị bằng `SafetyAuditFormatter` để chẩn đoán.

## Lưu ý

- File model nhận giọng nói và giọng đọc rất lớn, được tải về khi build và cố ý không đưa vào lịch
  sử Git.
- Tài liệu adapter/tham chiếu kỹ thuật có thể nêu tên màn hình xe cụ thể khi cần kiểm tra phần cứng;
  thương hiệu hiển thị cho người dùng chỉ là **THT Voice**.
- Không commit API key, token, keystore, khoá ký hay VIN/số điện thoại vào repo.
