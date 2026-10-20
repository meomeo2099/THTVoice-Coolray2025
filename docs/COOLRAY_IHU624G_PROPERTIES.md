# Coolray (IHU624G) — bảng thuộc tính xe

Bản THT Voice cho Coolray là nhánh riêng, không dùng chung cấu hình với EX2.

**Nguồn duy nhất:** phần Coolray trong GalaxyVoiceAssistant
(`github.com/sniiick/GalaxyVoiceAssistant`, `CarModel.COOLRAY` = chuỗi `IHU624G`).
Chỉ lấy hai loại giá trị:
- giá trị ghi đè riêng cho Coolray (`if (CarModel.isCoolray) ...`);
- giá trị handler dùng chung cho mọi xe, nên cũng chạy trên Coolray.

Giá trị chỉ dành cho E5 / Starship 7 (ví dụ cốp `373295873`, kính `322964416`,
đèn `356544592`, độ sáng `624981307`, massage ghế) và toàn bộ catalog EX2 **không** dùng.

Code:
- `car/CoolrayProperties.kt` — chỉ chứa các giá trị trong bảng dưới.
- Catalog và executor EX2 đã xoá khỏi repo (01/10/2026). Parser vẫn hiểu câu lệnh EX2 để trả lời rõ "xe này chưa hỗ trợ".
- `voice/CoolrayCapabilities.kt` — nhóm lệnh được phép ghi/đọc.

Mọi giá trị đều ở trạng thái **REFERENCE** (lấy từ mã tham chiếu, chưa thử trên xe thật).

## Đã bật cho giọng nói

| Nhóm | Thuộc tính | Area | Giá trị | Nguồn trong tham chiếu |
|---|---|---|---|---|
| Nguồn điều hòa | 354419984 (HVAC_POWER_ON) | 5 | bool | AcControlHandler |
| Máy nén A/C | 354419973 (HVAC_AC_ON) | 117 | bool | AcControlHandler |
| Auto A/C | 354419978 (HVAC_AUTO_ON) | 1 | bool | AcControlHandler — chưa dùng trong luồng typed |
| Nhiệt độ | 358614275 (HVAC_TEMPERATURE_SET) | 1 (SEAT_ROW_1_LEFT) | float 15,5..28,5 °C, bước 0,5 (khớp REF-VOICE) | ChangeAcTempHandler |
| Tốc độ quạt | 356517120 (HVAC_FAN_SPEED) | 5 | 1..8 (Coolray `maxSpeed = 8`) | ChangeFanSpeedHandler |
| Hướng gió | 356517121 (HVAC_FAN_DIRECTION) | 1 | bitmask 1 mặt / 2 chân / 4 kính, tổng 1..7 | AcControlHandler.setAcDirection |
| Sấy kính sau (+ gương) | 354419988 | 2 | bool | AcControlHandler.setWindowHeat |
| Kính cửa | 591405227 (Coolray ghi đè) | 16 / 64 / 256 / 1024 | 0 đóng .. 100 mở; vùng hoạt động 12..88, bước 4 | WindowControlHandler |
| Cửa sổ trời | 591405227 | 65536 | 0 đóng .. 100 mở; vùng hoạt động 12..88, bước 4; mở kính trời thì mở rèm ≥ kính + 4 | WindowControlHandler.handleSunroof |
| Rèm che nắng | 591405227 | 131072 | 0..100; tối thiểu 16 (Coolray ghi đè), tối đa 88, bước 4 | WindowControlHandler.handleCurtain |
| Đèn trong xe | 356519684 (Coolray ghi đè) | 1, 2, 4, 16, 64 (ghi mọi area) | 1 bật / 0 tắt | LightControlHandler |
| Đèn ngoài | 557871126 | 0 | 0 tắt / 1 định vị / 3 chiếu gần (cốt) | ExteriorLightControlHandler |
| Đèn sương mù | 289410578 | 0 | 1 bật / 0 tắt | ExteriorLightControlHandler |
| Sưởi ghế | 356517131 | 1 lái / 4 phụ | 0 tắt, 1..3; bật không nói mức = 2 | SeatClimateHandler |
| Thông gió ghế | 356517139 | 1 lái / 4 phụ | 0 tắt, 1..3; bật không nói mức = 2 | SeatClimateHandler |
| Tốc độ xe | 291504647 (PERF_VEHICLE_SPEED) | 0 | float | TrunkControlHandler (cổng an toàn) |

## Nguồn thứ hai: REF-WIDGET (chủ repo đồng ý 09/10/2026)

Chi tiết và lý do: `docs/reference/coolray/07_RefWidget/README.md`. Chỉ dùng khi Galaxy không có
mapping, hoặc làm đường dự phòng **một lần** sau Galaxy, luôn đọc lại trước khi báo thành công.

| Nhóm | Ghi | Đọc lại | Cách dùng |
|---|---|---|---|
| Sấy kính sau | `591400996` area 2, 1/0 | `354419988` area 2 | Dự phòng khi ghi `354419988` không đọc lại đúng |
| Sấy kính trước | `557846585` area 0, 1 bật / 2 tắt | `268698880` area 117 | Đường duy nhất |
| Sưởi vô lăng | `289408269` area 0, 1 bật / -1 tắt | cùng mã (1 bật, 0 tắt; -1 = chưa xác nhận) | Đường duy nhất |
| Sưởi ghế | `624955432` area 1 / 4, 0..3 | cùng mã | Dự phòng khi `356517131` không xác nhận |

Truy vấn trạng thái cho phép: tốc độ, số, nhiệt độ cài đặt, mức quạt (Galaxy/AOSP) và
quãng đường còn lại, mức xăng, ODO, quãng đường chuyến đi, tiêu hao trung bình, nhiệt độ nước làm mát,
điện áp ắc quy, áp suất lốp, lịch bảo dưỡng (REF-WIDGET, chỉ đọc).
Các truy vấn khác (pin xe điện, cửa, khóa, sạc, dây an toàn, đèn, ADAS…)
trả lời "Xe này chưa hỗ trợ đọc thông tin đó." và không đọc gì từ xe.

## Ngoài tham chiếu (giữ vì an toàn)

| Thuộc tính | Area | Dùng cho |
|---|---|---|
| 289408001 (CURRENT_GEAR, AOSP) | 0 | Chỉ đọc, cổng an toàn "đỗ số P" và truy vấn số. Tham chiếu chỉ kiểm tra tốc độ < 5; bản này chặt hơn. Không bao giờ ghi. |

## Đã có trong catalog, chưa gắn lệnh giọng nói

| Nhóm | Thuộc tính | Area | Giá trị | Nguồn |
|---|---|---|---|---|
| Nắp bình xăng | 287310600 | 0 | bool true = mở — bỏ, không gắn lệnh (chủ repo 01/10/2026) | FuelDoorHandler |
| Độ sáng màn hình | 687997952 (Coolray ghi đè) | 2 | int 0..150 — bỏ, không gắn lệnh (chủ repo 01/10/2026) | ChangeScreenBrightnessHandler |
| Độ sáng tự động | 555775150 | 0 | bool (tham chiếu chỉ ghi true) | ChangeScreenBrightnessHandler |

Tham chiếu còn dùng chung `557871372` (chế độ lái) và `555774850` (sạc) cho mọi xe,
nhưng đó là chức năng của xe điện Galaxy; Coolray là xe xăng nên không đưa vào.

## Nguồn thứ ba: REF-SYSUI (16/10/2026)

Giao diện hệ thống / launcher của chính head unit (`docs/reference/coolray/08_RefSystemUI/README.md`).
Chỉ dùng property AAOS chuẩn có area rõ, đọc lại trước khi báo thành công. Function ID ECARX chưa dùng.

| Nhóm | Ghi / đọc | Area | Giá trị | Cách dùng |
|---|---|---|---|---|
| Sấy kính trước | `354419975` (HVAC_DEFROSTER) | 117 | bool | Đường chính; không xác nhận thì gửi lệnh MCU REF-WIDGET `557846585` một lần. Đọc lại `354419975` trước, `268698880` sau |
| Lấy gió trong / ngoài | `354419976` (HVAC_RECIRC_ON) | 117 | true = gió trong | Đường duy nhất |
| Làm lạnh nhanh (Max A/C) | `354419974` (HVAC_MAX_AC_ON) | 117 | bool | Lệnh mới: "bật/tắt làm lạnh nhanh", "max A/C" |
| Sưởi vô lăng | `289408269` | 0 | 0..3 (0 tắt) | Bật ghi 1; tắt ghi 0, xe từ chối thì thử `-1` (REF-WIDGET) một lần |

Chưa dùng: đồng bộ nhiệt độ hai bên `354419977` (tài liệu chưa có area), các function ID ECARX (massage, ion, nước hoa, ECO, sưởi nhanh, chế độ quét gió…).

## Đã tắt trên Coolray (không có mapping trong tham chiếu)

Điều hòa tiết kiệm, tự động đảo gió của xe (lệnh "đảo gió" dùng đảo gió định kỳ của app),
cốp sau `554768640` area 0 (bỏ theo quyết định chủ repo 01/10/2026; mã chỉ còn trong catalog), chế độ lái/phanh tái sinh, cảm giác phanh, sạc, cốp trước, khóa cửa, đèn viền,
cài đặt tiện ích, chế độ đỗ, chỉnh độ cao đèn, đèn pha thông minh.
Router trả `Rejected` với câu giải thích, không ghi gì xuống xe.

Muốn bật một nhóm: thêm mapping Coolray có nguồn vào bảng trên, chuyển giá trị
vào `CoolrayProperties`, rồi thêm nhóm vào `ENABLED` trong `CoolrayCapabilities`, kèm test.

## Lưu ý đơn vị tốc độ

Cổng an toàn coi đỗ là `số P` và tốc độ < 1. Nếu IHU624G trả tốc độ theo m/s
thay vì km/h thì ngưỡng vẫn an toàn vì điều kiện số P là bắt buộc.
