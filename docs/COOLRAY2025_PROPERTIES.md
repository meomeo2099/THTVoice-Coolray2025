# Coolray 2025 — bảng thuộc tính xe (SX11-LA / IHU730P)

Nguồn duy nhất: dữ liệu lấy trực tiếp từ đầu xe Coolray 2025 của chủ repo
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

1. **VHAL chuẩn (`CarPropertyManager`)**: điều hoà, sưởi vô lăng, đọc trạng thái. Hằng số trong `car/CoolrayProperties.kt`.
2. **ECARX AdaptAPI 1.1.18.9 (`ICarFunction`)**: kính, cửa sổ trời, sưởi/thông gió ghế, đèn. Hằng số trong `car/Coolray2025Functions.kt`,
   truy cập bằng reflection qua `car/EcarxFunctionAccess.kt`. Trước mỗi lệnh gọi `isFunctionSupported`, sau lệnh đọc lại.

Khác bản Coolray cũ (IHU624G/IHU689G): điều hoà dùng **area 75** (không phải 5/117), nhiệt độ **area 31/44, 16–32 °C**,
quạt **1–7**; các mã kính `591405227`, đèn, ghế, cốp và REF-WIDGET **không có** trên đầu xe 2025 nên không dùng.

## Đã bật cho giọng nói

| Nhóm | Đường | Mã | Zone / area | Giá trị |
|---|---|---|---|---|
| Nguồn điều hoà | VHAL | 354419984 `HVAC_POWER_ON` | 75 | bool |
| A/C | VHAL | 354419973 `HVAC_AC_ON` | 75 | bool |
| Lạnh nhanh | VHAL | 354419974 `HVAC_MAX_AC_ON` | 75 | bool |
| Lấy gió trong/ngoài | VHAL | 354419976 `HVAC_RECIRC_ON` | 75 | bool, true = gió trong |
| Nhiệt độ | VHAL | 358614275 `HVAC_TEMPERATURE_SET` | 31 (lái), 44 (phụ) | float 16..32, bước 0,5 |
| Quạt | VHAL | 356517120 `HVAC_FAN_SPEED` | 75 | 1..7; "mạnh nhất" = 7; "tắt quạt" = tắt nguồn điều hoà |
| Hướng gió | VHAL | 356517121 `HVAC_FAN_DIRECTION` | 75 | bitmask 1 mặt / 2 chân / 4 kính |
| Sấy kính trước / sau | VHAL | 320865540 `HVAC_DEFROSTER` | 1 / 2 | bool |
| Sưởi vô lăng | VHAL | 289408269 `HVAC_STEERING_WHEEL_HEAT` | 0 | -2..2; bật = 2, tắt = 0 |
| Kính cửa | AdaptAPI | `BCM_FUNC_WINDOW_POS` 553845504 (customize %) | 16 / 32 / 256 / 512 | 0..100; dự phòng `BCM_FUNC_WINDOW` ON/OFF; dừng = `+1` |
| Cửa sổ trời (kính) | AdaptAPI | như trên | 4 (`WINDOW_ROOF_TOP`) | 0..100 |
| Rèm che nắng | — | không có actuator rõ zone | — | từ chối, không ghi |
| Sưởi ghế | AdaptAPI | `HVAC_FUNC_SEAT_HEATING` 268763648 | 1 lái / 4 phụ | 0 hoặc `ID + 1..3` |
| Thông gió ghế | AdaptAPI | `HVAC_FUNC_SEAT_VENTILATION` 268763392 | 1 / 4 | 0 hoặc `ID + 1..3` |
| Đèn trong xe | AdaptAPI | `BCM_FUNC_LIGHT_READING_LIGHT` 553980672 | ALL | ON/OFF |
| Đèn chiếu gần / định vị | AdaptAPI | `BCM_FUNC_LIGHT_DIPPED_BEAM` 553976064 / `BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS` 553977856 | ALL | ON/OFF |
| Đèn sương mù | AdaptAPI | `BCM_FUNC_LIGHT_FRONT_FOG_LAMPS` 553976832 | ALL | ON/OFF |

Đọc trạng thái: tốc độ (`PERF_VEHICLE_SPEED`), số (`CURRENT_GEAR`), nhiệt độ/quạt điều hoà, mức xăng
(`FUEL_LEVEL` ÷ `INFO_FUEL_CAPACITY`, kèm `FUEL_LEVEL_LOW`), ODO (`PERF_ODOMETER`), nhiệt độ ngoài trời
(`ENV_OUTSIDE_TEMPERATURE`), "tình trạng xe" (xăng + kính qua AdaptAPI). Quãng đường còn lại, tiêu hao, nước làm mát,
ắc quy, áp suất lốp, bảo dưỡng: đầu xe không có thuộc tính chuẩn → trả lời "chưa hỗ trợ".

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
| `HVAC_AC_ON` | 354419973 (`0x15200505`) | đọc/ghi | 75: 0..0 |
| `HVAC_MAX_AC_ON` | 354419974 (`0x15200506`) | đọc/ghi | 75: 0..0 |
| `HVAC_MAX_DEFROST_ON` | 354419975 (`0x15200507`) | đọc/ghi | 75: 0..0 |
| `HVAC_RECIRC_ON` | 354419976 (`0x15200508`) | đọc/ghi | 75: 0..0 |
| `HVAC_DUAL_ON` | 354419977 (`0x15200509`) | đọc/ghi | 75: 0..0 |
| `HVAC_AUTO_ON` | 354419978 (`0x1520050a`) | đọc/ghi | 75: 0..0 |
| `HVAC_POWER_ON` | 354419984 (`0x15200510`) | đọc/ghi | 75: 0..0 |
| `HVAC_AUTO_RECIRC_ON` | 354419986 (`0x15200512`) | đọc/ghi | 75: 0..0 |
| `HVAC_FAN_SPEED` | 356517120 (`0x15400500`) | đọc/ghi | 75: 1..7 |
| `HVAC_FAN_DIRECTION` | 356517121 (`0x15400501`) | đọc/ghi | 75: 0..0 |
| `HVAC_TEMPERATURE_SET` | 358614275 (`0x15600503`) | đọc/ghi | 31: 16.0..32.0; 44: 16.0..32.0 |
| `DOOR_LOCK` | 371198722 (`0x16200b02`) | đọc | 1: 0..0; 4: 0..0 |
