# THT Voice — No-brand UI final

> Trạng thái: **APPROVED / AUTHORITATIVE**

Mọi bề mặt người dùng của ứng dụng vẫn dùng branding chính **THT Voice**.

## Bắt buộc

- Không dùng tên hãng xe làm **tên ứng dụng, logo, badge, hero branding, package hoặc release branding**.
- Không hiển thị logo/badge hãng trên ảnh xe như một phần nhận diện THT Voice.
- Nút phần cứng được gọi trung tính là **Nút Voice trên xe**.
- Mô tả chuẩn: `Trợ lý giọng nói tiếng Việt cho xe của bạn`.
- VIN/model chỉ hiển thị nếu là dữ liệu runtime thật và cần cho Security; không dùng làm branding.

### Ngoại lệ chức năng — wake phrase compatibility

Theo quyết định sản phẩm hiện tại, Settings/KWS được phép hiển thị các **câu đánh thức chức năng** sau:

- `Hey THT` — mặc định.
- `Hello Geely` — tùy chọn.
- `Hey Geely` — tùy chọn.

Hai cụm có tên hãng chỉ là **keyword compatibility để kích hoạt trợ lý**, không thay đổi branding của ứng dụng: app vẫn tên `THT Voice`, không dùng logo/badge hãng và không đổi package/release branding.

Mỗi thời điểm chỉ một wake profile được active. Xem `docs/WAKE_WORD.md`.

## Bộ visual authoritative

1. `01-home` — Trang chủ
2. `02-assistant` — Trợ lý
3. `03-apps` — Ứng dụng
4. `04-settings` — Cài đặt
5. `05-support` — Ủng hộ
6. `06-about` — Giới thiệu
7. `07-popup-overlay` — Popup Voice
8. `08-security-activation` — Security / Activation

Board source-controlled: `docs/ui/UI_BASELINE_BOARD.svg`.
Popup authoritative: `docs/ui/POPUP_FINAL_APPROVED.jpg` + `docs/ui/POPUP_FINAL.md`.
Security authoritative: `docs/ui/SECURITY_ACTIVATION_UI_APPROVED.jpg` + `docs/ui/SECURITY_ACTIVATION_UI.md`.

## Phạm vi không đổi ở bước này

Tên package/source kỹ thuật cũ như `com.voicegeely...`, tên adapter phần cứng, property alias, tài liệu hardware-validation hoặc HTTP header tương thích cũ **không được xem là branding UI**. Chúng chỉ được đổi trong một migration kỹ thuật riêng có regression test đầy đủ.

## Kiểm tra trước merge

- [x] README mô tả THT Voice trung tính.
- [x] App/release branding vẫn là THT Voice.
- [x] Security/Activation copy trung tính.
- [x] Ảnh xe trong visual final không dùng badge/logo hãng làm điểm nhận diện.
- [x] Wake phrase compatibility được giới hạn ở Settings/KWS và tài liệu liên quan.
- [ ] Kiểm tra thực tế toàn bộ màn hình trên IHU689G ở Phase 10.
