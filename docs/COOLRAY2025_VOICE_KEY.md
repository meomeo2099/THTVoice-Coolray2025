# Kiểm tra nút voice — Coolray 2025

Trạng thái: **REFERENCE / UNVERIFIED trên xe thật**. Nguồn triển khai:
`COOLRAY2025_VOICE_SPEC.md`, mục 8. Mã phím thực tế trên xe cần xác nhận.

## Thay đổi

- Giữ hai biến thể broadcast ECARX hiện có và đường trợ lý mặc định.
- Nhận `KEYCODE_VOICE_ASSIST` (231), `KEYCODE_ASSIST` (219),
  `KEYCODE_SEARCH` (84) khi giao diện THT Voice đang mở.
- Thêm dịch vụ Trợ năng để nhận các phím đó khi app khác đang mở.
  Người dùng tự bật; dịch vụ không đọc nội dung màn hình.
- Chỉ DOWN đầu tiên kích hoạt nghe; giữ nút, lặp phím và UP không mở thêm phiên.
  Các đường nhận dùng chung debounce. Phím khác được chuyển tiếp.
- Áp dụng lựa chọn nhường nút cho Android Auto/CarPlay ở bộ định tuyến chung,
  bao gồm phím Android, broadcast, trợ lý mặc định và lệnh mở service ECARX.

## Cách thử

1. Cài APK có thay đổi này, mở THT Voice và cấp quyền micrô.
2. Vào **Cài đặt → Nhận nút voice khi chạy nền → Mở cài đặt**.
   Trong Trợ năng bật **THT Voice – Nút voice**.
3. Trở lại **Kiểm tra nút voice**: kiểm tra trạng thái kết nối dịch vụ.
4. Bấm/thả nút voice khi THT Voice đang mở, sau đó khi app khác đang mở.
   Mỗi lần bấm chỉ mở một phiên nghe; giữ nút không mở liên tục.
5. Kiểm tra âm lượng, phát/tạm dừng và Back vẫn hoạt động.
6. Nếu dùng Android Auto/CarPlay, thử cả hai lựa chọn ưu tiên nút voice.
   Việc nhận diện kết nối dùng cơ chế USB hiện có; chưa xác minh kết nối không dây.
7. Khởi động lại màn, kiểm tra dịch vụ Trợ năng và lặp lại bước 4.

Nếu không nhận, vào **Kiểm tra nút voice → Sao chép**. Nhật ký cho biết nguồn
`activity_key` / `accessibility_key`, `keyCode`, `scanCode`, DOWN/UP và số lần lặp.
Không có tín hiệu chưa đủ để kết luận nút bị hỏng: firmware có thể chặn phím
trước khi tới app, dịch vụ Trợ năng có thể chưa kết nối hoặc mã phím khác.

Khi có ADB, lấy mã nút theo đặc tả:

```sh
adb shell getevent -l
adb logcat -v time -s THTVoiceECARX
```

`getevent` ghi sự kiện Linux; mã Linux không phải Android `keyCode`.
Không tự thêm mã phím riêng của firmware khi chưa đối chiếu key layout/log.

## Kiểm thử phần mềm

`VoiceKeyEventPolicyTest` kiểm tra phím được hỗ trợ, giữ/thả/hủy phím, nhường phím,
tách thiết bị, dọn trạng thái và debounce với broadcast.
CI `Android build` chạy cùng các kiểm thử định tuyến, projection và nhật ký hiện có.
Unit test/CI không xác nhận khả năng nhận nút trên xe thật.
