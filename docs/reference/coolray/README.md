# Tài liệu tham khảo Coolray: phân tích REF-DIAG + REF-VOICE

Tên ứng dụng gốc được thay bằng nhãn trung tính theo yêu cầu chủ repo: **REF-VOICE** = ứng dụng trợ lý giọng nói
tham chiếu (cùng mã với GalaxyVoiceAssistant), **REF-DIAG** = ứng dụng chẩn đoán tham chiếu (profile SX11A5).

Gói phân tích do chủ repo tổng hợp (01/10/2026) từ hai APK `ref-diag.apk` và `ref-voice.apk`
(SHA-256 trong `01_Report/APK_METADATA.txt`). Chỉ lưu dữ liệu đã phân tích; **không** lưu APK gốc,
config thô trích từ APK (`03_RefDiag_SX11A5_Raw/`) hay chuỗi dump từ binary (`05_RefVoice_Evidence/`),
theo quy tắc không chép asset/source của ứng dụng khác trong `AGENTS.md`.

| Thư mục | Nội dung |
|---|---|
| `README_FIRST.txt` | Hướng dẫn gốc của gói |
| `01_Report/` | Báo cáo tổng hợp, hash APK |
| `02_Combined_Mapping/` | Bảng VHAL Coolray từ REF-VOICE, operation REF-DIAG liên quan voice, JSON tổng hợp |
| `04_RefDiag_CSV/` | 12 ECU, 514 DID, 82 operation của profile SX11A5 (đã làm phẳng) |
| `06_Implementation/` | Đề xuất module và thứ tự test |
| `07_RefWidget/` | Nguồn thứ hai REF-WIDGET (09/10/2026): sấy kính, sưởi vô lăng/ghế, thông tin xe chỉ đọc |
| `08_RefSystemUI/` | REF-SYSUI (16/10/2026, chưa dùng trong code): bảng HVAC hệ thống, function ID ECARX và cách phân giải `IWrapper`, media, navi, cảnh, launcher |

## Đối chiếu với repo

- **REF-VOICE** là bản build của GalaxyVoiceAssistant đã đổi package. Mọi property/area
  Coolray trong `02_Combined_Mapping/ref_voice_coolray_vhal_mapping.csv` khớp `car/CoolrayProperties.kt`
  (kính `0x234020AB`=591405227, đèn trong xe `0x15400F04`=356519684, ghế `0x1540050B`/`0x15400513`, HVAC…).
  Khoảng nhiệt độ 15,5..28,5 °C đã được áp dụng vào repo từ nguồn này.
- **REF-DIAG SX11A5** dùng UDS qua VHAL `0x2170A634` area `0x01000000` tới BCM `0x720`, AC `0x7C6`…
  **Không dùng cho lệnh giọng nói**: đây là lệnh ép trạng thái kiểu chẩn đoán (có thể cần Security Access,
  không có read-back). Chỉ cân nhắc DID **đọc** (0x22) cho truy vấn trạng thái, sau khi thử trên xe thật.
  Tuyệt đối không expose các routine TCU/EMS/EPS/ESC/EPB/SRS, ghi VIN, reset/adaptation ECU.
- Mọi mục `confidence=Medium/Candidate` phải được log trên đúng head unit (IHU624G/IHU689G) trước khi dùng.
