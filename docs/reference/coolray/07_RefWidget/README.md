# REF-WIDGET — nguồn tham chiếu thứ hai (09/10/2026)

**REF-WIDGET** = plugin widget mã nguồn mở cho launcher Flyme Auto (`com.flyme.auto.launcher`)
trên một xe Geely chạy xăng cùng nền tảng ECARX. Tên dự án gốc được thay bằng nhãn trung tính
theo quy định của repo. Chủ repo đồng ý dùng làm nguồn thứ hai ngày 09/10/2026.

Không chép mã, asset hay chuỗi giao diện. Chỉ lấy số hiệu thuộc tính, area, giá trị ghi và
công thức quy đổi. Mọi giá trị ở trạng thái **REFERENCE**.

## Cách dùng trong repo

- Dùng khi đường GalaxyVoiceAssistant **không có** mapping (sấy kính trước, sưởi vô lăng,
  các thông tin xe chỉ đọc).
- Hoặc làm **đường dự phòng một lần** sau đường Galaxy (sấy kính sau, sưởi ghế), có đọc lại.
- Không dùng thay cho số Galaxy đã có.

## Nhận xét về nền tảng

- REF-WIDGET ghi **mã lệnh MCU** để bật bộ sưởi, còn thuộc tính dạng chuẩn chỉ dùng để
  giao diện điều hoà gốc **đọc lại** trạng thái. Đúng kiểu ghi một mã, đọc ở mã khác.
- Mã đọc lại sấy kính sau `354419988` area 2 trùng với Galaxy Coolray; mã lệnh `591400996`
  area 2 trùng với đường dự phòng của bản EX2. Đó là lý do coi cùng nền tảng.
- `557875206` là **điện áp ắc quy** ở REF-WIDGET nhưng là **đèn pha thông minh** ở EX2:
  cùng số, khác nghĩa theo xe. Bản Coolray chỉ **đọc** số này, không bao giờ ghi.

## Bảng số hiệu

| Chức năng | Ghi (property / area / giá trị) | Đọc lại |
|---|---|---|
| Sấy kính sau | `591400996` (0x23401024) / 2 / 1 bật, 0 tắt | `354419988` / 2 (bool) |
| Sấy kính trước | `557846585` (0x21401039) / 0 / 1 bật, 2 tắt | `268698880` (0x10040500) / 117 |
| Sưởi vô lăng | `289408269` (0x1140050D) / 0 / 1 bật, -1 tắt | cùng mã: 1 bật, 0 hoặc -1 tắt |
| Sưởi ghế | `624955432` (0x25401028) / 1 lái, 4 phụ / 0..3 | cùng mã |

| Thông tin (chỉ đọc) | Property / area | Kiểu và quy đổi |
|---|---|---|
| Quãng đường còn đi được | `289407752` / 0 | float km |
| Mức xăng | `557884777` / 0 | int % (0..100) |
| ODO | `289407492` / 0 | float km |
| Tiêu hao trung bình | `658548325` / 1 | float L/100 km |
| Tốc độ trung bình | `658547480` / 1 | float km/h (chưa gắn lệnh) |
| Quãng đường chuyến đi | `658548345` / 1 | float km |
| Thời gian chuyến đi | `658548346` / 1 | int phút (chưa gắn lệnh) |
| Nhiệt độ nước làm mát | `557875223` / 0 | int, °C = raw × 0,75 − 48 |
| Điện áp ắc quy 12 V | `557875206` / 0 | int, V = raw / 10 |
| Áp suất lốp TT / TP / ST / SP | `557884502..557884505` / 0 | float kPa (đọc ra bar = kPa / 100) |
| Nhiệt độ lốp TT / TP / ST / SP | `557884512..557884515` / 0 | int °F, °C = (raw − 32) / 1,8 (chưa gắn lệnh) |
| Km còn lại tới bảo dưỡng | `557884772` / 0 | int km |
| Ngày còn lại tới bảo dưỡng | `557885043` / 0 | int ngày |
