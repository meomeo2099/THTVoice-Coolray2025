# Coolray 2025 — dữ liệu tham chiếu nền tảng SX11 (21/10/2026)

Nguồn: phân tích một gói phần mềm cộng đồng cho cùng nền tảng đầu xe ECARX SX11 (Coolray / X50),
đối chiếu với dump `car_service` của chính đầu xe `SX11-LA` / `IHU730P`. Chỉ ghi lại **sự kiện kỹ thuật**
(mã thuộc tính, mã phím, định dạng dữ liệu); không chép mã nguồn. Trạng thái: **REFERENCE** — chưa ghi
+ đọc lại bằng THT Voice trên xe.

## Nút vô lăng
- Phím vô lăng đi: VHAL → CarService `InputHalService.dispatchKeyEvent(listener, action, code, display)` → dịch vụ phím ECARX.
- Mã phím (vendor) trên đầu SX11: **298 = voice**, 299 = call, 286/287 = âm lượng +/−, 327 = nguồn.
- THT Voice xin chặn 298 (cùng 231, 200231) qua AdaptAPI `Input.requestKeysInterception` — không cần root.
  (Gói tham chiếu dùng hook Xposed trong CarService, cần root; THT Voice không làm vậy.)

## Kính / cửa sổ trời / rèm — đường MCU
| Việc | Cách |
|---|---|
| Ra lệnh | `CarImpl.sendBytesToMcuByPropID(INFO_ID_WINDOW_GROUP = 0x287000A2, byte[8])` |
| Định dạng | index 0 FL, 1 FR, 2 RL, 3 RR, 4 kính trời, 6 rèm che nắng; giá trị 0..100 % mở; `-1` = giữ nguyên |
| Đọc vị trí | `CarImpl.getIntByPropID(INFO_ID_WINDOW_FL/FR/RL/RR 0x2870009D..A0, SUNROOF 0x2870009B, ROLLSHADE 0x2870009C, BCM_FUNC_WINDOW_POS)` |
| Hé kính trời | AdaptAPI `BCM_FUNC_SUNROOF_TILT` (0 đóng, 1 hé) |

`CarImpl` là đối tượng `com.ecarx.xui.adaptapi.car.Car.create(context)` trả về trên xe; THT Voice gọi các hàm này bằng reflection
(`car/EcarxFunctionAccess.kt`). AdaptAPI `BCM_FUNC_WINDOW_POS` vẫn là đường dự phòng cho kính cửa và kính trời.

## Điều hoà (khớp cách THT Voice đang làm)
- Nhiệt độ: `setCustomizeFunctionValue(HVAC_FUNC_TEMP 268828928, zone 1, °C)`, bước 0,5.
- Quạt: `setFunctionValue(HVAC_FUNC_FAN_SPEED, FAN_SPEED_LEVEL_n)`; công tắc (POWER/AC/AUTO/AC_MAX): 1/0;
  lấy gió `HVAC_FUNC_CIRCULATION` = INNER/OUTSIDE.
- Thông gió ghế: `HVAC_FUNC_SEAT_VENTILATION`, zone 1/4, `SEAT_VENTILATION_LEVEL_n`.
- Sưởi vô lăng `HVAC_FUNC_STEERING_WHEEL_HEAT` (bật = LOW), sưởi kính chắn gió `HVAC_FUNC_FRONT_WINDSHIELD_HEATING`,
  sưởi vòi phun `HVAC_FUNC_FRONT_NOZZLE_HEATING`, sấy kính trước/sau `HVAC_FUNC_DEFROST_FRONT/REAR`.
- Khác: đèn chạy ban ngày `SETTING_FUNC_LAMP_DAYTIME_RUNNING_LIGHT`, Auto Hold, Start/Stop, ESC Sport (IVehicle).

## Cảm biến / trạng thái
| Dữ liệu | Nguồn | Giải mã |
|---|---|---|
| Điện áp ắc quy 12 V | `INFO_ID_VPOWERINFO_BATTERY_VOL` 0x287000E4 | bytes `[vôn, phần mười]` |
| Xăng còn (lít) | `INFO_ID_REMAINING_OIL` 0x28700207 | 1 byte |
| Tốc độ / số / ODO / % xăng / khoá điện | AdaptAPI `ISensor` 1048832 / 2097664 / 1050368 / 1050624 / 2097408 | |
| Phím CarPlay | `INFO_ID_CARPLAY_KEY_ID/VALUE` | chỉ khi CarPlay đang kết nối |

## Không dùng
Gói tham chiếu cần root + firmware tuỳ biến (Magisk, LSPosed, thay `NSInputService`/`NSSystemUIplugin`). THT Voice
chạy trên firmware gốc, ký bằng khoá platform của đầu xe, không cần root.
