# THT Voice — UI baseline đã chốt

> Trạng thái: **APPROVED / AUTHORITATIVE**
>
> Đây là nguồn chuẩn cho UI THT Voice. UI người dùng chỉ dùng branding **THT Voice**; không hiển thị tên, logo, badge hoặc wake word mang thương hiệu hãng xe.

## 1. Cấu trúc chính

Sau khi Security Gate mở, THT Voice có đúng **6 mục**:

1. **Trang chủ**
2. **Trợ lý**
3. **Ứng dụng**
4. **Cài đặt**
5. **Ủng hộ**
6. **Giới thiệu**

Popup Voice là overlay, không phải menu thứ 7. Security / Activation là gate trước navigation, cũng không phải menu.

## 2. Style chung

- Landscape 1920×1080, ưu tiên không cần cuộn ở bố cục mặc định.
- Màn hình khác kích thước tự co giãn (`ui/ResponsiveLayout.kt`): thu nhỏ toàn UI về tỉ lệ 1920×1080 nhưng không dưới 72%; thanh menu 232 → 190 dp hoặc chỉ biểu tượng; Cài đặt 1 cột khi hẹp; lưới app 1–3 cột; tự cuộn khi thiếu chiều cao; khối cố định (QR, mic, ảnh xe) có giới hạn theo màn hình. Ở 1920×1080 (và lớn hơn) bố cục giữ nguyên như bản đã duyệt.
- Dark/navy, accent cyan/blue, card bo góc, tương phản cao.
- Ảnh xe phải trung tính; **không chữ/logo/badge hãng**.
- Branding chỉ dùng `THT Voice` và mô tả trung tính như `Trợ lý giọng nói tiếng Việt cho xe của bạn`.
- Tên IHU689G chỉ được dùng trong tài liệu kỹ thuật/adapter, không dùng làm branding UI.

## 3. Trang chủ

- Hero/banner THT Voice + ảnh xe trung tính.
- Gợi ý kích hoạt: **Hey THT** và **nút Voice trên xe**.
- Quick actions: Trợ lý / Ứng dụng / Cài đặt / Ủng hộ.
- Trạng thái: Wake word / Popup / Media / Bản đồ / ASR / TTS.
- Hoạt động gần đây.
- Preview popup Voice.

## 4. Trợ lý

- Mic lớn + trạng thái runtime thật.
- Kích hoạt nhanh: **Hey THT** / **nút Voice trên xe** / nghe thủ công.
- State machine: Sẵn sàng → Đang nghe → Đang xử lý → Hoàn tất/Lỗi.
- Text realtime/final và kết quả thực thi.
- Lệnh nhanh: Điều hòa / Kính / Media / Bản đồ / Xe.
- Lịch sử lệnh gần đây.
- Trạng thái ASR / KWS / Mic / VHAL / TTS.
- Wake word production hiện tại: **Hey THT**.

## 5. Ứng dụng

Nhóm: Tất cả / Yêu thích / Media / Bản đồ / Khác.

Chức năng:
- Tìm app đã cài.
- Icon + tên app.
- Mở app.
- Yêu thích.
- Đặt Media/Bản đồ mặc định.
- Làm mới.
- Thử lệnh Voice.

Media baseline: YouTube / Vanced / ReVanced / Morphe / SmartTube / Zing MP3.
Bản đồ baseline: Google Maps / VietMap Live / Waze.

## 6. Cài đặt

### Popup
- Hiển thị popup: Bật/Tắt.
- Vị trí: Trên / Giữa / **Dưới**.
- Auto-hide: 1 / **2** / 3 giây.
- Kéo popup: Bật/Tắt.
- Kiểu mặc định: **Thanh ngang**.
- Preview popup.

### Voice
- Wake word Hey THT.
- Phản hồi giọng nói Bật/Tắt.
- Giọng TTS.
- Độ nhạy mic: Thấp / **Chuẩn** / Cao.
- Trailing silence: 0,5 / **0,7** / 0,9 / 1,2 giây.
- Max listen: 6 / 8 / 10 / **12** giây.
- Media mặc định.
- Bản đồ mặc định.
- Nút **Lưu** và **Khôi phục mặc định**.

## 7. Ủng hộ

- Tên người nhận: **TRAN HUU TAI**.
- Ngân hàng: **MBBank**.
- STK: **67777777778**.
- QR lớn, không trang trí bên trong vùng QR.
- Nút Sao chép STK.
- QR local trong APK khi implementation hoàn tất để không phụ thuộc mạng.

## 8. Giới thiệu

- THT Voice + ảnh xe trung tính.
- Feature summary.
- Version/system info đọc runtime khi có thể.
- Kiểm tra cập nhật / Changelog / Hướng dẫn / GitHub.
- Không hard-code thông số xe/hãng vào branding.

## 9. Security / Activation Gate

Visual/spec authoritative:
- `docs/ui/SECURITY_ACTIVATION_UI_APPROVED.jpg`
- `docs/ui/SECURITY_ACTIVATION_UI.md`

Luồng:

```text
Mở app
  ↓
Kiểm tra license + binding
  ↓
Hợp lệ? ── Có ──> mở UI 6 menu + Voice core
  │
  Không
  ↓
Security / Activation full-screen
```

Khi gate chưa mở:
- Không KWS/Hey THT.
- Không nút Voice trên xe.
- Không mic/ASR.
- Không điều khiển xe.
- Không Apps launcher để bypass.
- Không tạo UI 6 menu phía sau.

Binding baseline hiện tại: DeviceIdentity + Android Keystore + server validation.
Mục tiêu tăng cường: VIN + device fingerprint + Keystore identity + server-signed license.
VIN không được hard-code/đoán; chỉ bắt buộc sau test thật.

## 10. Popup Voice final

Visual/spec authoritative:
- `docs/ui/POPUP_FINAL_APPROVED.jpg`
- `docs/ui/POPUP_FINAL.md`

- Thanh ngang compact, bottom-center.
- 3 cụm: mic/state → realtime/final → processing/result + minimize/close.
- Lifecycle: **Hey THT / nút Voice trên xe / Nghe thử → LISTENING → PROCESSING → SUCCESS/ERROR → auto-hide**.
- Chỉ phản ánh kết quả runtime thật; không giả trạng thái thành công.

## 11. Bộ visual final

Bộ giao diện final cần có:
- `01-home`
- `02-assistant`
- `03-apps`
- `04-settings`
- `05-support`
- `06-about`
- `07-popup-overlay`
- `08-security-activation`

**Quy tắc final:** tất cả ảnh/user-facing copy phải không có tên/logo/badge hãng xe. Ảnh cũ không đáp ứng quy tắc này chỉ được coi là lịch sử thiết kế, không phải visual authoritative.

## 12. Definition of Done

- [ ] Security gate không thể bypass.
- [ ] Có đúng 6 menu sau khi gate mở.
- [ ] Không branding hãng xe trong UI người dùng.
- [ ] Không wake word mang thương hiệu hãng xe trong UI.
- [ ] Landscape 1920×1080 không vỡ layout.
- [ ] Settings mặc định không scroll.
- [ ] Popup bottom compact hoạt động đúng.
- [ ] Runtime state không fake.
- [ ] Apps/default apps nối đúng router.
- [ ] QR quét được trên màn hình xe thật.
- [ ] VIN chỉ hiển thị từ runtime thật.
- [ ] Hardware validation IHU689G trước khi gắn nhãn stable/release-ready.
