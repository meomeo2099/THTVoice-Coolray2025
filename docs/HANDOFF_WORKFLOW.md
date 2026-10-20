# THT Voice (Coolray) — hướng dẫn làm việc (handoff)

Tài liệu này đủ để một người hoặc agent khác tiếp tục bản Coolray chỉ từ repo này.
Bản Coolray tách khỏi bản EX2 (repo `meomeo2099/THTVoice`); mã, tài liệu và script
riêng của EX2 đã bị xoá khỏi repo này (01/10/2026).

Cập nhật: 01/10/2026.

## 1. Đọc gì trước (theo thứ tự)

| # | File | Dùng để |
|---|---|---|
| 1 | [`AGENTS.md`](../AGENTS.md) | Quy tắc bắt buộc |
| 2 | Tài liệu này | Quy trình một lát cắt, việc tiếp theo |
| 3 | [`docs/COOLRAY_DEPLOYMENT_PLAN.md`](COOLRAY_DEPLOYMENT_PLAN.md) | Kiến trúc, lộ trình C0–C6, cách cài lên xe — **đây là backlog** |
| 4 | [`docs/COOLRAY_IHU624G_PROPERTIES.md`](COOLRAY_IHU624G_PROPERTIES.md) | Property/area Coolray và nguồn trong tham chiếu |
| 5 | `app/src/main/java/com/voicegeely/app/car/CoolrayProperties.kt` | Hằng số property đang dùng |
| 6 | `app/src/main/java/com/voicegeely/app/voice/CoolrayCapabilities.kt` | Nhóm lệnh được phép đi xuống xe |
| 7 | [`docs/reference/voice/VOICE_PHRASES.csv`](reference/voice/VOICE_PHRASES.csv) | Mẫu câu tiếng Việt cho test độ phủ parser |

## 2. Quy tắc không được phá

1. **Nguồn property/area duy nhất:** phần Coolray của GalaxyVoiceAssistant (`CarModel.COOLRAY`), đã chép vào `docs/COOLRAY_IHU624G_PROPERTIES.md`. Không dùng số của EX2, E5 hay Starship 7.
2. **Head unit:** IHU624G (tên trong tham chiếu) hoặc IHU689G (tên chủ repo dùng). Script, CI và gói test phần cứng mang tên IHU689G.
3. **Parser giữ nguyên như bản gốc.** Lệnh chưa có mapping Coolray vẫn được hiểu, nhưng `CoolrayCapabilities` trả `Rejected` ("chưa hỗ trợ trên xe này") và không ghi gì xuống xe.
4. **Không chờ test xe.** Phần chưa chạy trên xe ghi `REFERENCE`; matrix giữ `NOT_TESTED`; không tự ghi `CONFIRMED`/`PASS`.
5. **Chỉ báo thành công khi đọc lại khớp:** `PARSE → ACK → SAFETY → EXECUTE → VERIFY → RESULT`.
6. **Branding:** chỉ `THT Voice`; application ID `com.tht.voice.coolray2025`.

## 3. Quy trình một lát cắt

1. `git fetch --prune`, làm từ `main` mới nhất.
2. Chọn mục chưa tick đầu tiên trong `docs/COOLRAY_DEPLOYMENT_PLAN.md`, theo thứ tự C2 → C6.
3. Bật một nhóm lệnh mới (C4):
   1. Ghi property/area và nguồn vào `docs/COOLRAY_IHU624G_PROPERTIES.md`.
   2. Thêm hằng số vào `CoolrayProperties.kt`.
   3. Viết executor (đọc trước, cổng an toàn, ghi, đọc lại) và nối vào `VehicleCommandRouter`.
   4. Thêm nhóm vào `ENABLED` trong `CoolrayCapabilities`.
   5. Test JVM: parser, executor với fake vehicle, router.
4. Thêm ca `NOT_TESTED` vào `docs/phase10/IHU689G_TEST_MATRIX.csv` nếu là chức năng xe.
5. Tick mục trong `COOLRAY_DEPLOYMENT_PLAN.md`, commit, push, chờ CI "Android build" xanh.

## 4. Việc tiếp theo (01/10/2026)

- **C2:** nhận diện head unit lúc khởi động (`ro.product.*` chứa `IHU624G` hoặc `IHU689G`); không khớp thì chặn mọi lệnh ghi xuống xe. Chọn audio usage cho TTS.
- **C3:** chờ log `adb logcat` từ xe thật để chuyển `REFERENCE` → `VERIFIED`.
- **C4:** cửa sổ trời/rèm, đèn, ghế, độ sáng màn hình, nắp xăng — property đã có trong catalog.

### Cập nhật 16/10/2026 — đồng bộ lõi THTVoice tới #329

Chủ repo yêu cầu đưa sang bản Coolray mọi phần dùng được của THTVoice (#292–#329, nền so sánh THTVoice f64449d): nút voice (biến thể action, nhật ký tín hiệu, Kiểm tra nút voice — **không** dùng tín hiệu VHAL 557884981 của EX2), tự kết nối lại xe, giọng đọc (cắt tiếng dư, lưu sẵn câu, đọc từ kỹ thuật), câu nói tiếp, tên gọi riêng + kịch bản + sao chép/gửi, nhiều lệnh một câu, nghe tiếp sau từ nối, dừng nghe sau 4 s không nói, hỏi lại kính, gợi ý lệnh khi không hiểu, cách nói vùng miền, bản test ký khóa chính thức. Riêng Coolray: "mở cốp" chạy luôn "mở cốp sau" (chỉ có cốp sau); "tình trạng xe" đọc xăng + quãng đường + kính (chưa có mapping cửa/khóa). Phần EX2 (pin/sạc/hồi năng lượng/cốp trước/khóa cửa) không đưa sang.
