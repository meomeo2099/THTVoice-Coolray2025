# Đồng bộ lõi từ THTVoice EX2 — 03/10/2026

Coolray trước đồng bộ: `c83a556d0beb61716056c31613489133b69efc44`.
EX2 đối chiếu đến: `0a5e1f25d8bd9c77b0f629aed8c3992ed6ad652c`.
Các thay đổi #255–#259 đã có trên Coolray; đợt này chỉ đồng bộ #260–#265.

| Nguồn EX2 | Nội dung đưa sang Coolray |
|---|---|
| #260 / d75011a | Chặn lan truyền broadcast ECARX có thứ tự khi đã xử lý nút Voice; cho chọn THT Voice làm trợ lý Android. |
| #261 / 200788b | Tùy chọn nút Voice khi có điện thoại USB Apple hoặc Android Auto AOA; mặc định ưu tiên THT Voice. Nhận diện bằng USB, chưa xác minh projection thực tế; không nhận diện kết nối không dây. |
| #262 / bfbf711 | Hey Geely mặc định cho cấu hình mới; giữ lựa chọn đã lưu. Thêm cách phát âm tiếng Việt, bỏ wake phrase khỏi đầu câu, retry mic và nhường mic khi gọi điện/bị hệ thống tắt âm ghi. |
| #263 / 70ca7db | Lời nhắn có thể sửa và lưu, sao chép kèm mã kích hoạt; D1 tự tạo app_settings khi cần. |
| #264 / 083309a | Timeout lệnh 15 giây, phục hồi phiên quá 35 giây khi nhấn lại, alias đám → đóng trong ngữ cảnh cửa/kính. |
| #265 / 0a5e1f2 | Đọc chữ ký APK qua API mới, API cũ và apksig; chữ ký đọc được nhưng khác vẫn chặn. Không đọc được thì chuyển kiểm tra chữ ký cho trình cài hệ thống. |

## Phạm vi Coolray

Không thay đổi `CoolrayProperties`, `CoolrayCapabilities`, `VehicleCommandRouter`, giới hạn nhiệt độ, package `com.tht.voice.coolray2025`, máy chủ/license c1 hay nguồn cập nhật Coolray. Không đưa actuator/mapping EX2 vào Coolray.

## Kiểm tra

- Áp dụng từng commit trên snapshot Coolray, không có xung đột patch.
- Worker: 22/22 test thành công trước khi tạo PR.
- Android: CI `Android build` của PR chạy unit tests, kiểm tra đóng gói/quyền và assemble debug.
- Kiểm tra phần cứng Coolray vẫn UNVERIFIED; không coi CI là xác nhận xe thật.
- Thay đổi Worker mới có trong mã nguồn; cần deploy Worker riêng để giao diện web đang chạy nhận lời nhắn mới.
- Không phát hành APK release trong đợt đồng bộ mã nguồn này.
