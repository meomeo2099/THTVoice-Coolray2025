# Coolray 2025 — bảng thuộc tính xe (SX11-LA / IHU730P)

Nguồn nền tảng: dữ liệu lấy trực tiếp từ đầu xe Coolray 2025 của chủ repo
(`getprop`, `dumpsys car_service`, `/system/framework/AdapterAPI.jar`, 20/10/2026).
Mọi giá trị ở trạng thái **REFERENCE**: có trong cấu hình của chính đầu xe, nhưng chưa ghi + đọc lại bằng giọng nói trên xe.

## Đầu xe

| Mục | Giá trị |
|---|---|
| `ro.product.model` / `ro.product.name` | `SX11-LA` / `IHU730P` (dùng trong `car/CoolrayHeadUnit.kt`) |
| Nền tảng | ECARX `ecarxp`, MediaTek 8 nhân, RAM ~3,8 GB |
| Android | 9 (API 28), build `user`, `ro.debuggable=1`, `dev-keys` |
| Màn hình | 1920×720, 160 dpi |
| Chứng chỉ platform (`framework-res.apk`) | SHA-256 `C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8` (khoá test AOSP) |
| Trợ lý giọng nói gốc | Không có |

## Hai đường điều khiển

1. **VHAL chuẩn (`CarPropertyManager`)**: catalog cấu hình điều hoà và đọc trạng thái; runtime điều hoà dùng OEM facade từ 1.0.7. Sưởi bị loại theo trang bị chủ xe. Hằng số trong `car/CoolrayProperties.kt`.
2. **ECARX AdaptAPI 1.1.18.9 (`ICarFunction`)**: kính, cửa sổ trời, sưởi/thông gió ghế, đèn. Hằng số trong `car/Coolray2025Functions.kt`,
   truy cập bằng reflection qua `car/EcarxFunctionAccess.kt`. Trước mỗi lệnh gọi `isFunctionSupported`, sau lệnh đọc lại.

Capture thực tế ngày 11/11/2026 xác nhận điều hoà dùng **area 0x75 = 117**, nhiệt độ **area 0x31 = 49 / 0x44 = 68, 16–32 °C**,
quạt **1–7**; các mã kính `591405227`, đèn, ghế, cốp và REF-WIDGET **không có** trên đầu xe 2025 nên không dùng.

Lệnh tăng/giảm nhiệt độ không nêu số dùng bước 0,5 °C, theo xác nhận của chủ xe ngày 11/11/2026. Ví dụ 24 → 24,5 °C. Khi nói rõ số độ, trợ lý dùng đúng số đã chỉ định.

## Routing hiện tại (1.0.9)

Chi tiết 47 action tham khảo, nguồn bytecode, giá trị/zone và câu mẫu ở
[COOLRAY2025_LUNARIS_COMMAND_MATRIX.md](COOLRAY2025_LUNARIS_COMMAND_MATRIX.md).
Mọi route vẫn REFERENCE/UNVERIFIED về phần cứng; kiểm tra hỗ trợ và đọc lại ở runtime.

| Nhóm | Backend / mã | Zone / giá trị |
| --- | --- | --- |
| Nguồn/AUTO/A/C/lạnh nhanh | ICarFunction `0x10010100` / `0x10010200` / `0x10010300` / `0x10010400` | global0; 1/0 |
| Nhiệt độ | customize `0x10060100` | 1 lái / 4 phụ; 16..32 bước0,5 |
| Quạt | function `0x10020100` | đặt global0, đọc hàng ghế trước1 rồi fallback0; enum +1..+7 |
| Hướng gió | function `0x10070100` | đặt0, đọc1/0; enum +1..+7 |
| Lấy gió | function `0x10030100` | global0; +1 trong / +2 ngoài |
| Khử mờ kính trước / sấy sau | function `0x10040100` / `0x10040300` | global0; 1/0 |
| ECO điều hoà / quạt AUTO | `0x10080100` / `0x10020200` | global0; bool / +1 êm,+2 thường,+3 mạnh |
| Kính / mái / rèm | MCU `0x287000A2`, 8byte | index0..3 kính,4 mái,6 rèm;0..100,-1 giữ nguyên |
| Nghiêng mái | function `0x21030400` | global0;1/0; đọc MCU `0x287000A1` |
| Chế độ lái | function `0x22010100` | global0;+1 ECO,+2 Comfort,+3 Sport |
| DRL / StartStop / AutoHold / ESC Sport | `0x20040800` / `0x20020100` / `0x20060400` / `0x20020300` | global0;1/0; yêu cầu xe đỗ/P |
| Thông gió ghế | function `0x10050100` | 1/4;0 hoặc enum+1..+3; chỉ khi có trang bị |
| Đèn đọc sách/chiếu gần/định vị/sương mù | các IBcm function có sẵn trong Coolray2025Functions | giữ route hiện có |
| Sưởi ghế/vô lăng/vòi phun/kính điện trở | không route | loại theo yêu cầu chủ xe |

Đọc tốc độ, số, mức xăng/dung tích, ODO và nhiệt độ ngoài trời từ VHAL có cấu hình.
Điện áp ắc quy đọc MCU `0x287000E4`; ODO fallback sensor `0x00100700`, quãng đường còn lại sensor
`0x00100800`, chế độ lái đọc ICarFunction. Xăng MCU `0x28700207` là phần trăm, không phải lít;
VHAL FUEL_LEVEL có đơn vị ml. Không nói kính đóng hết nếu chưa đọc đủ bốn kính.
Các bảng dưới giữ lại catalog VHAL gốc và không đồng nghĩa mọi property được route để ghi.

## Toàn bộ thuộc tính VHAL chuẩn trên đầu xe

Thuộc tính riêng ECARX (`0x287xxxxx`, kiểu BYTES) không liệt kê ở đây; danh sách ghi được nằm ở phụ lục B của
[`COOLRAY2025_VOICE_SPEC.md`](COOLRAY2025_VOICE_SPEC.md).

| Tên | ID | Quyền | Area: min..max |
|---|---|---|---|
| `INFO_MAKE` | 286261505 (`0x11100101`) | đọc | 0 (global) |
| `FUEL_DOOR_OPEN` | 287310600 (`0x11200308`) | đọc | 0 (global) |
| `EV_CHARGE_PORT_OPEN` | 287310602 (`0x1120030a`) | đọc | 0 (global) |
| `EV_CHARGE_PORT_CONNECTED` | 287310603 (`0x1120030b`) | đọc | 0 (global) |
| `PARKING_BRAKE_ON` | 287310850 (`0x11200402`) | đọc | 0 (global) |
| `FUEL_LEVEL_LOW` | 287310853 (`0x11200405`) | đọc | 0 (global) |
| `NIGHT_MODE` | 287310855 (`0x11200407`) | đọc | 0 (global) |
| `ABS_ACTIVE` | 287310858 (`0x1120040a`) | đọc | 0 (global) |
| `TRACTION_CONTROL_ACTIVE` | 287310859 (`0x1120040b`) | đọc | 0 (global) |
| `ENGINE_OIL_LEVEL` | 289407747 (`0x11400303`) | đọc | 0 (global) |
| `GEAR_SELECTION` | 289408000 (`0x11400400`) | đọc | 0 (global) |
| `CURRENT_GEAR` | 289408001 (`0x11400401`) | đọc | 0 (global) |
| `IGNITION_STATE` | 289408009 (`0x11400409`) | đọc | 0 (global) |
| `HVAC_STEERING_WHEEL_HEAT` | 289408269 (`0x1140050d`) | đọc/ghi | 0: -2..2 |
| `HVAC_TEMPERATURE_DISPLAY_UNITS` | 289408270 (`0x1140050e`) | đọc/ghi | 0: 0..0 |
| `AP_POWER_BOOTUP_REASON` | 289409538 (`0x11400a02`) | đọc | 0 (global) |
| `DISPLAY_BRIGHTNESS` | 289409539 (`0x11400a03`) | đọc/ghi | 0: 0..100 |
| `INFO_FUEL_TYPE` | 289472773 (`0x11410105`) | đọc | 0 (global) |
| `INFO_EV_CONNECTOR_TYPE` | 289472775 (`0x11410107`) | đọc | 0 (global) |
| `AP_POWER_STATE_REQ` | 289475072 (`0x11410a00`) | đọc | 0 (global) |
| `AP_POWER_STATE_REPORT` | 289475073 (`0x11410a01`) | ghi | 0 (global) |
| `WHEEL_TICK` | 290521862 (`0x11510306`) | đọc | 0 (global) |
| `INFO_FUEL_CAPACITY` | 291504388 (`0x11600104`) | đọc | 0 (global) |
| `INFO_EV_BATTERY_CAPACITY` | 291504390 (`0x11600106`) | đọc | 0 (global) |
| `PERF_ODOMETER` | 291504644 (`0x11600204`) | đọc | 0 (global) |
| `PERF_VEHICLE_SPEED` | 291504647 (`0x11600207`) | đọc | 0 (global) |
| `ENGINE_OIL_TEMP` | 291504900 (`0x11600304`) | đọc | 0 (global) |
| `ENGINE_RPM` | 291504901 (`0x11600305`) | đọc | 0 (global) |
| `FUEL_LEVEL` | 291504903 (`0x11600307`) | đọc | 0 (global) |
| `EV_BATTERY_LEVEL` | 291504905 (`0x11600309`) | đọc | 0 (global) |
| `EV_BATTERY_INSTANTANEOUS_CHARGE_RATE` | 291504908 (`0x1160030c`) | đọc | 0 (global) |
| `ENV_OUTSIDE_TEMPERATURE` | 291505923 (`0x11600703`) | đọc | 0 (global) |
| `VEHICLE_MAP_SERVICE` | 299895808 (`0x11e00c00`) | đọc/ghi | 0 (global) |
| `OBD2_LIVE_FRAME` | 299896064 (`0x11e00d00`) | đọc | 0 (global) |
| `OBD2_FREEZE_FRAME` | 299896065 (`0x11e00d01`) | đọc | 0 (global) |
| `OBD2_FREEZE_FRAME_INFO` | 299896066 (`0x11e00d02`) | đọc | 0 (global) |
| `OBD2_FREEZE_FRAME_CLEAR` | 299896067 (`0x11e00d03`) | ghi | 0 (global) |
| `HVAC_DEFROSTER` | 320865540 (`0x13200504`) | đọc/ghi | 1: 0..0; 2: 0..0 |
| `HVAC_AC_ON` | 354419973 (`0x15200505`) | đọc/ghi | 117: 0..0 |
| `HVAC_MAX_AC_ON` | 354419974 (`0x15200506`) | đọc/ghi | 117: 0..0 |
| `HVAC_MAX_DEFROST_ON` | 354419975 (`0x15200507`) | đọc/ghi | 117: 0..0 |
| `HVAC_RECIRC_ON` | 354419976 (`0x15200508`) | đọc/ghi | 117: 0..0 |
| `HVAC_DUAL_ON` | 354419977 (`0x15200509`) | đọc/ghi | 117: 0..0 |
| `HVAC_AUTO_ON` | 354419978 (`0x1520050a`) | đọc/ghi | 117: 0..0 |
| `HVAC_POWER_ON` | 354419984 (`0x15200510`) | đọc/ghi | 117: 0..0 |
| `HVAC_AUTO_RECIRC_ON` | 354419986 (`0x15200512`) | đọc/ghi | 117: 0..0 |
| `HVAC_FAN_SPEED` | 356517120 (`0x15400500`) | đọc/ghi | 117: 1..7 |
| `HVAC_FAN_DIRECTION` | 356517121 (`0x15400501`) | đọc/ghi | 117: 0..0 |
| `HVAC_TEMPERATURE_SET` | 358614275 (`0x15600503`) | đọc/ghi | 49: 16.0..32.0; 68: 16.0..32.0 |
| `DOOR_LOCK` | 371198722 (`0x16200b02`) | đọc | 1: 0..0; 4: 0..0 |
