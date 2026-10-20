# THT Voice — Popup Voice final

> Trạng thái: **APPROVED / AUTHORITATIVE**
>
> Visual chuẩn: [`POPUP_FINAL_APPROVED.jpg`](POPUP_FINAL_APPROVED.jpg)

## Mục tiêu
Popup này là giao diện Voice hiển thị khi người dùng đang ở **ngoài màn hình chính THT Voice** như bản đồ, media hoặc app khác.

## Bố cục đã chốt

### Cụm trái
- Mic tròn phát sáng.
- Tên `THT Voice`.
- Trạng thái ngắn: `Đang nghe` / `Đang xử lý` / `Hoàn tất` / `Lỗi`.

### Cụm giữa
- Dòng text nhận dạng realtime, chỉ 1 dòng khi có thể.
- Dòng text final ngắn gọn nằm ngay dưới.

### Cụm phải
- Trạng thái xử lý hoặc kết quả thực thi.
- Có loader nhỏ khi đang xử lý.
- Nút thu nhỏ.
- Nút đóng.

## Hình thức
- Thanh ngang nhỏ gọn.
- Nền dark/navy.
- Viền neon cyan/blue.
- Mặc định đặt **phía dưới màn hình**, căn giữa ngang.
- Không che đáng kể navigation/media/app nền.
- Có thể kéo nếu setting cho phép.
- Không mở full-screen khi chỉ cần phản hồi lệnh ngắn.

## Lifecycle

```text
Hey THT / nút Voice trên xe / Nghe thử
        ↓
Popup hiện
        ↓
LISTENING
        ↓
Realtime text
        ↓
PROCESSING
        ↓
SUCCESS / ERROR
        ↓
TTS hoặc completion sound
        ↓
Auto-hide theo setting
```

## Mặc định
- Hiển thị popup: **Bật**.
- Vị trí: **Dưới**.
- Tự ẩn: **2 giây** sau khi có kết quả ổn định.
- Cho phép kéo: **Bật**.
- Kiểu: **Thanh ngang compact**.

## Quy tắc runtime
- Popup chỉ phản ánh state thật từ Voice runtime; không giả trạng thái thành công.
- `Confirmed` mới được hiển thị như hoàn tất chắc chắn.
- `AcceptedUnverified` phải dùng wording trung tính như `Đã gửi lệnh` / `Chưa xác nhận trạng thái`.
- Lỗi phải ngắn gọn, không che app nền lâu hơn cần thiết.
- Nếu người dùng tắt popup, KWS/nút Voice trên xe/ASR/TTS vẫn hoạt động bình thường.

## Definition of Done
- [ ] Match visual `POPUP_FINAL_APPROVED.jpg` về hierarchy và density.
- [ ] Đúng vị trí bottom-center trên 1920×1080.
- [ ] Không che các control quan trọng của Google Maps/VietMap/media.
- [ ] LISTENING / PROCESSING / SUCCESS / ERROR đồng bộ runtime.
- [ ] Partial/final text không làm vỡ layout.
- [ ] Thu nhỏ/đóng hoạt động.
- [ ] Drag + persist position hoạt động khi bật setting.
- [ ] Auto-hide 1/2/3 giây hoạt động đúng.
- [ ] Test Hey THT, nút Voice trên xe và manual listen.
- [ ] Hardware validation trên IHU689G trước khi gắn nhãn UI stable.
