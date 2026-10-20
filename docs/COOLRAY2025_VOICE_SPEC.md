# Coolray 2025 — Đặc tả điều khiển bằng giọng nói

Tài liệu cho THT Voice, rút ra từ dữ liệu đầu xe Coolray 2025: getprop, car_service, danh sách gói, framework ECARX.

---

## 1. Tóm tắt nhanh

| Mục | Giá trị |
|---|---|
| Đầu xe | ECARX `SX11-LA` / `IHU730P`, nền tảng `ecarxp`, chip MediaTek 8 nhân, RAM 3.8 GB |
| Android | **9 (API 28)**, build `user`, `ro.debuggable=1` |
| Màn hình | 1920×720, 160 dpi |
| Trợ lý giọng nói gốc | **Không có**: trên máy không có gói nhận dạng hay đọc giọng nói nào, nên THT Voice phải tự mang ASR và TTS offline |
| API điều khiển xe | **ECARX AdaptAPI `com.ecarx.xui.adaptapi` bản 1.1.18.9**, nằm sẵn trong boot classpath (`AdapterAPI.jar` + `AdapterAPIImpl`) |
| API phụ | Android Automotive `android.car` / `CarPropertyManager`: 1.216 dòng thuộc tính (≈700 thuộc tính khác nhau), 410 thuộc tính ghi được |
| Định danh profile | `Build.MODEL == "SX11-LA"` hoặc `Build.PRODUCT == "IHU730P"` |

**Hai đường điều khiển:**
1. **ECARX AdaptAPI (`ICarFunction`)**: nên dùng làm đường chính. API tầng cao theo *function ID*, tự kiểm tra xe có hỗ trợ hay không.
2. **`CarPropertyManager`**: dùng để *đọc* trạng thái chuẩn (tốc độ, số, xăng…). Thuộc tính riêng của ECARX (`0x287xxxxx`) có kiểu `BYTES`, khó dùng nên chỉ để dự phòng.

---

## 2. Cách gọi ECARX AdaptAPI

```kotlin
// Không cần đóng gói jar: lớp đã có sẵn trên đầu xe. Có thể dùng reflection,
// hoặc thêm AdapterAPI.jar dạng compileOnly(files("libs/AdapterAPI.jar")).
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.base.ICarFunction
import com.ecarx.xui.adaptapi.FunctionStatus

val car = Car.create(context)                 // ICar
val fn: ICarFunction = car.iCarFunction       // getICarFunction()

// Luôn kiểm tra hỗ trợ trước khi thực thi lệnh
if (fn.isFunctionSupported(FUNC, ZONE) == FunctionStatus.active) {
    fn.setFunctionValue(FUNC, ZONE, VALUE)            // giá trị nguyên/enum
    fn.setCustomizeFunctionValue(FUNC, ZONE, 24.5f)   // giá trị số thực (nhiệt độ, %)
}
val cur = fn.getFunctionValue(FUNC, ZONE)
val supported: IntArray = fn.getSupportedFunctionValue(FUNC, ZONE)
fn.registerFunctionValueWatcher(FUNC, watcher)        // nghe thay đổi để phản hồi TTS
```

### Quy ước giá trị
- **Bật / tắt**: `COMMON_VALUE_ON = 1`, `COMMON_VALUE_OFF = 0`.
- **Giá trị enum** = `FUNC_ID + n`. Ví dụ `HVAC_FUNC_FAN_SPEED = 268566784`, nên mức gió 3 là `268566787`. Các bảng bên dưới ghi `+n`, khi gọi thì truyền `FUNC_ID + n`.
- Mục ghi `0` (ví dụ `FAN_SPEED_OFF`) là giá trị **0 tuyệt đối**, không cộng thêm FUNC_ID.
- Riêng `COMMON_VALUE_*` cũng là giá trị tuyệt đối (0, 1, 2…).

### Vùng (zone): `com.ecarx.xui.adaptapi.vehicle`
| Tên | Giá trị | Dùng cho |
|---|---|---|
| `ZONE_ALL` | `Integer.MIN_VALUE` | toàn xe |
| `ZONE_ROW_1_LEFT` | 1 | ghế/điều hòa phía tài xế |
| `ZONE_ROW_1_RIGHT` | 4 | ghế/điều hòa phía phụ |
| `ZONE_ROW_1_ALL` | 8 | hàng 1 |
| `ZONE_ROW_2_ALL` | 128 | hàng 2 |
| `WINDOW_ROW_1_LEFT` | 16 | kính lái |
| `WINDOW_ROW_1_RIGHT` | 32 | kính phụ |
| `WINDOW_ROW_2_LEFT` | 256 | kính sau trái |
| `WINDOW_ROW_2_RIGHT` | 512 | kính sau phải |
| `WINDOW_ROOF_TOP` | 4 | cửa sổ trời |
| `WINDOW_FRONT_WINDSHIELD` | 1 | kính trước |
| `WINDOW_REAR_WINDSHIELD` | 2 | kính sau |

### Phân quyền
- Chưa xác định được AdaptAPI có đòi quyền hệ thống hay không. **Lần đầu cài lên xe cần thử ngay**: gọi `isFunctionSupported` / `setFunctionValue` cho một lệnh an toàn, ví dụ bật tắt ion, rồi xem logcat.
- Nếu bị `SecurityException`, chuyển sang phương án gửi broadcast tới `ecarx.hvac.app` / `ecarx.settings`, hoặc chỉ hỗ trợ nhóm lệnh ở mục 6 (không cần quyền đặc biệt).
- Gói `com.ecarx.apkauth.provider` có thể kiểm soát APK ngoài. Nếu app bị chặn mở thì kiểm tra gói này trước.

---

## 3. Phần cứng xác nhận có trên xe (từ VHAL)

Danh sách này lấy từ thuộc tính **ghi được** trong car_service. Đây là những tính năng nên ưu tiên làm lệnh giọng nói:

| Nhóm | Thuộc tính VHAL tương ứng (bằng chứng) |
|---|---|
| Điều hòa | `HVAC_POWER_ON`, `HVAC_AC_ON`, `HVAC_AUTO_ON`, `HVAC_MAX_AC_ON`, `HVAC_DUAL_ON`, `HVAC_RECIRC_ON`, `HVAC_AUTO_RECIRC_ON` |
| Nhiệt độ | `HVAC_TEMPERATURE_SET` vùng 31 (trái) & 44 (phải), **16.0–32.0 °C**; `INFO_ID_L_TEMPRETURE`, `INFO_ID_R_TEMPRETURE`, `INFO_ID_DOUBLE_TEMPRETURE` |
| Quạt | `HVAC_FAN_SPEED` **1–7**; `HVAC_FAN_DIRECTION` |
| Sấy kính | `HVAC_DEFROSTER`, `HVAC_MAX_DEFROST_ON`, `INFO_ID_F_DEFROST`, `INFO_ID_R_DEFROST` |
| Sưởi vô lăng | `HVAC_STEERING_WHEEL_HEAT`, `INFO_ID_AC_SWHEAT` |
| Ghế | `INFO_ID_DRV_HEAT`, `INFO_ID_PAS_HEAT` (sưởi); `INFO_ID_AC_DRVVENT`, `INFO_ID_AC_PASVENT` (thông gió) |
| Lọc không khí | `INFO_ID_IONS`, `INFO_ID_AC_PM25_SWITCH`, `INFO_ID_AC_AUTODRYING`, `INFO_ID_AC_GCLEAN`, `INFO_ID_AC_CABINCLEAN`, `INFO_ID_AC_AUTO_AIR_PURIFICATION_SWITCH` |
| Nước hoa | `INFO_ID_AC_FRAGERANCE_SWICTH`, `_LEVEL`, `_TASTE` |
| Cửa kính | `INFO_ID_WINDOW_FL/FR/RL/RR`, `INFO_ID_WINDOW_GROUP`, `INFO_ID_VEHIC_WINDOW_VENTILATE`, `INFO_ID_VEHIC_AUTO_CLOSE_WINDOW` |
| Cửa sổ trời | `INFO_ID_WINDOW_SUNROOF`, `INFO_ID_WINDOW_SUNROOFTILE`, `INFO_ID_WINDOW_ROLLSHADE`, `INFO_ID_SKYLIGHT_SUNSHADE_SWITCH` |
| Cốp | `INFO_ID_VEHIC_TRUNKACTIONREQ` |
| Gương | `INFO_ID_VEHIC_MIRROR_AUTO_FOLDING`, `INFO_ID_VEHIC_REARMIRROR_TILT` |
| Đèn viền | `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_SWITCH/COLOR/BRIGHTNESS/MODE` (+ bản `_3B`), `_MUSIC_SWITCH_3B`, `_VOICE_MODE` |
| Đèn ngoài | `INFO_ID_VEHIC_DAY_RUNNING_LAMP`, `INFO_ID_VEHIC_WELCOME_LIGHT`, `INFO_ID_VEHIC_FOLLOWME_HOME_*`, `INFO_ID_VEHIC_INTELLIGENT_HIGH_BEAM_CONTROL` |
| Chế độ lái | `INFO_ID_VDRIVEINFO_DRIVER_MODE`, `INFO_ID_VEHICLE_MODE_REQ` |
| Camera hành trình | `INFO_ID_DVR_RECORDING`, `INFO_ID_DVR_*` |
| Camera 360 | `INFO_ID_AVM_FRONT/LEFT/REAR/RIGHT` |
| Màn hình | `INFO_ID_DMM_BackLightBright`, `INFO_ID_SYNC_DAY_NIGHT_MODE_TO_IPK` |

**Không thấy trên xe** (không làm lệnh): massage ghế, chỉnh ghế điện bằng giọng nói, sưởi hàng 2, điều hòa hàng 2 riêng, cửa điện, rèm sau.

---

## 4. Bộ lệnh giọng nói đề xuất

Cột *Hàm* là hằng số ECARX AdaptAPI. *Giá trị* `+n` = FUNC_ID + n. Mẫu câu viết không dấu cũng nên khớp.

### 4.1 Điều hòa (`car.hvac.IHvac`)
| Intent | Mẫu câu | Hàm (ID) | Zone | Giá trị |
|---|---|---|---|---|
| `ac.power_on` / `off` | "bật/tắt điều hòa" | `HVAC_FUNC_POWER` (268501248) | ALL | ON / OFF |
| `ac.cool_on` / `off` | "bật/tắt lạnh", "bật/tắt AC" | `HVAC_FUNC_AC` (268501760) | ALL | ON / OFF |
| `ac.auto` | "điều hòa tự động" | `HVAC_FUNC_AUTO` (268501504) | ALL | ON |
| `ac.max_cool` | "lạnh tối đa", "làm mát nhanh" | `HVAC_FUNC_AC_MAX` (268502016) | ALL | ON |
| `ac.temp_set` | "để 24 độ", "nhiệt độ 22" | `HVAC_FUNC_TEMP` (268828928), **setCustomizeFunctionValue** | ROW_1_LEFT / RIGHT / ALL | float 16–32 |
| `ac.temp_up` / `down` | "tăng/giảm nhiệt độ", "nóng quá", "lạnh quá" | `HVAC_FUNC_TEMP` | như trên | hiện tại ± 1 (đọc `HVAC_FUNC_TEMP_STEP`) |
| `ac.temp_driver` / `passenger` | "bên lái 22 độ", "bên phụ 25" | `HVAC_FUNC_TEMP` | ROW_1_LEFT / ROW_1_RIGHT | float |
| `ac.dual` | "đồng bộ nhiệt độ" / "tách vùng" | `HVAC_FUNC_TEMP_DUAL` (268829952) | ALL | OFF (đồng bộ) / ON |
| `ac.fan_set` | "gió số 3" | `HVAC_FUNC_FAN_SPEED` (268566784) | ALL | +1…+7 |
| `ac.fan_up` / `down` | "gió to/nhỏ lên" | `HVAC_FUNC_FAN_SPEED` | ALL | hiện tại ± 1, kẹp 1–7 |
| `ac.recirc_in` | "lấy gió trong", "đóng gió ngoài" | `HVAC_FUNC_CIRCULATION` (268632320) | ALL | +1 |
| `ac.recirc_out` | "lấy gió ngoài" | `HVAC_FUNC_CIRCULATION` | ALL | +2 |
| `ac.recirc_auto` | "tuần hoàn tự động" | `HVAC_FUNC_CIRCULATION` | ALL | +3 |
| `ac.blow_face` | "thổi mặt" | `HVAC_FUNC_BLOWING_MODE` (268894464) | ALL | +1 |
| `ac.blow_feet` | "thổi chân" | `HVAC_FUNC_BLOWING_MODE` | ALL | +2 |
| `ac.blow_face_feet` | "thổi mặt và chân" | `HVAC_FUNC_BLOWING_MODE` | ALL | +3 |
| `ac.blow_windshield` | "thổi kính" | `HVAC_FUNC_BLOWING_MODE` | ALL | +4 |
| `ac.defrost_front` | "sấy kính trước" | `HVAC_FUNC_DEFROST_FRONT` (268697856) | ALL | ON / OFF |
| `ac.defrost_front_max` | "sấy kính tối đa" | `HVAC_FUNC_DEFROST_FRONT_MAX` (268698112) | ALL | ON |
| `ac.defrost_rear` | "sấy kính sau" | `HVAC_FUNC_DEFROST_REAR` (268698368) | ALL | ON / OFF |
| `ac.wheel_heat` | "sưởi vô lăng" | `HVAC_FUNC_STEERING_WHEEL_HEAT` (269025536) | ALL | +1 thấp / +2 vừa / +3 cao / 0 tắt |
| `ac.seat_heat` | "sưởi ghế lái mức 2" | `HVAC_FUNC_SEAT_HEATING` (268763648) | ROW_1_LEFT / RIGHT | +1…+3 / 0 |
| `ac.seat_vent` | "làm mát ghế phụ" | `HVAC_FUNC_SEAT_VENTILATION` (268763392) | ROW_1_LEFT / RIGHT | +1…+3 / 0 |
| `ac.ion` | "bật/tắt ion" | `HVAC_FUNC_IONS_SWITCH` (268961024) | ALL | ON / OFF |
| `ac.pm25` | "lọc bụi mịn", "kiểm tra PM2.5" | `HVAC_FUNC_PM25_SWITCH` (269811968) | ALL | ON / OFF |
| `ac.auto_dry` | "tự sấy dàn lạnh" | `HVAC_FUNC_AUTOMATIC_VENTILATION_DRY` (269485312) | ALL | ON / OFF |
| `ac.g_clean` | "làm sạch không khí" | `HVAC_FUNC_G_CLEAN` (269485056) | ALL | ON / OFF |
| `ac.eco` | "điều hòa tiết kiệm" | `HVAC_FUNC_ECO_SWITCH` (268960000) | ALL | ON / OFF |
| `ac.fragrance` | "bật nước hoa" | `HVAC_FUNC_AIR_FRAGRANCE` (269156608) | ALL | ON / OFF |
| `ac.fragrance_level` | "nước hoa đậm/nhạt" | `HVAC_FUNC_AIR_FRAGRANCE_LEVEL` (269157120) | ALL | +1…+3 |

### 4.2 Cửa kính, cửa sổ trời, cốp, gương (`car.vehicle.IBcm`, `IVehicle`)
| Intent | Mẫu câu | Hàm (ID) | Zone | Giá trị |
|---|---|---|---|---|
| `window.open` / `close` | "mở/đóng kính lái", "mở hết kính" | `BCM_FUNC_WINDOW` (553844992) | WINDOW_ROW_x / tổ hợp OR | ON mở / OFF đóng |
| `window.half` | "mở kính một nửa" | `BCM_FUNC_WINDOW` | như trên | +2 (`WINDOW_HALF`) |
| `window.stop` | "dừng kính" | `BCM_FUNC_WINDOW` | như trên | +1 (`WINDOW_PAUSE`) |
| `window.percent` | "mở kính lái 30%" | `BCM_FUNC_WINDOW_POS` (553845504), customize | WINDOW_ROW_x | float 0–100 |
| `window.vent` | "hé kính thông gió" | `SETTING_FUNC_WINDOW_VENTILATE` (537396736) | ALL | ON |
| `sunroof.open` / `close` | "mở/đóng cửa sổ trời" | `BCM_FUNC_WINDOW` | WINDOW_ROOF_TOP (4) | ON / OFF |
| `sunroof.tilt` | "hé cửa sổ trời" | `BCM_FUNC_SUNROOF_TILT` (553845760) | WINDOW_ROOF_TOP | ON |
| `sunshade.open` / `close` | "mở/đóng rèm che nắng" | `SETTING_FUNC_WINDOW_CLOSE_SUNCURTAIN` (537395456) | ALL | OFF / ON |
| `trunk.open` | "mở cốp" | `BCM_FUNC_DOOR` (553779456) | `DOOR_REAR` (536870912) | ON. **Chỉ khi xe ở số P** |
| `mirror.fold` | "gập/mở gương" | `BCM_FUNC_FOLD_REAR_MIRROR` (554041600) | ALL | ON / OFF |
| `wiper.auto` | "gạt mưa tự động" | `BCM_FUNC_WIPER` (553713920) | ALL | +1 |

Khi nói "tất cả", truyền zone là phép OR các bit, ví dụ `16|32|256|512` cho 4 kính. Nếu `isFunctionSupported` báo không hỗ trợ zone gộp thì lặp lại cho từng kính.

### 4.3 Đèn (`car.vehicle.IAmbienceLight`, `ILamp`)
| Intent | Mẫu câu | Hàm (ID) | Giá trị |
|---|---|---|---|
| `ambient.on` / `off` | "bật/tắt đèn viền" | `BCM_FUNC_LIGHT_ATMOSPHERE_LAMPS` (553979904) | ON / OFF |
| `ambient.color` | "đèn viền màu xanh" | `SETTING_FUNC_AMBIENCE_LIGHT_THEME_COLOR` (704709120) | +1 đỏ, +2 cam, +3 vàng, +4 xanh lá, +5 chàm, +6 xanh dương, +7 tím |
| `ambient.brightness` | "đèn viền sáng 50%" | `SETTING_FUNC_AMBIENCE_LIGHT_INTENSITY_SET` (704708864), customize | float |
| `ambient.music` | "đèn nháy theo nhạc" | `SETTING_FUNC_AMBIENCE_LIGHT_MUSIC_SHOW_MODE` (704972800) | +1 sôi động, +2 thường, +3 nhẹ |
| `light.reading` | "bật đèn đọc sách" | `BCM_FUNC_LIGHT_READING_LIGHT` (553980672) | ON / OFF |
| `light.drl` | "bật đèn ban ngày" | `SETTING_FUNC_LAMP_DAYTIME_RUNNING_LIGHT` (537135104) | ON / OFF |
| `light.welcome` | "bật đèn chào mừng" | `SETTING_FUNC_WELCOME_LIGHT` (721617152) | ON / OFF |
| `light.auto_high_beam` | "bật pha tự động" | `SETTING_FUNC_LAMP_ACTIVE_HIGH_BEAM_CONTROL` (721486080) | ON / OFF |

Hiệu ứng đèn khi nói: `SETTING_FUNC_ALCM_VOICE_MODE` (705103104). Gửi +1 khi bắt đầu thu âm, +3 khi bắt đầu nhận dạng, +7 khi bắt đầu đọc TTS, +8 khi đọc xong. Đèn viền sẽ "thở" theo giọng nói. Hàm này có trên xe (`LAMP_VOICE_MODE`).

### 4.4 Chế độ lái, màn hình (`car.vehicle.IDriveMode`, `IDayMode`)
| Intent | Mẫu câu | Hàm (ID) | Giá trị |
|---|---|---|---|
| `drive.eco` | "chế độ tiết kiệm" | `DM_FUNC_DRIVE_MODE_SELECT` (570491136) | +1 |
| `drive.comfort` | "chế độ thường/thoải mái" | như trên | +2 |
| `drive.sport` | "chế độ thể thao" | như trên | +3 |
| `screen.brightness` | "màn hình sáng hơn/tối hơn" | `SETTING_FUNC_BRIGHTNESS_BACKLIGHT` (687997184), đọc `_MIN/_MAX/_STEP` | int |
| `screen.daynight` | "chế độ ban đêm/ngày/tự động" | `SETTING_FUNC_DAYMODE_SETTING` (538247424) | đọc `getSupportedFunctionValue` |

**Chặn khi xe đang chạy** (đọc tốc độ > 0): đổi chế độ lái, mở cốp, gập gương, thao tác camera 360.

---

## 5. Câu hỏi trạng thái (đọc, không ghi)

Đọc qua `CarPropertyManager`. ID là thuộc tính VHAL chuẩn, đã có trên xe:

| Intent | Mẫu câu | Thuộc tính | ID | Ghi chú |
|---|---|---|---|---|
| `info.speed` | "đang chạy bao nhiêu km/h" | `PERF_VEHICLE_SPEED` | `0x11600207` | m/s × 3.6 |
| `info.fuel` | "còn bao nhiêu xăng" | `FUEL_LEVEL` / `INFO_FUEL_CAPACITY` | `0x11600307` / `0x11600104` | ml → % |
| `info.fuel_low` | "có sắp hết xăng không" | `FUEL_LEVEL_LOW` | `0x11200405` | bool |
| `info.odo` | "xe đi được bao nhiêu km" | `PERF_ODOMETER` | `0x11600204` | km |
| `info.gear` | "đang ở số nào" | `GEAR_SELECTION` | `0x11400400` | P=4, R=2, N=1, D=8 |
| `info.parking_brake` | "phanh tay đã kéo chưa" | `PARKING_BRAKE_ON` | `0x11200402` | bool |
| `info.night` | — (tự đổi giọng/âm lượng) | `NIGHT_MODE` | `0x11200407` | bool |
| `info.ac_state` | "điều hòa đang mấy độ" | AdaptAPI `getCustomizeFunctionValue(HVAC_FUNC_TEMP, zone)` | — | |
| `info.tire` | "áp suất lốp thế nào" | AdaptAPI `car.sensor.ITireSensor` | — | kiểm tra có hỗ trợ |

---

## 6. Lệnh hệ thống & app (không cần quyền xe)

| Intent | Mẫu câu | Cách làm |
|---|---|---|
| `media.play/pause/next/prev` | "phát nhạc", "bài tiếp" | `MediaSessionManager` + `NotificationListenerService` (giống THT Launcher) |
| `media.volume_up/down/set` | "to lên", "âm lượng 10" | `AudioManager.adjustStreamVolume(STREAM_MUSIC, …)` |
| `media.mute` | "tắt tiếng" | `AudioManager.ADJUST_MUTE` |
| `media.source_usb` | "mở nhạc USB" | mở `com.ecarx.multimedia` / `ecarx.xsf.mediacenter` |
| `radio.open` / `radio.fm` | "mở radio", "FM 99.9" | `com.ecarx.radio.service` (FM/AM); `sw.com.dabdrmradio` (DAB) |
| `phone.call` | "gọi cho Minh" | `Intent.ACTION_CALL` → `com.ecarx.btphone`; danh bạ từ PBAP |
| `phone.answer/reject` | "nghe máy", "từ chối" | `TelecomManager.acceptRingingCall()` (cần `ANSWER_PHONE_CALLS`) |
| `nav.go` | "chỉ đường về nhà", "đi đến chợ Bến Thành" | `geo:0,0?q=…` hoặc `google.navigation:q=…` gửi tới app bản đồ đã cài |
| `app.open` | "mở YouTube", "mở CarPlay" | `packageManager.getLaunchIntentForPackage`; CarPlay = `com.neusoft.carplay` |
| `app.ac_screen` | "mở màn hình điều hòa" | `ecarx.hvac.app` |
| `app.settings` | "mở cài đặt" | `ecarx.settings` |
| `app.camera360` | "mở camera 360" | `INFO_ID_AVM_*` / `com.neusoft.nsapa` |
| `app.dvr` | "chụp ảnh camera hành trình" | `com.neusoft.dvr` |
| `sys.home` | "về màn hình chính" | `Intent.CATEGORY_HOME` |

---

## 7. Gói hệ thống liên quan trên xe

| Gói | Vai trò |
|---|---|
| `ecarx.launcher3` | launcher gốc |
| `ecarx.hvac.app` | màn hình điều hòa |
| `ecarx.settings` | cài đặt xe |
| `com.ecarx.multimedia`, `ecarx.xsf.mediacenter`, `ecarx.xsf.widget` | nhạc / media |
| `com.ecarx.radio.service`, `sw.com.dabdrmradio` | radio FM/AM/DAB |
| `com.ecarx.btphone` | điện thoại Bluetooth |
| `com.neusoft.carplay` | Apple CarPlay |
| `com.neusoft.dvr` | camera hành trình |
| `com.neusoft.nsapa`, `ecarx.pdc` | camera 360 / cảm biến đỗ |
| `ecarx.ihu.service`, `ecarx.os.ecarxoservice`, `ecarx.power.service` | dịch vụ nền đầu xe |
| `com.ecarx.apkauth.provider` | kiểm soát APK cài thêm |
| `com.iqqijni.car.keyboard` | bàn phím |

Dịch vụ binder: `car_service` (`android.car.ICar`), `ecarx_ihu_service`, `ecarx_power_manager`, `ecarx_tbox`, `ecarx_avm`, `ecarx_splitscreen`, `ecarxappservice`.

---

## 8. Ràng buộc kỹ thuật cho THT Voice

- **minSdk 28**, không dùng API mới hơn Android 9: không có `SpeechRecognizer` on-device, không có `AudioPlaybackCapture`.
- **RAM 3.8 GB, CPU ARM64 8 nhân (MediaTek)**: chạy được ASR offline cỡ nhỏ và vừa (Vosk/Sherpa-ONNX tiếng Việt khoảng 50–150 MB) và TTS offline (Piper/Sherpa VITS tiếng Việt).
- Dung lượng trống `/data` khoảng 40 GB, thoải mái chứa model.
- Không có trợ lý gốc, nên không phải tranh micro hay nút voice với app khác. Nút voice trên vô lăng cần bắt qua `KeyEvent` (dò `KEYCODE_VOICE_ASSIST` / `KEYCODE_SEARCH` trên xe) hoặc dùng `AccessibilityService`.
- **An toàn**: lệnh mở cốp, đổi chế độ lái, mở kính trên 50% khi tốc độ trên 80 km/h phải đọc tốc độ và số trước. Mọi lệnh ghi phải đọc lại giá trị bằng `getFunctionValue` rồi mới phản hồi TTS ("Đã bật điều hòa 24 độ").
- Mỗi intent trong bảng nên gọi `isFunctionSupported` một lần lúc khởi động và lưu cache. Intent nào không hỗ trợ thì tự ẩn, TTS trả lời "Xe không hỗ trợ tính năng này".

---

## 9. Việc cần kiểm tra trên xe (checklist lần cài đầu)

1. `Car.create(context)` chạy được, không ném `SecurityException`.
2. Ghi log toàn bộ `isFunctionSupported` cho các hàm ở mục 4, để chốt danh sách lệnh thật.
3. Thử `HVAC_FUNC_TEMP` với `ZONE_ROW_1_LEFT` và `ZONE_ALL`.
4. Thử `BCM_FUNC_WINDOW` với từng zone và zone gộp.
5. Tìm mã phím của nút voice trên vô lăng: `adb shell getevent -l` rồi bấm nút.
6. Kiểm tra micro: `AudioRecord` với `MIC` và `VOICE_RECOGNITION`, 16 kHz mono.

---

## Phụ lục A: Danh mục đầy đủ function ID của ECARX AdaptAPI 1.1.18.9

Tạo tự động từ `AdapterAPI.jar` trên xe. *Giá trị* ghi dạng `TÊN=+n` (truyền `ID + n`). Không phải hàm nào cũng có trên Coolray 2025, nên luôn kiểm tra bằng `isFunctionSupported`.

### `car.hvac.IHvac` (95 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `HVAC_FUNC_POWER` | 268501248 |  |
| `HVAC_FUNC_AUTO` | 268501504 |  |
| `HVAC_FUNC_AC` | 268501760 |  |
| `HVAC_FUNC_AC_MAX` | 268502016 |  |
| `HVAC_FUNC_CLIMATE_ZONE` | 268502272 | CLIMATE_ZONE_SINGLE=+1, CLIMATE_ZONE_DUAL=+2, CLIMATE_ZONE_TRIPLE=+3, CLIMATE_ZONE_FOUR=+4, CLIMATE_ZONE_ELECTRIC=+5 |
| `HVAC_FUNC_POWER_VR` | 268505344 |  |
| `HVAC_FUNC_FAN_SPEED` | 268566784 | FAN_SPEED_LEVEL_1=+1, FAN_SPEED_LEVEL_2=+2, FAN_SPEED_LEVEL_3=+3, FAN_SPEED_LEVEL_4=+4, FAN_SPEED_LEVEL_5=+5, FAN_SPEED_LEVEL_6=+6, FAN_SPEED_LEVEL_7=+7, FAN_SPEED_LEVEL_8=+8, FAN_SPEED_LEVEL_9=+9, FAN_SPEED_LEVEL_AUTO=+10 |
| `HVAC_FUNC_AUTO_FAN_SETTING` | 268567040 | AUTO_FAN_SETTING_SILENT=+1, AUTO_FAN_SETTING_NORMAL=+2, AUTO_FAN_SETTING_HIGH=+3, AUTO_FAN_SETTING_QUIETER=+4, AUTO_FAN_SETTING_HIGHER=+5 |
| `HVAC_FUNC_CIRCULATION` | 268632320 | CIRCULATION_INNER=+1, CIRCULATION_OUTSIDE=+2, CIRCULATION_AUTO=+3 |
| `HVAC_FUNC_CIRCULATION_TIMER` | 268632576 |  |
| `HVAC_FUNC_CIRCULATION_LONG_TOUCH` | 268632832 |  |
| `HVAC_FUNC_DEFROST_FRONT` | 268697856 |  |
| `HVAC_FUNC_DEFROST_FRONT_MAX` | 268698112 |  |
| `HVAC_FUNC_DEFROST_REAR` | 268698368 |  |
| `HVAC_FUNC_AUTO_DEFROST_REAR` | 268698624 |  |
| `HVAC_FUNC_AUTO_DEFROST_FRONT` | 268698880 |  |
| `HVAC_FUNC_AUTO_DEFROST_REQUEST` | 268699136 |  |
| `HVAC_FUNC_AUTO_DEFROST_CONFIRM` | 268699392 |  |
| `HVAC_FUNC_SEAT_VENTILATION` | 268763392 | SEAT_VENTILATION_LEVEL_1=+1, SEAT_VENTILATION_LEVEL_2=+2, SEAT_VENTILATION_LEVEL_3=+3, SEAT_VENTILATION_LEVEL_AUTO=+15 |
| `HVAC_FUNC_SEAT_HEATING` | 268763648 | SEAT_HEATING_LEVEL_1=+1, SEAT_HEATING_LEVEL_2=+2, SEAT_HEATING_LEVEL_3=+3, SEAT_HEATING_TIMER_OUT_OFF=+4, SEAT_HEATING_LEVEL_AUTO=+15 |
| `HVAC_FUNC_AUTO_SEAT_VENTILATION` | 268763904 | AUTO_SEAT_VENTILATION_LEVEL_1=+1, AUTO_SEAT_VENTILATION_LEVEL_2=+2, AUTO_SEAT_VENTILATION_LEVEL_3=+3 |
| `HVAC_FUNC_AUTO_SEAT_VENTILATION_TIME` | 268764160 | AUTO_SEAT_VENTILATION_TIME_1=+1, AUTO_SEAT_VENTILATION_TIME_2=+2, AUTO_SEAT_VENTILATION_TIME_3=+3 |
| `HVAC_FUNC_AUTO_SEAT_HEATING` | 268764416 | AUTO_SEAT_HEATING_LEVEL_1=+1, AUTO_SEAT_HEATING_LEVEL_2=+2, AUTO_SEAT_HEATING_LEVEL_3=+3 |
| `HVAC_FUNC_AUTO_SEAT_HEATING_TIME` | 268764672 | AUTO_SEAT_HEATING_TIME_1=+1, AUTO_SEAT_HEATING_TIME_2=+2, AUTO_SEAT_HEATING_TIME_3=+3 |
| `HVAC_FUNC_SEAT_MASSAGE` | 268764928 | SEAT_MASSAGE_LEVEL_1=+1, SEAT_MASSAGE_LEVEL_2=+2, SEAT_MASSAGE_LEVEL_3=+3, SEAT_MASSAGE_LEVEL_AUTO=+15 |
| `HVAC_FUNC_AUTO_SEAT_MASSAGE` | 268765184 | AUTO_SEAT_MASSAGE_LEVEL_1=+1, AUTO_SEAT_MASSAGE_LEVEL_2=+2, AUTO_SEAT_MASSAGE_LEVEL_3=+3 |
| `HVAC_FUNC_AUTO_SEAT_MASSAGE_TIME` | 268765440 | AUTO_SEAT_MASSAGE_TIME_1=+1, AUTO_SEAT_MASSAGE_TIME_2=+2, AUTO_SEAT_MASSAGE_TIME_3=+3 |
| `HVAC_FUNC_SEAT_MASSAGE_SWITCH` | 268765696 |  |
| `HVAC_FUNC_SEAT_MASSAGE_PROGRAM` | 268765952 | SEAT_MASSAGE_PROGRAM_1=+1, SEAT_MASSAGE_PROGRAM_2=+2, SEAT_MASSAGE_PROGRAM_3=+3, SEAT_MASSAGE_PROGRAM_4=+4, SEAT_MASSAGE_PROGRAM_5=+5, SEAT_MASSAGE_PROGRAM_6=+6, SEAT_MASSAGE_PROGRAM_7=+7, SEAT_MASSAGE_PROGRAM_8=+8, SEAT_MASSAGE_PROGRAM_9=+9, SEAT_MASSAGE_PROGRAM_A=+10 |
| `HVAC_FUNC_SECOND_ROW_SEAT_HEATING` | 268766208 |  |
| `HVAC_FUNC_TEMP` | 268828928 |  |
| `HVAC_FUNC_TEMP_MAX` | 268829184 |  |
| `HVAC_FUNC_TEMP_MIN` | 268829440 |  |
| `HVAC_FUNC_TEMP_STEP` | 268829696 |  |
| `HVAC_FUNC_TEMP_DUAL` | 268829952 |  |
| `HVAC_FUNC_TEMP_UNIT` | 268830208 | TEMPERATURE_UNIT_C=+1, TEMPERATURE_UNIT_F=+2 |
| `HVAC_FUNC_BLOWING_MODE` | 268894464 | BLOWING_MODE_FACE=+1, BLOWING_MODE_LEG=+2, BLOWING_MODE_FACE_AND_LEG=+3, BLOWING_MODE_FRONT_WINDOW=+4, BLOWING_MODE_FACE_AND_FRONT_WINDOW=+5, BLOWING_MODE_LEG_AND_FRONT_WINDOW=+6, BLOWING_ALL=+7, BLOWING_MODE_AUTO_SWITCH=+8 |
| `HVAC_FUNC_SWEEPING_MODE` | 268894720 | SWEEPING_MODE_LEFT_RIGHT=+1, SWEEPING_MODE_UP_DOWN=+2, DIRECTION_MODE_CUSTOM=+3, SWEEPING_MODE_LR_AND_UD=+3 |
| `HVAC_FUNC_DIRECTION_MODE` | 268894976 | DIRECTION_MODE_FOCUS=+1, DIRECTION_MODE_AVOID=+2 |
| `HVAC_FUNC_SWEEPING_HORIZONTAL_POS` | 268895232 |  |
| `HVAC_FUNC_SWEEPING_VERTICAL_POS` | 268895488 |  |
| `HVAC_FUNC_BLOWING_TEMP_COLOR` | 268895744 |  |
| `HVAC_FUNC_AUTO_BLOWING_MODE` | 268896000 |  |
| `HVAC_FUNC_ECO_SWITCH` | 268960000 |  |
| `HVAC_FUNC_AQS_SWITCH` | 268960256 |  |
| `HVAC_FUNC_AUTO_DEHUMIDIFICATION` | 268960512 |  |
| `HVAC_FUNC_OVERHEAT_PROTECTION` | 268960768 |  |
| `HVAC_FUNC_IONS_SWITCH` | 268961024 |  |
| `HVAC_FUNC_STEERING_WHEEL_HEAT` | 269025536 | STEERING_WHEEL_HEAT_LOW=+1, STEERING_WHEEL_HEAT_MID=+2, STEERING_WHEEL_HEAT_HIGH=+3, STEERING_WHEEL_HEAT_AUTO=+15 |
| `HVAC_FUNC_AUTO_STEERING_WHEEL_HEAT` | 269025792 | AUTO_STEERING_WHEEL_HEAT_LOW=+1, AUTO_STEERING_WHEEL_HEAT_MID=+2, AUTO_STEERING_WHEEL_HEAT_HIGH=+3 |
| `HVAC_FUNC_AUTO_STEERING_WHEEL_HEAT_TIME` | 269026048 | AUTO_STEERING_WHEEL_HEAT_TIME_1=+1, AUTO_STEERING_WHEEL_HEAT_TIME_2=+2, AUTO_STEERING_WHEEL_HEAT_TIME_3=+3 |
| `HVAC_FUNC_AUTO_STEERING_WHEEL_HEAT_SWITCH` | 269026304 |  |
| `HVAC_FUNC_PRE_CLIMATISATION` | 269091072 |  |
| `HVAC_FUNC_POST_CLIMATISATION` | 269091328 |  |
| `HVAC_FUNC_CLIMATISATION_ERROR_CONDITIONS` | 269091584 |  |
| `HVAC_FUNC_AIR_FRAGRANCE` | 269156608 | AFUSWITCHRESP_SIGNAL_LOST=+1, AFUSWITCHRESP_SIGNAL_LOST_TYPE=+1, AFUSWITCHRESP_SIGNAL_UNLOST=+3 |
| `HVAC_FUNC_AIR_FRAGRANCE_TYPE` | 269156864 | AIR_FRAGRANCE_TASTE1=+1, AIR_FRAGRANCE_TASTE2=+2, AIR_FRAGRANCE_TASTE3=+3, AIR_FRAGRANCE_TASTE4=+4, AIR_FRAGRANCE_TASTE5=+5, AIR_FRAGRANCE_TASTE6=+6, AIR_FRAGRANCE_TASTE7=+7, AIR_FRAGRANCE_TASTE8=+8, AIR_FRAGRANCE_TASTE9=+9, AIR_FRAGRANCE_TASTE10=+10, AIR_FRAGRANCE_TASTE11=+11, AIR_FRAGRANCE_TASTE12=+12, AIR_FRAGRANCE_TASTE13=+13, AIR_FRAGRANCE_TASTE14=+14, AIR_FRAGRANCE_TASTE15=+15, AIR_FRAGRANCE_TASTE16=+16, AIR_FRAGRANCE_TASTE17=+17, AIR_FRAGRANCE_TASTE18=+18, AIR_FRAGRANCE_TASTE19=+19 |
| `HVAC_FUNC_AIR_FRAGRANCE_LEVEL` | 269157120 | AIR_FRAGRANCE_LEVEL_1=+1, AIR_FRAGRANCE_LEVEL_2=+2, AIR_FRAGRANCE_LEVEL_3=+3 |
| `HVAC_FUNC_AIR_FRAGRANCE_RATIO` | 269158144 |  |
| `HVAC_FUNC_AUTO_ION` | 269222144 |  |
| `HVAC_FUNC_AUTO_ION_REQUEST` | 269222400 |  |
| `HVAC_FUNC_AUTO_ION_CONFIRM` | 269222656 |  |
| `HVAC_FUNC_AUTO_DEHUMIDIFICATION_REQUEST` | 269287680 |  |
| `HVAC_FUNC_AUTO_DEHUMIDIFICATION_CONFIRM` | 269287936 |  |
| `HVAC_FUNC_CO2_SWITCH` | 269353216 |  |
| `HVAC_FUNC_CO2_HIGHER_REQUEST` | 269353472 |  |
| `HVAC_FUNC_CO2_HIGHER_CONFIRM` | 269353728 |  |
| `HVAC_FUNC_AUTO_CLOSE_WINDOW_REMIND` | 269418752 |  |
| `HVAC_FUNC_AUTO_CLOSE_WINDOW_REMIND_REQUEST` | 269419008 |  |
| `HVAC_FUNC_AUTO_CLOSE_WINDOW_REMIND_CONFIRM` | 269419264 |  |
| `HVAC_FUNC_AUTO_SECOND_ROW_CLIMATE` | 269484288 |  |
| `HVAC_FUNC_CLIMATE_LOCK` | 269484544 |  |
| `HVAC_FUNC_DISPLAY_WINDOW_TAB` | 269484800 | DISPLAY_WINDOW_TAB_DEFAULT=+1, DISPLAY_WINDOW_TAB_LEFT_TEMP=+2, DISPLAY_WINDOW_TAB_RIGHT_TEMP=+3, DISPLAY_WINDOW_TAB_HARDWARE_POP=+4, DISPLAY_WINDOW_TAB_SEAT=+5, DISPLAY_WINDOW_TAB_IONS_POP=+6 |
| `HVAC_FUNC_G_CLEAN` | 269485056 |  |
| `HVAC_FUNC_AUTOMATIC_VENTILATION_DRY` | 269485312 |  |
| `HVAC_FUNC_AUTO_CZIS` | 269485568 |  |
| `HVAC_FUNC_VENTILATION_ONTIME` | 269485824 |  |
| `HVAC_FUNC_CLIMATE_HARDKEY_SOUND` | 269486080 |  |
| `HVAC_FUNC_INTELLIGENT_RECOMMENDATION` | 269615360 |  |
| `HVAC_FUNC_TEMP_OPTIMIZE` | 269615616 |  |
| `HVAC_FUNC_MODULE_CONNECT_STATUS` | 269680896 |  |
| `HVAC_FUNC_ELECTRICAL_AIR_VENT` | 269746432 |  |
| `HVAC_FUNC_HARDKEY` | 269746688 | HVAC_HARDKEY_LEFT_TEMP=+1, HVAC_HARDKEY_RIGHT_TEMP=+2, HVAC_HARDKEY_FAN_UP=+3, HVAC_HARDKEY_FAN_DOWN=+4, HVAC_HARDKEY_MODE=+5, HVAC_HARDKEY_AUTO=+6, HVAC_HARDKEY_AC=+7, HVAC_HARDKEY_LOOP=+8, HVAC_HARDKEY_FRONT_DEFROST=+9, HVAC_HARDKEY_REAR_DEFROST=+10, HVAC_HARDKEY_AC_MAX=+11, HVAC_HARDKEY_TEMP_SYNC=+11 |
| `HVAC_FUNC_FILTER_ELEMENT_LIFE` | 269746944 |  |
| `HVAC_FUNC_FRONT_NOZZLE_HEATING` | 269747200 |  |
| `HVAC_FUNC_FRONT_WINDSHIELD_HEATING` | 269747456 |  |
| `HVAC_FUNC_FRONT_WINDSHIELD_HEATING_TIMEOUT` | 269747712 | FRONT_WINDSHIELD_HEATING_TIMEOUT=+1 |
| `HVAC_FUNC_DEODORIZATIO_OFF_REASON` | 269748240 |  |
| `SCENE_FUNC_DEODORIZATIO_OFF_BY_MAX_AC` | 269748241 |  |
| `SCENE_FUNC_DEODORIZATIO_OFF_BY_MAX_DEF` | 269748242 |  |
| `SCENE_FUNC_DEODORIZATIO_OFF_BY_VENT` | 269748243 |  |
| `SCENE_FUNC_DEODORIZATIO_OFF_BY_TIME_OUT` | 269748244 |  |
| `SCENE_FUNC_DEODORIZATIO_OFF_BY_VMM_ENABLE` | 269748245 |  |
| `HVAC_FUNC_PM25_SWITCH` | 269811968 | PM25_TPYE_STATE_INITIAL=+1, PM25_TPYE_STATE_COLLECTING=+2, PM25_TPYE_STATE_OFF=+3, PM25_TPYE_STATE_COMPLETED=+4 |
| `HVAC_FUNC_INTELLIGENCE_AC` | 269877504 |  |

### `car.hvac.IFragrance` (9 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `HVAC_FUNC_AIR_FRAGRANCE` | 269156608 |  |
| `HVAC_FUNC_AIR_FRAGRANCE_TYPE` | 269156864 |  |
| `HVAC_FUNC_AIR_FRAGRANCE_LEVEL` | 269157120 |  |
| `HVAC_FUNC_AIR_FRAGRANCE_SLOT` | 269157376 | AIR_FRAGRANCE_SLOT_1=+1, AIR_FRAGRANCE_SLOT_2=+2, AIR_FRAGRANCE_SLOT_3=+3, AIR_FRAGRANCE_SLOT_4=+4, AIR_FRAGRANCE_SLOT_5=+5 |
| `HVAC_FUNC_AIR_FRAGRANCE_TYPE_ID` | 269157632 |  |
| `HVAC_FUNC_AIR_FRAGRANCE_LOW` | 269157888 |  |
| `HVAC_FUNC_AIR_FRAGRANCE_RATIO` | 269158144 |  |
| `HVAC_FUNC_AUTO_REFRESHING_FRAGRANCE` | 269160704 |  |
| `HVAC_FUNC_REFRESHING_FRAGRANCE_POP` | 269160960 | REFRESHING_FRAGRANCE_ACTIVED=+1 |

### `car.vehicle.IBcm` (51 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `BCM_FUNC_WIPER` | 553713920 | WIPER_GEAR_AUTO=+1, WIPER_GEAR_LOW=+2, WIPER_GEAR_HIGHT=+3, WIPER_GEAR_INTERMITTENT=+4 |
| `BCM_FUNC_DOOR` | 553779456 | DOOR_PAUSE=+1 |
| `BCM_FUNC_DOOR_LOCK` | 553779712 |  |
| `BCM_FUNC_DOOR_POS` | 553779968 |  |
| `BCM_FUNC_CHILD_SAFETY_LOCK` | 553780224 |  |
| `BCM_FUNC_CHARGING_CAP` | 553780480 |  |
| `BCM_FUNC_FUEL_CAP` | 553780736 |  |
| `BCM_FUNC_DOOR_CONTROL` | 553783296 |  |
| `BCM_FUNC_DOOR_OBSTACLE_DETECTED` | 553785344 |  |
| `BCM_FUNC_DOOR_ANTI_PINCH` | 553785600 |  |
| `BCM_FUNC_DOOR_STATUS` | 553785856 |  |
| `BCM_FUNC_WINDOW` | 553844992 | WINDOW_PAUSE=+1, WINDOW_HALF=+2, WINDOW_OPEN_PAUSE=+3, WINDOW_CLOSE_PAUSE=+4 |
| `BCM_FUNC_WINDOW_LOCK` | 553845248 |  |
| `BCM_FUNC_WINDOW_POS` | 553845504 |  |
| `BCM_FUNC_SUNROOF_TILT` | 553845760 |  |
| `BCM_FUNC_WINDOW_TRANSPARENCY` | 553846016 |  |
| `BCM_FUNC_WINDOW_CURRENT_POS` | 553846272 |  |
| `BCM_FUNC_WASHER` | 553910528 |  |
| `BCM_FUNC_LIGHT_DIPPED_BEAM` | 553976064 |  |
| `BCM_FUNC_LIGHT_MAIN_BEAM` | 553976320 |  |
| `BCM_FUNC_LIGHT_DRIVING_LAMPS` | 553976576 |  |
| `BCM_FUNC_LIGHT_FRONT_FOG_LAMPS` | 553976832 |  |
| `BCM_FUNC_LIGHT_REAR_FOG_LAMPS` | 553977088 |  |
| `BCM_FUNC_LIGHT_CORNERING_LAMPS` | 553977344 |  |
| `BCM_FUNC_LIGHT_SPOT_LIGHTS` | 553977600 |  |
| `BCM_FUNC_LIGHT_FRONT_POSITION_LAMPS` | 553977856 |  |
| `BCM_FUNC_LIGHT_DAYTIME_RUNNING_LAMPS` | 553978112 |  |
| `BCM_FUNC_LIGHT_DIM_DIP_LAMPS` | 553978368 |  |
| `BCM_FUNC_LIGHT_SIDE_MARKER_LIGHTS` | 553978624 |  |
| `BCM_FUNC_LIGHT_REAR_POSITION_LAMPS` | 553978880 |  |
| `BCM_FUNC_LIGHT_STOP_LAMPS` | 553979136 |  |
| `BCM_FUNC_LIGHT_REVERSING_LAMPS` | 553979392 |  |
| `BCM_FUNC_LIGHT_HAZARD_FLASHERS` | 553979648 |  |
| `BCM_FUNC_LIGHT_ATMOSPHERE_LAMPS` | 553979904 |  |
| `BCM_FUNC_LIGHT_LEFT_TRUN_SIGNAL` | 553980160 |  |
| `BCM_FUNC_LIGHT_RIGHT_TRUN_SIGNAL` | 553980416 |  |
| `BCM_FUNC_LIGHT_READING_LIGHT` | 553980672 |  |
| `BCM_FUNC_LIGHT_REAR_LOGO_LIGHT` | 553980928 |  |
| `BCM_FUNC_LIGHT_GRILLE_LAMP` | 553981184 |  |
| `BCM_FUNC_LIGHT_ALL_WEATHER_LIGHT` | 553981440 |  |
| `BCM_FUNC_LIGHT_NUMBER_PLATE_LIGHT` | 553981696 |  |
| `BCM_FUNC_LIGHT_WELCOME_LIGHT` | 553981952 |  |
| `BCM_FUNC_LIGHT_GRILLE_LAMP_COLOR` | 553983488 | GRILLE_LAMP_COLOR_1=+1, GRILLE_LAMP_COLOR_2=+2, GRILLE_LAMP_COLOR_3=+3 |
| `BCM_FUNC_FOLD_REAR_MIRROR` | 554041600 |  |
| `BCM_FUNC_REAR_MIRROR_ADJUST` | 554041856 |  |
| `BCM_FUNC_STEERING_WHEEL_ADJUST` | 554107136 |  |
| `BCM_FUNC_POWER_ONOFF` | 554696960 |  |
| `BCM_FUNC_DISPLAY_ONOFF` | 554697216 |  |
| `BCM_FUNC_CUSTOM_KEY` | 554762496 |  |
| `BCM_FUNC_WINDOW_MOVING_STATE` | 554762752 |  |
| `BCM_FUNC_ALL_READING_LIGHTS_SWITCH` | 554763008 |  |

### `car.vehicle.IVehicle` (185 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_HMI_THEMES_CHANGE` | 536936704 | HMI_THEMES_VALUE_CLEAR=+1, HMI_THEMES_VALUE_LOUDER=+2, HMI_THEMES_VALUE_INTER=+3, HMI_THEMES_VALUE_HYPER=+4 |
| `SETTING_FUNC_ENGINE_STOP_START` | 537002240 |  |
| `SETTING_FUNC_DRIVER_ALERT_CONTROL` | 537002496 |  |
| `SETTING_FUNC_ESC_SPORT_MODE` | 537002752 |  |
| `SETTING_FUNC_SAILING_MODE` | 537003008 |  |
| `SETTING_FUNC_ENERGY_REGENERATION` | 537003264 | ENERGY_REGENERATION_LEVEL_LOW=+1, ENERGY_REGENERATION_LEVEL_MID=+2, ENERGY_REGENERATION_LEVEL_HIGH=+3 |
| `SETTING_FUNC_DRIVER_PERFOR_SUPPORT` | 537003520 |  |
| `SETTING_FUNC_SPEED_LIMITATION` | 537067776 |  |
| `SETTING_FUNC_SPEED_CONTROL` | 537068032 |  |
| `SETTING_FUNC_SPEED_LIMITATION_MODE` | 537068800 | SPEED_LIMITATION_MODE_AVSL=+1, SPEED_LIMITATION_MODE_ASL=+2 |
| `SETTING_FUNC_SPEED_CONTROL_MODE` | 537069056 | SPEED_CONTROL_MODE_CC=+1, SPEED_CONTROL_MODE_ACC=+2, SPEED_CONTROL_MODE_GPILOT=+3 |
| `SETTING_FUNC_LAMP_TRIPLE_FLASH` | 537133312 |  |
| `SETTING_FUNC_LAMP_HIGHBEAM_ACTIVE` | 537133568 | LAMP_HIGHBEAM_ACTIVE_VALUE_SLOW=+1, LAMP_HIGHBEAM_ACTIVE_VALUE_NORMAL=+2, LAMP_HIGHBEAM_ACTIVE_VALUE_FAST=+3 |
| `SETTING_FUNC_LAMP_AUTOLIGHT` | 537133824 | LAMP_AUTOLIGHT_VALUE_LATER=+1, LAMP_AUTOLIGHT_VALUE_NORMAL=+2, LAMP_AUTOLIGHT_VALUE_EARLIER=+3 |
| `SETTING_FUNC_LAMP_AUX_HIGHBEAM` | 537134080 |  |
| `SETTING_FUNC_LAMP_BENDINGLIGHT` | 537134336 |  |
| `SETTING_FUNC_LAMP_AUTOMATIC_COURTESY_LIGHT` | 537134592 |  |
| `SETTING_FUNC_LAMP_HOME_SAFE_LIGHT` | 537134848 | HOME_SAFE_LIGHT_VALUE_30S=+1, HOME_SAFE_LIGHT_VALUE_60S=+2, HOME_SAFE_LIGHT_VALUE_90S=+3 |
| `SETTING_FUNC_LAMP_DAYTIME_RUNNING_LIGHT` | 537135104 |  |
| `SETTING_FUNC_LAMP_APPROACH_LIGHT` | 537135360 |  |
| `SETTING_FUNC_LAMP_CORNERING_LIGHT` | 537135616 |  |
| `SETTING_FUNC_AUTO_TRAILER_LAMP_CHECK` | 537135872 |  |
| `SETTING_FUNC_MANUAL_TRAILER_LAMP_CHECK` | 537136128 |  |
| `SETTING_FUNC_LAMP_ADAPTIVE_FRONT_LIGHT` | 537136384 |  |
| `SETTING_FUNC_LAMP_EXTERIOR_LIGHT_CONTROL` | 537136640 | LAMP_EXTERIOR_LIGHT_CONTROL_POS_LIGHT=+1, LAMP_EXTERIOR_LIGHT_CONTROL_LOWBEAM=+2, LAMP_EXTERIOR_LIGHT_CONTROL_AUTOMATIC=+3, LAMP_EXTERIOR_LIGHT_CONTROL_AHBC=+4 |
| `SETTING_FUNC_PBC_AUTO_APPLY` | 537264384 |  |
| `SETTING_FUNC_PEB_MODE` | 537264640 | PEB_MODE_PEB=+1, PEB_MODE_MSP=+2 |
| `SETTING_FUNC_PDC_SWITCH` | 537264896 |  |
| `SETTING_FUNC_AUTO_HOLD` | 537265152 | ESC_AVH_SWITCH_AVAILABLE_STATE=+1 |
| `SETTING_FUNC_HDC_SWITCH` | 537265408 | ESC_HDC_SWITCH_AVAILABLE_STATE=+1 |
| `SETTING_FUNC_PBC_EPB_SWITCH` | 537268224 |  |
| `SETTING_FUNC_PBC_DOUBLE_EPB_SWITCH` | 537268480 |  |
| `SETTING_FUNC_TRAILER_MODE` | 537268736 |  |
| `SETTING_FUNC_LANE_KEEPING_AID` | 537329920 |  |
| `SETTING_FUNC_LANE_KEEPING_AID_MODE` | 537330176 | LANE_KEEPING_AID_MODE_WARN_INTV=+1, LANE_KEEPING_AID_MODE_INTV=+2, LANE_KEEPING_AID_MODE_WARN=+3 |
| `SETTING_FUNC_LANE_CHANGE_WARING` | 537330432 |  |
| `SETTING_FUNC_LANE_CHANGE_WARNING_SOUND` | 537330688 | LANE_CHANGE_WARNING_SOUND_LOW=+1, LANE_CHANGE_WARNING_SOUND_MID=+2, LANE_CHANGE_WARNING_SOUND_HIGH=+3 |
| `SETTING_FUNC_LANE_KEEPING_AID_WARNING` | 537330944 | LANE_KEEPING_AID_WARNING_SOUND=+1, LANE_KEEPING_AID_WARNING_HAPTIC=+2, LANE_KEEPING_AID_WARNING_SOUND_HAPTIC=+3 |
| `SETTING_FUNC_EMGY_LANE_KEEP_AID` | 537331200 |  |
| `SETTING_FUNC_LANE_CHANGE_ASSIST` | 537331456 |  |
| `SETTING_FUNC_STEERING_ASSISTANCE_LEVEL` | 537331712 | STEERING_ASSISTANCE_LEVEL_HIGH=+1, STEERING_ASSISTANCE_LEVEL_MEDIUM=+2, STEERING_ASSISTANCE_LEVEL_LOW=+3 |
| `SETTING_FUNC_FRONT_CROSS_TRAFFIC_ALERT` | 537331968 |  |
| `SETTING_FUNC_REAR_CROSS_TRAFFIC_ALERT` | 537332224 |  |
| `SETTING_FUNC_EMGY_LANE_OCC_WARNING` | 537332480 |  |
| `SETTING_FUNC_EVASIVE_MANEUVER_AID` | 537332736 |  |
| `SETTING_FUNC_TRAFFIC_LIGHT_ATTENTION` | 537332992 |  |
| `SETTING_FUNC_AUTONOMOUS_EMERGENCY_BRAKING` | 537333248 |  |
| `SETTING_FUNC_ELE_SEATBELT_COMFORT` | 537333504 |  |
| `SETTING_FUNC_REAR_COLLISION_WARNING` | 537333760 |  |
| `SETTING_FUNC_WINDOW_CLOSE_SUNCURTAIN` | 537395456 |  |
| `SETTING_FUNC_REAR_WINDOW_CLEAN` | 537395712 |  |
| `SETTING_FUNC_AUTO_CLOSE_ROOF_RAINY` | 537395968 |  |
| `SETTING_FUNC_AUTO_CLOSE_WINDOW` | 537396224 | AUTO_CLOSE_WINDOW_VEHICLE_LOCK=+1, AUTO_CLOSE_WINDOW_KEY_LONG_PRESS=+2 |
| `SETTING_FUNC_WINDOW_PINCH_WARN` | 537396480 |  |
| `SETTING_FUNC_WINDOW_VENTILATE` | 537396736 |  |
| `SETTING_FUNC_SUNROOF_TRANSPARENCY_AUTO` | 537396992 |  |
| `SETTING_FUNC_MIRROR_DIMMING` | 537460992 | MIRROR_DIMMING_DARK=+1, MIRROR_DIMMING_NORMAL=+2, MIRROR_DIMMING_LIGHT=+3 |
| `SETTING_FUNC_MIRROR_AUTO_FOLDING` | 537461248 |  |
| `SETTING_FUNC_MIRROR_DIPPING` | 537461504 | MIRROR_DIPPING_DRIVER=+1, MIRROR_DIPPING_PASSENGER=+2, MIRROR_DIPPING_BOTH=+3 |
| `SETTING_FUNC_MIRROR_DIPPING_SWITCH` | 537461760 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_EXPERIENCE` | 537526528 | AMBIENCE_LIGHT_EXPERIENCE_FULL=+1, AMBIENCE_LIGHT_EXPERIENCE_CUSTOM=+2 |
| `SETTING_FUNC_AMBIENCE_LIGHT_MAINCOLOR` | 537526784 | AMBIENCE_LIGHT_MAINCOLOR_THEME=+1, AMBIENCE_LIGHT_MAINCOLOR_DRIVERMODE=+2, AMBIENCE_LIGHT_MAINCOLOR_SETCOLOR=+3, AMBIENCE_LIGHT_MAINCOLOR_MUSIC=+4, AMBIENCE_LIGHT_MAINCOLOR_NON_POLAR=+5, AMBIENCE_LIGHT_MAINCOLOR_BREATHE_MODE=+6, AMBIENCE_LIGHT_MAINCOLOR_SPEED_MODE=+7, AMBIENCE_LIGHT_MAINCOLOR_WEATHER=+8, AMBIENCE_LIGHT_MAINCOLOR_TEMPERATURE_MODE=+9 |
| `SETTING_FUNC_AMBIENCE_LIGHT_BOTZONES` | 537527040 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_TOPZONES` | 537527296 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES` | 537527552 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_BRIGHTNESS_STATIONARY` | 537527808 | BRIGHTNESS_STATIONARY_LOW=+1, BRIGHTNESS_STATIONARY_HIGH=+2 |
| `SETTING_FUNC_AMBIENCE_LIGHT_BRIGHTNESS_DRIVING` | 537528064 | BRIGHTNESS_DRIVING_LOW=+1, BRIGHTNESS_DRIVING_HIGH=+2 |
| `SETTING_FUNC_AMBIENCE_LIGHT_INTERACTIVE_EFFECT` | 537528320 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_COLOR_SET` | 537528576 |  |
| `SETTING_FUNC_TRAFFIC_SIGN_RECOGNITION` | 537592064 |  |
| `SETTING_FUNC_TRAFFIC_SIGN_RECOGNITION_ALERT` | 537592320 |  |
| `SETTING_FUNC_WINDSCREEN_SERVICE_POSITION` | 537657600 |  |
| `SETTING_FUNC_AUTO_REAR_WIPING` | 537657856 |  |
| `SETTING_FUNC_FRONT_WIPER_IDLE` | 537658112 |  |
| `SETTING_FUNC_PARK_ASSIST_SYS_ACTIVATED` | 537723136 |  |
| `SETTING_FUNC_PARK_ASSIST_SYS_VOLUME` | 537723392 | PARK_ASSIST_SYS_VOLUME_LOW=+1, PARK_ASSIST_SYS_VOLUME_MID=+2, PARK_ASSIST_SYS_VOLUME_HIGH=+3 |
| `SETTING_FUNC_FORWARD_COLLISION_WARN` | 537788672 |  |
| `SETTING_FUNC_FORWARD_COLLISION_WARN_SNVTY` | 537788928 | FORWARD_COLLISION_WARN_SNVTY_LOW=+1, FORWARD_COLLISION_WARN_SNVTY_NORMAL=+2, FORWARD_COLLISION_WARN_SNVTY_HIGH=+3 |
| `SETTING_FUNC_PRIVATE_LOCK` | 537854208 |  |
| `SETTING_FUNC_PRIVATE_LOCK_REMINDER` | 537854464 | PRIVATE_LOCK_REMINDER_REMINDER=+1 |
| `SETTING_FUNC_VISIBLE_LOCKING_FEEDBACK` | 537919744 |  |
| `SETTING_FUNC_VISIBLE_UNLOCKING_FEEDBACK` | 537920000 |  |
| `SETTING_FUNC_AUDIBLE_LOCKING_FEEDBACK` | 537920256 |  |
| `SETTING_FUNC_KEYLESS_UNLOCKING` | 537920512 | KEYLESS_UNLOCKING_ALL_DOORS=+1, KEYLESS_UNLOCKING_SINGLE_DOOR=+2 |
| `SETTING_FUNC_REMOTE_UNLOCKING` | 537920768 | REMOTE_UNLOCKING_ALL_DOORS=+1, REMOTE_UNLOCKING_SINGLE_DOOR=+2 |
| `SETTING_FUNC_SPEED_LOCKING` | 537921024 |  |
| `SETTING_FUNC_PASSIVE_ARMING` | 537921280 |  |
| `SETTING_FUNC_REDUCED_GUARD` | 537921536 |  |
| `SETTING_FUNC_CENTRAL_LOCK` | 537921792 |  |
| `SETTING_FUNC_TWOSTEP_UNLOCKING` | 537922048 | TWOSTEP_UNLOCKING_ALL_DOORS=+1, TWOSTEP_UNLOCKING_SINGLE_DOOR=+2 |
| `SETTING_FUNC_HUD_ACTIVE` | 537985280 |  |
| `SETTING_FUNC_HUD_CALIBRATION` | 537985536 |  |
| `SETTING_FUNC_DOOR_OPEN_WARN_ACTIVE` | 538050816 |  |
| `SETTING_FUNC_DOOR_OPEN_WARN_VOLUME` | 538051072 | DOOR_OPEN_WARN_VOLUME_LOW=+1, DOOR_OPEN_WARN_VOLUME_MID=+2, DOOR_OPEN_WARN_VOLUME_HIGH=+3 |
| `SETTING_FUNC_RMS_ACTIVE` | 538116352 |  |
| `SETTING_FUNC_RESET_SETTINGS_DEFAULT` | 538181888 |  |
| `SETTING_FUNC_DAYMODE_SETTING` | 538247424 | DAYMODE_SETTING_DAY=+1, DAYMODE_SETTING_NIGHT=+2, DAYMODE_SETTING_AUTO=+3 |
| `SETTING_FUNC_DAYMODE_SYNC` | 538247680 |  |
| `SETTING_FUNC_BRIGHTNESS_DAY` | 538247936 |  |
| `SETTING_FUNC_BRIGHTNESS_NIGHT` | 538248192 |  |
| `SETTING_FUNC_BRIGHTNESS_MAX` | 538248448 |  |
| `SETTING_FUNC_BRIGHTNESS_MIN` | 538248704 |  |
| `SETTING_FUNC_BRIGHTNESS_STEP` | 538248960 |  |
| `SETTING_FUNC_CAR_LOCATOR` | 538312960 |  |
| `SETTING_FUNC_XCALL_KEY_LOCK` | 538313216 |  |
| `SETTING_FUNC_JOURNAL_LOGS` | 538313472 |  |
| `SETTING_FUNC_CAR_LOCATOR_REMINDER_MODE` | 538313728 | CAR_LOCATOR_REMINDER_MODE_SOUND=+1, CAR_LOCATOR_REMINDER_MODE_LIGHT=+2, CAR_LOCATOR_REMINDER_MODE_LIGHT_SOUND=+3 |
| `SETTING_FUNC_TEM_PROVISIONING_STATE` | 538313984 |  |
| `SETTING_FUNC_TCAM_RESET` | 538314240 |  |
| `SETTING_FUNC_EASY_INGRESS_EGRESS` | 538378496 |  |
| `SETTING_FUNC_E_PEDAL` | 538444032 |  |
| `SETTING_FUNC_SUSPENSION_HEIGHT_ADJUST` | 538509568 | SUSPENSION_HEIGHT_ADJUST_LEVEL_HIGH_2=+1, SUSPENSION_HEIGHT_ADJUST_LEVEL_HIGH_1=+2, SUSPENSION_HEIGHT_ADJUST_LEVEL_NORMAL=+3, SUSPENSION_HEIGHT_ADJUST_LEVEL_LOW_1=+4, SUSPENSION_HEIGHT_ADJUST_LEVEL_LOW_2=+5 |
| `SETTING_FUNC_SUSPENSION_DEACTIVATION_DAMPENING` | 538509824 |  |
| `SETTING_FUNC_SUSPENSION_DRIVER_ENTRY_CONTROL` | 538510080 |  |
| `SETTING_FUNC_REAR_SPOILER_ADJUST` | 538510336 | REAR_SPOILER_ADJUST_P1=+1, REAR_SPOILER_ADJUST_P2=+2 |
| `SETTING_FUNC_ESM_SWITCH` | 538575104 |  |
| `SETTING_FUNC_ESM_VOLUME` | 538575360 | ESM_VOLUME_LEVEL_LOW=+1, ESM_VOLUME_LEVEL_MID=+2, ESM_VOLUME_LEVEL_HIGH=+3 |
| `SETTING_FUNC_ARTIFICIAL_SOUND_SWITCH` | 538575616 |  |
| `SETTING_FUNC_ARTIFICIAL_SOUND_TYPE` | 538575872 | ARTIFICIAL_SOUND_TYPE_1=+1, ARTIFICIAL_SOUND_TYPE_2=+2, ARTIFICIAL_SOUND_TYPE_3=+3, ARTIFICIAL_SOUND_TYPE_4=+4, ARTIFICIAL_SOUND_TYPE_5=+5, ARTIFICIAL_SOUND_TYPE_6=+6, ARTIFICIAL_SOUND_TYPE_7=+7, ARTIFICIAL_SOUND_TYPE_8=+8 |
| `SETTING_FUNC_AVAS_SWITCH` | 538576128 |  |
| `SETTING_FUNC_AVAS_VOLUME` | 538576384 | AVAS_VOLUME_LEVEL_LOW=+1, AVAS_VOLUME_LEVEL_MID=+2, AVAS_VOLUME_LEVEL_HIGH=+3 |
| `SETTING_FUNC_AVAS_SOUND_TYPE` | 538576640 | AVAS_SOUND_TYPE_1=+1, AVAS_SOUND_TYPE_2=+2, AVAS_SOUND_TYPE_3=+3, AVAS_SOUND_TYPE_4=+4, AVAS_SOUND_TYPE_5=+5, AVAS_SOUND_TYPE_6=+6, AVAS_SOUND_TYPE_7=+7, AVAS_SOUND_TYPE_8=+8 |
| `SETTING_FUNC_EXTERNAL_ARTIFICIAL_SOUND_SWITCH` | 538576896 |  |
| `SETTING_FUNC_EXTERNAL_ARTIFICIAL_SOUND_TYPE` | 538577152 |  |
| `SETTING_FUNC_PCM_TIMER` | 538640640 | PARKING_COMFORT_MODE_TIMER_15=+1, PARKING_COMFORT_MODE_TIMER_30=+2, PARKING_COMFORT_MODE_TIMER_45=+3, PARKING_COMFORT_MODE_TIMER_60=+4, PARKING_COMFORT_MODE_TIMER_90=+5, PARKING_COMFORT_MODE_TIMER_120=+6 |
| `SETTING_FUNC_DAYLIGHT_SAVING_TIME` | 538640896 |  |
| `SETTING_FUNC_LOCK_REAR_SEAT_DISPLAY` | 538706176 |  |
| `SETTING_FUNC_FACIAL_RECOGNITION` | 538706432 |  |
| `SETTING_FUNC_VOICE_RECOGNITION` | 538706688 |  |
| `SETTING_FUNC_LOCK_RFDM_REMOTE_CONTROLLER` | 538707968 |  |
| `SETTING_FUNC_SOUND_WARNING_VOLUME` | 538771712 | SOUND_WARNING_VOLUME_LEVEL_LOW=+1, SOUND_WARNING_VOLUME_LEVEL_MID=+2, SOUND_WARNING_VOLUME_LEVEL_HIGH=+3 |
| `SETTING_FUNC_ROTATED_WHEELS_WARNING` | 538771968 |  |
| `SETTING_FUNC_ROTATED_WHEELS_WARNING_INFO` | 538772224 | ROTATED_WHEELS_WARNING_INFO_LEFTWARD=+1, ROTATED_WHEELS_WARNING_INFO_RIGHTWARD=+2 |
| `SETTING_FUNC_BLIND_CAMERA_SYNC_RT_TURN` | 538772480 |  |
| `SETTING_FUNC_PARK_COMFORT_MODE_TIMER` | 538837248 |  |
| `SETTING_FUNC_PARK_COMFORT_MODE_TIMER_MAX` | 538837504 |  |
| `SETTING_FUNC_PARK_COMFORT_MODE_TIMER_MIN` | 538837760 |  |
| `SETTING_FUNC_PARK_COMFORT_MODE_TIMER_STEP` | 538838016 |  |
| `SETTING_FUNC_PARK_COMFORT_MODE_OFF_REASON` | 538838272 | LOW_SOC=+1 |
| `SETTING_FUNC_INTERNAL_COMMUNICATION` | 538902784 |  |
| `SETTING_FUNC_INTERNAL_COMMUNICATION_VOLUME` | 538903040 |  |
| `SETTING_FUNC_ENERGY_PREDICTION_SWITCH` | 538903808 |  |
| `SETTING_FUNC_INTELLIGENT_FUEL_SAVE` | 538904064 |  |
| `SETTING_FUNC_DIM_HOLIDAY_WALLPAPER` | 538904320 |  |
| `SETTING_FUNC_TRACK_MODE` | 538904576 |  |
| `SETTING_FUNC_MAINTENANCE_MILEAGE_RESET` | 538968320 |  |
| `SETTING_FUNC_MAINTENANCE_TIME_RESET` | 538968576 |  |
| `SETTING_FUNC_WALLPAPER_SYNC` | 539033856 |  |
| `SETTING_FUNC_WALLPAPER_SYNC_STYLE` | 539034112 | WALLPAPER_STYLE_1=+1, WALLPAPER_STYLE_2=+2, WALLPAPER_STYLE_3=+3, WALLPAPER_STYLE_4=+4, WALLPAPER_STYLE_5=+5 |
| `SETTING_FUNC_SCREEN_SAVER_STYLE` | 539034368 | SCREEN_SAVER_STYLE_1=+1, SCREEN_SAVER_STYLE_2=+2, SCREEN_SAVER_STYLE_3=+3, SCREEN_SAVER_STYLE_4=+4, SCREEN_SAVER_STYLE_5=+5, SCREEN_SAVER_STYLE_CUSTOM=+255 |
| `SETTING_FUNC_SCREEN_SAVER_CUSTOM_NAME` | 539034624 |  |
| `SETTING_FUNC_SCREEN_SAVER_CUSTOM_TEXT` | 539034880 |  |
| `SETTING_FUNC_SCREEN_SAVER_CUSTOM_PICTURE` | 539035136 |  |
| `SETTING_FUNC_WELCOME_SOUND` | 539099392 |  |
| `SETTING_FUNC_WELCOME_SOUND_TYPE` | 539099648 | WELCOME_SOUND_TYPE_1=+1, WELCOME_SOUND_TYPE_2=+2, WELCOME_SOUND_TYPE_3=+3 |
| `SETTING_FUNC_RVDC` | 539361536 |  |
| `SETTING_FUNC_DATA_COLLECTION` | 539361792 |  |
| `SETTING_FUNC_REMOTE_DIAGNOSTICS` | 539362048 |  |
| `SETTING_FUNC_UNLOCK_P_GEAR` | 539427072 |  |
| `SETTING_FUNC_LIFE_DETECTION` | 539427328 |  |
| `SETTING_FUNC_EYE_BALL_TRACK` | 539427584 |  |
| `SETTING_FUNC_SAY_HI` | 539427840 |  |
| `SETTING_FUNC_PASSENGER_AIRBAG` | 539428096 |  |
| `SETTING_FUNC_WAITING_MODE` | 539428352 |  |
| `SETTING_FUNC_ARTIFICIAL_SOUND_PREVIEW` | 539428608 |  |
| `SETTING_FUNC_LAUNCH_MODE` | 539428864 |  |
| `SETTING_FUNC_LAUNCH_MODE_INDCN` | 539429120 |  |
| `SETTING_FUNC_HILL_START_ASSIST` | 539429376 |  |
| `SETTING_FUNC_ELECTRIC_MILEAGE_DISPLAY_SWITCH` | 539429632 |  |
| `SETTING_FUNC_ABNORMAL_VEHICLE_ALARM` | 539492608 | ABNORMAL_VEHICLE_ALARM_OFF=+1, ABNORMAL_VEHICLE_ALARM_VISUAL_SOUND=+2, ABNORMAL_VEHICLE_ALARM_SOUND=+3 |
| `SETTING_FUNC_DANGEROUS_ROAD_ALARM` | 539492864 | DANGEROUS_ROAD_OFF=+1, DANGEROUS_ROAD_VISUAL_SOUND=+2, DANGEROUS_ROAD_SOUND=+3 |
| `SETTING_FUNC_EMERGENCY_VEHICLE_ALARM` | 539493120 | EMERGENCY_VEHICLE_OFF=+1, EMERGENCY_VEHICLE_VISUAL_SOUND=+2, EMERGENCY_VEHICLE_SOUND=+3 |
| `SETTING_FUNC_CONGESTION_AHEAD_ALARM` | 539493376 | CONGESTION_AHEAD_OFF=+1, CONGESTION_AHEAD_VISUAL_SOUND=+2, CONGESTION_AHEAD_SOUND=+3 |
| `SETTING_FUNC_SPEED_GUIDANCE_ALARM` | 539493632 | SPEED_GUIDANCE_OFF=+1, SPEED_GUIDANCE_VISUAL_SOUND=+2, SPEED_GUIDANCE_SOUND=+3 |
| `SETTING_FUNC_RED_LIGHT_ALARM` | 539493888 | RED_LIGHT_OFF=+1, RED_LIGHT_VISUAL_SOUND=+2, RED_LIGHT_SOUND=+3 |
| `SETTING_FUNC_MULTIMEDIA_GESTURE` | 539494144 |  |
| `SETTING_FUNC_TRACK_GESTURE` | 539494400 |  |
| `SETTING_FUNC_MUSIC_GESTURE` | 539494656 |  |
| `SETTING_FUNC_WALLPAPER_FESTIVAL_TYPE` | 539496192 | FESTIVAL_TYPE_NEW_YEAR=+1, FESTIVAL_TYPE_SPRING_FESTIVAL=+2, FESTIVAL_TYPE_VALENTINE_DAY=+3, FESTIVAL_TYPE_LABOR_DAY=+4, FESTIVAL_TYPE_DRAGON_BOAT_FESTIVAL=+5, FESTIVAL_TYPE_TANABATA_FESTIVAL=+6, FESTIVAL_TYPE_MID_AUTUMN_FESTIVAL=+7, FESTIVAL_TYPE_NATIONAL_DAY=+8, FESTIVAL_TYPE_CHRISTMAS=+9 |
| `SETTING_FUNC_DIGITAL_KEY` | 539496448 |  |
| `SETTING_FUNC_DIGITAL_KEY_REQ_STS` | 539496704 |  |
| `SETTING_FUNC_DIGITAL_KEY_UNPAIR` | 539496960 |  |
| `SETTING_FUNC_DIGITAL_KEY_TERMINATION` | 539497216 |  |
| `SETTING_FUNC_DIGITAL_KEY_SUSPENSION` | 539497472 |  |
| `SETTING_FUNC_TRANSPARENT_UNDERBODY` | 539497728 |  |

### `car.vehicle.ISeat` (36 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_EASY_INGRESS_EGRESS` | 538378496 |  |
| `SETTING_FUNC_DRIVER_CONTROL_PASSENGER_SEAT` | 755040512 |  |
| `SETTING_FUNC_SEAT_SUPPORT_CONTROL_MODE` | 755040768 | SEAT_SUPPORT_CONTROL_MODE_LONG_PRESS=+1, SEAT_SUPPORT_CONTROL_MODE_SHORT_PRESS=+2, SEAT_SUPPORT_CONTROL_MODE_LONG_SHORT_PRESS=+3 |
| `SETTING_FUNC_SEAT_LENGTH` | 755106048 | SEAT_LENGTH_FORWARD=+1, SEAT_LENGTH_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_HEIGHT` | 755106304 | SEAT_HEIGHT_UP=+1, SEAT_HEIGHT_DOWN=+2 |
| `SETTING_FUNC_SEAT_LENGTH_POS` | 755106560 |  |
| `SETTING_FUNC_SEAT_HEIGHT_POS` | 755106816 |  |
| `SETTING_FUNC_SEAT_CUSHION_TILT` | 755171584 | SEAT_CUSHION_TILT_UP=+1, SEAT_CUSHION_TILT_DOWN=+2 |
| `SETTING_FUNC_SEAT_BACKREST` | 755171840 | SEAT_BACKREST_FORWARD=+1, SEAT_BACKREST_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_CUSHION_TILT_POS` | 755172096 |  |
| `SETTING_FUNC_SEAT_BACKREST_POS` | 755172352 |  |
| `SETTING_FUNC_SEAT_CUSHION_SIDE_SUPPORT` | 755237120 | SEAT_CUSHION_SIDE_SUPPORT_UP=+1, SEAT_CUSHION_SIDE_SUPPORT_DOWN=+2 |
| `SETTING_FUNC_SEAT_BACKREST_SIDE_SUPPORT` | 755237376 | SEAT_BACKREST_SIDE_SUPPORT_FORWARD=+1, SEAT_BACKREST_SIDE_SUPPORT_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_HEADREST_HEIGHT` | 755302656 | SEAT_HEADREST_HEIGHT_UP=+1, SEAT_HEADREST_HEIGHT_DOWN=+2 |
| `SETTING_FUNC_SEAT_HEADREST_TILT` | 755302912 | SEAT_HEADREST_TILT_FORWARD=+1, SEAT_HEADREST_TILT_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_HEADREST_HEIGHT_POS` | 755303168 |  |
| `SETTING_FUNC_SEAT_HEADREST_TILT_POS` | 755303424 |  |
| `SETTING_FUNC_SEAT_LUMBAR_HEIGHT` | 755368192 | SEAT_LUMBAR_HEIGHT_UP=+1, SEAT_LUMBAR_HEIGHT_DOWN=+2 |
| `SETTING_FUNC_SEAT_LUMBAR_EXTENDED` | 755368448 | SEAT_LUMBAR_EXTENDED_FORWARD=+1, SEAT_LUMBAR_EXTENDED_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_CUSHION_EXTENSION` | 755433728 | SEAT_CUSHION_EXTENSION_FORWARD=+1, SEAT_CUSHION_EXTENSION_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_LEG_SUPPORT_HEIGHT` | 755499264 | SEAT_LEG_SUPPORT_HEIGHT_UP=+1, SEAT_LEG_SUPPORT_HEIGHT_DOWN=+2 |
| `SETTING_FUNC_SEAT_LEG_SUPPORT_LENGTH` | 755499520 | SEAT_LEG_SUPPORT_LENGTH_FORWARD=+1, SEAT_LEG_SUPPORT_LENGTH_BACKWARD=+2 |
| `SETTING_FUNC_SEAT_LEG_SUPPORT_HEIGHT_POS` | 755499776 |  |
| `SETTING_FUNC_SEAT_LEG_SUPPORT_LENGTH_POS` | 755500032 |  |
| `SETTING_FUNC_SEAT_POSITION_SAVE` | 759169280 | SEAT_POSITION_SAVED_1=+1, SEAT_POSITION_SAVED_2=+2, SEAT_POSITION_SAVED_3=+3, SEAT_POSITION_SAVED_4=+4, SEAT_POSITION_SAVED_5=+5, SEAT_POSITION_SAVED_6=+6, SEAT_POSITION_SAVED_7=+7, SEAT_POSITION_SAVED_8=+8 |
| `SETTING_FUNC_SEAT_POSITION_SET` | 759169536 |  |
| `SETTING_FUNC_SEAT_REST_PATTERN` | 759234816 |  |
| `SETTING_FUNC_SEAT_BOSS_KEY` | 759235072 |  |
| `SETTING_FUNC_SEAT_ONE_KEY_BED` | 759235328 |  |
| `SETTING_FUNC_SEAT_REST_PATTERN_ALARM` | 759235584 |  |
| `SETTING_FUNC_SEAT_REST_PATTERN_ALARM_TIME` | 759235840 |  |
| `SETTING_FUNC_SEAT_HOT_STONE_MASSAGE` | 759236096 |  |
| `SETTING_FUNC_SEAT_FOLD` | 759236352 | SEAT_FOLD_STATE=+1, SEAT_RAISE_STATE=+2, SEAT_IDLE_STATE=+3 |
| `SETTING_FUNC_MULTI_SEAT_MENU` | 759236608 | SEAT_LUMBAR_ADJUST=+1, SEAT_CUSHION_ADJUST=+2, SEAT_BACKREST_SIDE_ADJUST=+3, SEAT_MASSAGE_ADJUST=+4 |
| `SETTING_FUNC_SEAT_MEMORY` | 759300352 |  |
| `SCENE_FUNC_SEAT_REST_PATTERN_OFF_REASON` | 792789264 | NON_P=+1, USAGE_MODE_CHANGE=+2 |

### `car.vehicle.IAmbienceLight` (41 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_AMBIENCE_LIGHT_EXPERIENCE` | 537526528 | AMBIENCE_LIGHT_ZONE_FRONT=+1, AMBIENCE_LIGHT_ZONE_HEADREST=+2, AMBIENCE_LIGHT_ZONE_REAR=+3 |
| `SETTING_FUNC_AMBIENCE_LIGHT_MAINCOLOR` | 537526784 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_BOTZONES` | 537527040 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_TOPZONES` | 537527296 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES` | 537527552 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_BRIGHTNESS_STATIONARY` | 537527808 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_BRIGHTNESS_DRIVING` | 537528064 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_INTERACTIVE_EFFECT` | 537528320 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_COLOR_SET` | 537528576 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_INTENSITY_SET` | 704708864 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_THEME_COLOR` | 704709120 | AMBIENCE_LIGHT_THEME_COLOR_RED=+1, AMBIENCE_LIGHT_THEME_COLOR_ORANGE=+2, AMBIENCE_LIGHT_THEME_COLOR_YELLOW=+3, AMBIENCE_LIGHT_THEME_COLOR_GREEN=+4, AMBIENCE_LIGHT_THEME_COLOR_INDIGO=+5, AMBIENCE_LIGHT_THEME_COLOR_BLUE=+6, AMBIENCE_LIGHT_THEME_COLOR_VIOLET=+7, AMBIENCE_LIGHT_THEME_COLOR_WHITE=+8, AMBIENCE_LIGHT_THEME_COLOR_ICE_BLUE=+9, AMBIENCE_LIGHT_THEME_COLOR_SUN_RED=+10, AMBIENCE_LIGHT_THEME_COLOR_SPANISH_RED=+11, AMBIENCE_LIGHT_THEME_COLOR_APPLE_GREEN=+12, AMBIENCE_LIGHT_THEME_COLOR_PINK=+13 |
| `SETTING_FUNC_BREATHE_MODE_COLOR` | 704709376 |  |
| `SETTING_FUNC_AMBIENCE_ZONES_SYNC` | 704709632 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_BOTZONES_COLOR_SET` | 704774400 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_BOTZONES_INTENSITY` | 704774656 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_TOPZONES_COLOR_SET` | 704839936 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_TOPZONES_INTENSITY` | 704840192 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_COLOR_SET` | 704905472 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_MAINZONES_INTENSITY` | 704905728 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_WELCOME_SHOW` | 704971008 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_GOODBYE_SHOW` | 704971264 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_ENDURANCE_MIL_REMINDER` | 704971520 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_PHONE_CALL_REMINDER` | 704971776 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_STANDSTILL` | 704972032 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_OVER_SPEED_WARNING` | 704972288 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_WELCOME_SHOW_MODE` | 704972544 | WELCOME_SHOW_MODE_PASSIONATE=+1, WELCOME_SHOW_MODE_NORMAL=+2, WELCOME_SHOW_MODE_SUBDUED=+3 |
| `SETTING_FUNC_AMBIENCE_LIGHT_MUSIC_SHOW_MODE` | 704972800 | MUSIC_SHOW_MODE_PASSIONATE=+1, MUSIC_SHOW_MODE_NORMAL=+2, MUSIC_SHOW_MODE_SUBDUED=+3 |
| `SETTING_FUNC_AMBIENCE_LIGHT_VOICE` | 704974080 |  |
| `SETTING_FUNC_MOOD_LIGHT` | 705036544 |  |
| `SETTING_FUNC_MOOD_LIGHT_COLOR` | 705036800 | MOOD_LIGHT_COLOR_RED=+1, MOOD_LIGHT_COLOR_ORANGE=+2, MOOD_LIGHT_COLOR_YELLOW=+3, MOOD_LIGHT_COLOR_GREEN=+4, MOOD_LIGHT_COLOR_INDIGO=+5, MOOD_LIGHT_COLOR_BLUE=+6, MOOD_LIGHT_COLOR_VIOLET=+7, MOOD_LIGHT_COLOR_WHITE=+8 |
| `SETTING_FUNC_MOOD_LIGHT_MODE` | 705037056 | MOOD_LIGHT_MODE_TEMP=+1, MOOD_LIGHT_MODE_COLOR=+2 |
| `SETTING_FUNC_TRANSITION_MODE` | 705102080 |  |
| `SETTING_FUNC_TRANSITION_START_COLOR` | 705102336 |  |
| `SETTING_FUNC_TRANSITION_END_COLOR` | 705102592 |  |
| `SETTING_FUNC_BLUETOOTH_CALLING_STATUS` | 705102848 |  |
| `SETTING_FUNC_ALCM_VOICE_MODE` | 705103104 | VOICE_MODE_START_RECORD=+1, VOICE_MODE_END_RECORD=+2, VOICE_MODE_START_ASR=+3, VOICE_MODE_END_ASR=+4, VOICE_MODE_START_NLU=+5, VOICE_MODE_END_NLU=+6, VOICE_MODE_START_TTS=+7, VOICE_MODE_END_TTS=+8, VOICE_MODE_NLU_IMPL_SUCCESS=+9, VOICE_MODE_NLU_IMPL_FAIL=+10, VOICE_MODE_RESERVED=+11 |
| `SETTING_FUNC_ALCM_SOUND_PCT` | 705103360 | VOICE_PCT_RESERVED=+101, VOICE_PCT_NO_VOICE=+127 |
| `SETTING_FUNC_AMBIENCE_LIGHT_EFFECT_SET` | 705167616 | AMBIENCE_LIGHT_CUSTOM_MODE_SOLID_COLOR=+1, AMBIENCE_LIGHT_THEME_MODE_RADICAL=+1, AMBIENCE_LIGHT_CUSTOM_MODE_GRADIENTS=+2, AMBIENCE_LIGHT_THEME_MODE_SIMPLE=+2, AMBIENCE_LIGHT_CUSTOM_MODE_BREATHE=+3, AMBIENCE_LIGHT_THEME_MODE_LIBERATING=+3, AMBIENCE_LIGHT_THEME_MODE_AGILE=+4, AMBIENCE_LIGHT_THEME_MODE_FASHION=+5, AMBIENCE_LIGHT_THEME_MODE_EV=+6 |
| `SETTING_FUNC_AMBIENCE_LIGHT_CLIMATE` | 705167872 |  |
| `SETTING_FUNC_AMBIENCE_LIGHT_ICHARGING_REMIND` | 705168128 |  |
| `SETTING_FUNC_AMBIENCE_THEME_MODE` | 705168384 |  |

### `car.vehicle.ILamp` (23 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_LAMP_TRIPLE_FLASH` | 537133312 |  |
| `SETTING_FUNC_LAMP_HIGHBEAM_ACTIVE` | 537133568 |  |
| `SETTING_FUNC_LAMP_AUTOLIGHT` | 537133824 |  |
| `SETTING_FUNC_LAMP_AUX_HIGHBEAM` | 537134080 |  |
| `SETTING_FUNC_LAMP_BENDINGLIGHT` | 537134336 |  |
| `SETTING_FUNC_LAMP_AUTOMATIC_COURTESY_LIGHT` | 537134592 |  |
| `SETTING_FUNC_LAMP_HOME_SAFE_LIGHT` | 537134848 |  |
| `SETTING_FUNC_LAMP_DAYTIME_RUNNING_LIGHT` | 537135104 |  |
| `SETTING_FUNC_LAMP_APPROACH_LIGHT` | 537135360 |  |
| `SETTING_FUNC_LAMP_CORNERING_LIGHT` | 537135616 |  |
| `SETTING_FUNC_AUTO_TRAILER_LAMP_CHECK` | 537135872 |  |
| `SETTING_FUNC_MANUAL_TRAILER_LAMP_CHECK` | 537136128 |  |
| `SETTING_FUNC_LAMP_ADAPTIVE_FRONT_LIGHT` | 537136384 |  |
| `SETTING_FUNC_LAMP_EXTERIOR_LIGHT_CONTROL` | 537136640 |  |
| `SETTING_FUNC_LAMP_ACTIVE_HIGH_BEAM_CONTROL` | 721486080 | FCS_IHBC_STATUS=+1 |
| `SETTING_FUNC_LAMP_FLASHER_ANIM_CONTROL` | 721486336 | LAMP_FLASHER_ANIM_CONTROL_NORMAL=+1, LAMP_FLASHER_ANIM_CONTROL_FASHION=+2 |
| `SETTING_FUNC_LAMP_LR_TRAFFIC_LIGHT` | 721551616 | LR_TRAFFIC_LIGHT_LEFT=+1, LR_TRAFFIC_LIGHT_RIGHT=+2 |
| `SETTING_FUNC_LAMP_LOW_BEAM_VERTICAL_LEVEL` | 721551872 | LOW_BEAM_VERTICAL_LEVEL_1=+1, LOW_BEAM_VERTICAL_LEVEL_2=+2, LOW_BEAM_VERTICAL_LEVEL_3=+3, LOW_BEAM_VERTICAL_LEVEL_4=+4, LOW_BEAM_VERTICAL_LEVEL_5=+5 |
| `SETTING_FUNC_LAMP_LOW_BEAM_VERTICAL_LEVEL_ADJUST` | 721552128 |  |
| `SETTING_FUNC_WELCOME_LIGHT` | 721617152 |  |
| `SETTING_FUNC_WELCOME_LIGHT_MODE` | 721617408 | WELCOME_LIGHT_MODE_1=+1, WELCOME_LIGHT_MODE_2=+2, WELCOME_LIGHT_MODE_3=+3, WELCOME_LIGHT_MODE_4=+4, WELCOME_LIGHT_MODE_5=+5, WELCOME_LIGHT_MODE_6=+6 |
| `SETTING_FUNC_BLN_SWITCH` | 721617664 |  |
| `SETTING_FUNC_TRAILER_LAMP` | 721617920 |  |

### `car.vehicle.IDriveMode` (24 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `DM_FUNC_DRIVE_MODE_SELECT` | 570491136 | DRIVE_MODE_SELECTION_ECO=+1, DRIVE_MODE_SELECTION_COMFORT=+2, DRIVE_MODE_SELECTION_DYNAMIC=+3, DRIVE_MODE_SELECTION_XC=+4, DRIVE_MODE_SELECTION_HDC=+5, DRIVE_MODE_SELECTION_PURE=+6, DRIVE_MODE_SELECTION_HYBRID=+7, DRIVE_MODE_SELECTION_POWER=+8, DRIVE_MODE_SELECTION_SNOW=+9, DRIVE_MODE_SELECTION_MUD=+10, DRIVE_MODE_SELECTION_ROCK=+11, DRIVE_MODE_SELECTION_PHEV=+12, DRIVE_MODE_SELECTION_SAND=+13, DRIVE_MODE_SELECTION_AWD=+14, DRIVE_MODE_SELECTION_SAVE=+15, DRIVE_MODE_SELECTION_ECO_HEV_PHEV=+16, DRIVE_MODE_SELECTION_NORMAL=+17, DRIVE_MODE_SELECTION_OFFROAD=+19, DRIVE_MODE_ECO_PLUS=+20, DRIVE_MODE_SPORT_PLUS=+21, DRIVE_MODE_SELECTION_ADAPTIVE=+22, DRIVE_MODE_SELECTION_CUSTOM=+64 |
| `DM_FUNC_ECO_BUTTON` | 570556672 |  |
| `DM_FUNC_DM_CUSTOM_PROPULSION_SYS` | 570622208 | CUSTOM_PROPULSION_SYS_ECO=+1, CUSTOM_PROPULSION_SYS_COMFORT=+2, CUSTOM_PROPULSION_SYS_SPORT=+3, CUSTOM_PROPULSION_SYS_OFFROAD=+4, CUSTOM_PROPULSION_SYS_SNOW=+5, CUSTOM_PROPULSION_SYS_SAND=+6, CUSTOM_PROPULSION_SYS_HYBRID=+7, CUSTOM_PROPULSION_SYS_PURE=+8, CUSTOM_PROPULSION_SYS_POWER=+9, CUSTOM_PROPULSION_SYS_AWD=+10 |
| `DM_FUNC_DM_CUSTOM_SUSPENSION_MODE` | 570622464 | CUSTOM_SUSPENSION_MODE_STANDARD=+1, CUSTOM_SUSPENSION_MODE_COMFORT=+2, CUSTOM_SUSPENSION_MODE_SPORT=+3, CUSTOM_SUSPENSION_MODE_OFFROAD=+4, CUSTOM_SUSPENSION_MODE_SNOW=+5, CUSTOM_SUSPENSION_MODE_AUTOMATIC=+6 |
| `DM_FUNC_DM_CUSTOM_RAB` | 570622720 | CUSTOM_RAB_STANDARD=+1, CUSTOM_RAB_SPORT=+2 |
| `DM_FUNC_DM_CUSTOM_BPF` | 570622976 | CUSTOM_BPF_STANDARD=+1, CUSTOM_BPF_SPORT=+2 |
| `DM_FUNC_DM_CUSTOM_STEERING_WHEEL_FEEL` | 570624256 | CUSTOM_STEERING_WHEEL_FEEL_LIGHT=+1, CUSTOM_STEERING_WHEEL_FEEL_BALANCED=+2, CUSTOM_STEERING_WHEEL_FEEL_HEAVY=+3 |
| `DM_FUNC_DM_CUSTOM_CLIMATE_MODE` | 570624512 | CUSTOM_CLIMATE_MODE_NORMAL=+1, CUSTOM_CLIMATE_MODE_ECO=+2 |
| `DM_FUNC_DM_CUSTOM_INFOR_THEME` | 570624768 | CUSTOM_INFOR_THEME_LOUDER=+1, CUSTOM_INFOR_THEME_HYPER=+2, CUSTOM_INFOR_THEME_INTER=+3, CUSTOM_INFOR_THEME_CLEAR=+4 |
| `DM_FUNC_DM_CUSTOM_DRIVER_INFO` | 570625024 | CUSTOM_DRIVER_INFO_STANDARD=+1, CUSTOM_DRIVER_INFO_ECO=+2, CUSTOM_DRIVER_INFO_SPORT=+3, CUSTOM_DRIVER_INFO_OFFROAD=+4 |
| `DM_FUNC_DM_CUSTOM_INTERIOR_LIGHT` | 570625280 | CUSTOM_INTERIOR_LIGHT_ECO=+1, CUSTOM_INTERIOR_LIGHT_COMFORT=+2, CUSTOM_INTERIOR_LIGHT_SPORT=+3, CUSTOM_INTERIOR_LIGHT_OFFROAD=+4, CUSTOM_INTERIOR_LIGHT_SNOW=+5, CUSTOM_INTERIOR_LIGHT_SAND=+6, CUSTOM_INTERIOR_LIGHT_HYBRID=+7, CUSTOM_INTERIOR_LIGHT_PURE=+8, CUSTOM_INTERIOR_LIGHT_STANDARD=+9 |
| `DM_FUNC_DM_CUSTOM_EN_START_STOP` | 570625536 |  |
| `DM_FUNC_DIM_THEME_SYNC_DRIVEMODE` | 570687744 |  |
| `DM_FUNC_DIM_THEME_SET` | 570688000 | DIM_THEME_SET_RED=+1, DIM_THEME_SET_GOLD=+2, DIM_THEME_SET_BLUE=+3 |
| `DM_FUNC_STEERING_WHEEL_FEEL_SYNC_DRIVEMODE` | 570688256 |  |
| `DM_FUNC_STEERING_DRIVEMODE` | 570688512 | STEERING_MODE_SOFT=+1, STEERING_MODE_DYNAMIC=+2 |
| `DM_FUNC_ENERGY_DRIVEMODE` | 570688768 | ENERGY_MODE_RANGE=+1, ENERGY_MODE_TOUR=+2, ENERGY_MODE_SPORT=+3 |
| `DM_FUNC_CREEP_SET` | 570689024 |  |
| `DM_FUNC_LAUNCH_CONTROL_SET` | 570689280 |  |
| `DM_FUNC_NOISE_CONTROL_SET` | 570689536 |  |
| `SETTING_FUNC_SPEED_LIMIT_RANGE_MODE_VALUE` | 570689792 |  |
| `SETTING_FUNC_SPEED_LIMIT_RANGE_MODE_VALUE_MAX` | 570690048 |  |
| `SETTING_FUNC_SPEED_LIMI_RANGE_MODE_VALUE_MIN` | 570690304 |  |
| `SETTING_FUNC_SPEED_LIMIT_RANGE_MODE_VALUE_STEP` | 570690560 |  |

### `car.vehicle.IDayMode` (24 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_DAYMODE_SETTING` | 538247424 |  |
| `SETTING_FUNC_DAYMODE_SYNC` | 538247680 |  |
| `SETTING_FUNC_BRIGHTNESS_DAY` | 538247936 |  |
| `SETTING_FUNC_BRIGHTNESS_NIGHT` | 538248192 |  |
| `SETTING_FUNC_BRIGHTNESS_MAX` | 538248448 |  |
| `SETTING_FUNC_BRIGHTNESS_MIN` | 538248704 |  |
| `SETTING_FUNC_BRIGHTNESS_STEP` | 538248960 |  |
| `SETTING_FUNC_BACKLIGHT_LINKAGE` | 687931648 |  |
| `SETTING_FUNC_BRIGHTNESS_BACKLIGHT` | 687997184 |  |
| `SETTING_FUNC_BRIGHTNESS_BACKLIGHT_MAX` | 687997440 |  |
| `SETTING_FUNC_BRIGHTNESS_BACKLIGHT_MIN` | 687997696 |  |
| `SETTING_FUNC_BRIGHTNESS_BACKLIGHT_STEP` | 687997952 |  |
| `SETTING_FUNC_BRIGHTNESS_DIM` | 687998208 |  |
| `SETTING_FUNC_BRIGHTNESS_DIM_MAX` | 687998464 |  |
| `SETTING_FUNC_BRIGHTNESS_DIM_MIN` | 687998720 |  |
| `SETTING_FUNC_BRIGHTNESS_DIM_STEP` | 687998976 |  |
| `SETTING_FUNC_BRIGHTNESS_FLOODLIGHT` | 687999232 |  |
| `SETTING_FUNC_BRIGHTNESS_FLOODLIGHT_MAX` | 687999488 |  |
| `SETTING_FUNC_BRIGHTNESS_FLOODLIGHT_MIN` | 687999744 |  |
| `SETTING_FUNC_BRIGHTNESS_FLOODLIGHT_STEP` | 688000000 |  |
| `SETTING_FUNC_ELECTRIC_REAR_VIEW_MIRROR_DISPLAYS` | 688000256 |  |
| `SETTING_FUNC_ELECTRIC_REAR_VIEW_MIRROR_DISPLAYS_MIN` | 688000512 |  |
| `SETTING_FUNC_ELECTRIC_REAR_VIEW_MIRROR_DISPLAYS_MAX` | 688000768 |  |
| `SETTING_FUNC_ELECTRIC_REAR_VIEW_MIRROR_DISPLAYS_STEP` | 688062720 |  |

### `car.vehicle.IAudio` (7 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_SOUND_WARNING_VOLUME` | 538771712 |  |
| `SETTING_FUNC_ACOUSTIC_EFFECT_MODE` | 771817728 | ACOUSTIC_EFFECT_MODE_JAZZ=+1, ACOUSTIC_EFFECT_MODE_OPERA=+2, ACOUSTIC_EFFECT_MODE_THEATRE=+3 |
| `SETTING_FUNC_HXT_SWITCH` | 771817984 |  |
| `SETTING_FUNC_CAE_SWITCH` | 771818240 |  |
| `SETTING_FUNC_MULTIMEDIA_SOUND_EFFECT` | 771818496 |  |
| `SETTING_FUNC_SOFT_BUTTON_SOUND_TYPE` | 771883264 | SOFT_BUTTON_SOUND_TYPE_1=+1, SOFT_BUTTON_SOUND_TYPE_2=+2, SOFT_BUTTON_SOUND_TYPE_3=+3 |
| `SETTING_FUNC_AUDIO_SEPARATED` | 771948800 |  |

### `car.vehicle.ISceneMode` (14 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SCENE_FUNC_THEATER_MODE` | 788594944 |  |
| `SCENE_FUNC_WASH_MODE` | 788595200 |  |
| `SCENE_FUNC_CLEAN_MODE` | 788595456 |  |
| `SCENE_FUNC_BIOCHEMICAL_MODE` | 788595712 |  |
| `SCENE_FUNC_PET_MODE` | 788595968 |  |
| `SCENE_FUNC_SMOKING` | 788660480 |  |
| `SCENE_FUNC_AWAKENING` | 788660736 | FAN_OFF=+17, TIME_OUT=+18, USAGEMODE_CHANGE=+19 |
| `SCENE_FUNC_AWAKENING_OFF_REASON` | 788660752 | FAN_OFF=+1, TIME_OUT=+2, USAGEMODE_CHANGE=+3 |
| `SCENE_FUNC_PARENT_CHILD` | 788660992 |  |
| `SCENE_FUNC_YUEDONG` | 788661248 |  |
| `SCENE_FUNC_NORMAL_MODE` | 788661504 |  |
| `SCENE_FUNC_ROMANTIC_MODE` | 788661760 |  |
| `SCENE_FUNC_KING_MODE` | 788662016 |  |
| `SCENE_FUNC_NAP_MODE` | 788662272 |  |

### `car.vehicle.ISafety` (29 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_VISIBLE_LOCKING_FEEDBACK` | 537919744 |  |
| `SETTING_FUNC_VISIBLE_UNLOCKING_FEEDBACK` | 537920000 |  |
| `SETTING_FUNC_AUDIBLE_LOCKING_FEEDBACK` | 537920256 |  |
| `SETTING_FUNC_KEYLESS_UNLOCKING` | 537920512 |  |
| `SETTING_FUNC_REMOTE_UNLOCKING` | 537920768 |  |
| `SETTING_FUNC_SPEED_LOCKING` | 537921024 |  |
| `SETTING_FUNC_PASSIVE_ARMING` | 537921280 |  |
| `SETTING_FUNC_REDUCED_GUARD` | 537921536 |  |
| `SETTING_FUNC_CENTRAL_LOCK` | 537921792 |  |
| `SETTING_FUNC_TWOSTEP_UNLOCKING` | 537922048 |  |
| `SETTING_FUNC_APPROACH_UNLOCK` | 738263296 | APPROACH_UNLOCK_DOORS=+17, APPROACH_UNLOCK_SINGLE_DOOR=+18 |
| `SETTING_FUNC_APPROACH_UNLOCK_DOOR_AREA` | 738263312 | APPROACH_UNLOCK_DOORS=+1, APPROACH_UNLOCK_SINGLE_DOOR=+2 |
| `SETTING_FUNC_AWAY_LOCK` | 738263552 |  |
| `SETTING_FUNC_APPROACH_TAIL_UNLOCK` | 738264320 |  |
| `SETTING_FUNC_KEYLESS_TRUNK_UNLOCK` | 738264576 |  |
| `SETTING_FUNC_TRUNK_UNLOCK_DISTANCE` | 738264832 | TRUNK_UNLOCK_DISTANCE_LEVEL_1=+1, TRUNK_UNLOCK_DISTANCE_LEVEL_2=+2 |
| `SETTING_FUNC_TRUNK_OPENING_POSITION` | 738265088 | TRUNK_OPENING_POSITION_LEVEL_1=+1, TRUNK_OPENING_POSITION_LEVEL_2=+2, TRUNK_OPENING_POSITION_LEVEL_3=+3, TRUNK_OPENING_POSITION_LEVEL_4=+4, TRUNK_OPENING_POSITION_LEVEL_5=+5 |
| `SETTING_FUNC_AUTO_POWER_DOOR` | 738265344 |  |
| `SETTING_FUNC_P_GEAR_UNLOCK` | 738265600 |  |
| `SETTING_FUNC_TERMINAL_NOT_OFF_WARN` | 738328832 |  |
| `SETTING_FUNC_KEY_INCAR_REMINDER` | 738329088 |  |
| `SETTING_FUNC_WARNING_TYPE` | 738329344 | WARNING_TYPE_LIGHT=+1, WARNING_TYPE_LIGHT_VOICE=+2 |
| `SETTING_FUNC_ANY_DOOR_LOCK_WARNING` | 738329600 |  |
| `SETTING_FUNC_DOUBLE_LOCK` | 738329856 |  |
| `SETTING_FUNC_TRUNK_STATE` | 738330112 | TRUNK_STATE_UNKNOW=+1, TRUNK_STATE_FULL_CLOSE=+2, TRUNK_STATE_MOVE_UP=+3, TRUNK_STATE_MOVE_UP_BREAK=+4, TRUNK_STATE_STOP_DURING_OPEN=+5, TRUNK_STATE_FULL_OPEN=+6, TRUNK_STATE_MOVE_DOWN=+7, TRUNK_STATE_MOVE_DOWN_BREAK=+8, TRUNK_STATE_STOP_DURING_CLOSE=+9, TRUNK_STATE_HALF_CLOSE=+16, TRUNK_STATE_STOP_MIN_POSITION=+17 |
| `SETTING_FUNC_TRUNK_TAILGATE_TIME_OUT` | 738330368 |  |
| `SETTING_FUNC_KEY_LOST_WARNING` | 738330624 | KEY_LOST_WARNING_OFF=+1, KEY_LOST_WARNING_VISUAL=+2, KEY_LOST_WARNING_VISUAL_AUDIO=+3 |
| `SETTING_FUNC_ENGINE_OFF_UNLOCKING` | 739246848 | ENGINE_OFF_UNLOCKING_ALL_DOORS=+1, ENGINE_OFF_UNLOCKING_SINGLE_DOOR=+2 |
| `SETTING_FUNC_SPEED_AUTO_LOCKING_MODE` | 739247104 | SPEED_AUTO_LOCKING_MODE_10KM=+1, SPEED_AUTO_LOCKING_MODE_20KM=+2 |

### `car.vehicle.IADAS` (58 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_DRIVER_PERFOR_SUPPORT` | 537003520 |  |
| `SETTING_FUNC_SPEED_LIMITATION_MODE` | 537068800 |  |
| `SETTING_FUNC_SPEED_CONTROL_MODE` | 537069056 |  |
| `SETTING_FUNC_PEB_MODE` | 537264640 |  |
| `SETTING_FUNC_PDC_SWITCH` | 537264896 |  |
| `SETTING_FUNC_HDC_SWITCH` | 537265408 |  |
| `SETTING_FUNC_LANE_KEEPING_AID` | 537329920 |  |
| `SETTING_FUNC_LANE_KEEPING_AID_MODE` | 537330176 |  |
| `SETTING_FUNC_LANE_CHANGE_WARING` | 537330432 |  |
| `SETTING_FUNC_LANE_CHANGE_WARNING_SOUND` | 537330688 |  |
| `SETTING_FUNC_LANE_KEEPING_AID_WARNING` | 537330944 |  |
| `SETTING_FUNC_EMGY_LANE_KEEP_AID` | 537331200 |  |
| `SETTING_FUNC_LANE_CHANGE_ASSIST` | 537331456 |  |
| `SETTING_FUNC_STEERING_ASSISTANCE_LEVEL` | 537331712 |  |
| `SETTING_FUNC_FRONT_CROSS_TRAFFIC_ALERT` | 537331968 |  |
| `SETTING_FUNC_REAR_CROSS_TRAFFIC_ALERT` | 537332224 |  |
| `SETTING_FUNC_EMGY_LANE_OCC_WARNING` | 537332480 |  |
| `SETTING_FUNC_EVASIVE_MANEUVER_AID` | 537332736 |  |
| `SETTING_FUNC_TRAFFIC_LIGHT_ATTENTION` | 537332992 |  |
| `SETTING_FUNC_AUTONOMOUS_EMERGENCY_BRAKING` | 537333248 |  |
| `SETTING_FUNC_ELE_SEATBELT_COMFORT` | 537333504 |  |
| `SETTING_FUNC_REAR_COLLISION_WARNING` | 537333760 |  |
| `SETTING_FUNC_TRAFFIC_SIGN_RECOGNITION` | 537592064 |  |
| `SETTING_FUNC_TRAFFIC_SIGN_RECOGNITION_ALERT` | 537592320 |  |
| `SETTING_FUNC_FORWARD_COLLISION_WARN` | 537788672 |  |
| `SETTING_FUNC_FORWARD_COLLISION_WARN_SNVTY` | 537788928 |  |
| `SETTING_FUNC_DOOR_OPEN_WARN_ACTIVE` | 538050816 |  |
| `SETTING_FUNC_DOOR_OPEN_WARN_VOLUME` | 538051072 |  |
| `SETTING_FUNC_TRAFFIC_LIGHT_ATTENTION_SOUND` | 671154432 |  |
| `SETTING_FUNC_DRIVER_PERFOR_SUPPORT_REMINDER` | 671219968 |  |
| `SETTING_FUNC_LANE_DEPARTURE_WARNING` | 671285504 |  |
| `SETTING_FUNC_AUTO_LANE_CHANGE_ASSIST` | 671351040 |  |
| `SETTING_FUNC_LANE_CHANGE_ASSIST_WARNING` | 671351296 |  |
| `SETTING_FUNC_PDC_WARNING_VOLUME` | 671416576 | PDC_WARNING_VOLUME_LOW=+1, PDC_WARNING_VOLUME_MID=+2, PDC_WARNING_VOLUME_HIGH=+3 |
| `SETTING_FUNC_DOW_WARNING_STATUS` | 671416832 | DOW_WARNING_STATUS_LEVEL_1=+1, DOW_WARNING_STATUS_LEVEL_2=+2, DOW_WARNING_STATUS_LEVEL_3=+3 |
| `SETTING_FUNC_SPEED_LIMIT_WARN` | 671482112 |  |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_MODE` | 671482368 | SPEED_LIMIT_WARNING_MODE_NO_WARNING=+1, SPEED_LIMIT_WARNING_MODE_FLASHING=+2, SPEED_LIMIT_WARNING_MODE_SOUND=+3 |
| `SETTING_FUNC_ACC_WITH_TSR` | 671482624 |  |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET` | 671482880 | SPEED_LIMIT_WARNING_OFFSET_0KM=+1, SPEED_LIMIT_WARNING_OFFSET_5KM=+2, SPEED_LIMIT_WARNING_OFFSET_10KM=+3, SPEED_LIMIT_WARNING_OFFSET_MINUS_5KM=+4, SPEED_LIMIT_WARNING_OFFSET_MINUS_10KM=+5 |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE` | 671483136 |  |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE_MAX` | 671483392 |  |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE_MIN` | 671483648 |  |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE_STEP` | 671483904 |  |
| `SETTING_FUNC_SPEED_LIMIT_WARNING_OFFSET_VALUE_SWITCH` | 671484160 |  |
| `SETTING_FUNC_BIG_DATA_SPEED_LIMIT` | 671484416 |  |
| `SETTING_FUNC_OTHER_ROAD_SIGH_INFO` | 671485952 |  |
| `SETTING_FUNC_BLIND_SPOT_DETECTION` | 671547648 |  |
| `SETTING_FUNC_BLIND_SPOT_DETECTION_WARNING` | 671547904 |  |
| `SETTING_FUNC_PRE_COLLISION_SYS` | 671548160 |  |
| `SETTING_FUNC_DRIVE_PILOT` | 671548416 |  |
| `SETTING_FUNC_AI_DRIVER_ASSIST` | 671613184 |  |
| `SETTING_FUNC_AI_ASSIST_DEFAULT_ON` | 671613440 |  |
| `SETTING_FUNC_AI_ASSIST_FUSION_NAVI` | 671613696 |  |
| `SETTING_FUNC_AI_ASSIST_OUT_OVERTAKING_LANE` | 671613952 |  |
| `SETTING_FUNC_AI_ASSIST_LANE_CHANGE_STRATEGY` | 671614208 | AI_ASSIST_LANE_CHANGE_STRATEGY_GENTLE=+1, AI_ASSIST_LANE_CHANGE_STRATEGY_STANDARD=+2, AI_ASSIST_LANE_CHANGE_STRATEGY_RADICAL=+3 |
| `SETTING_FUNC_AI_ASSIST_LANE_CHANGE_CONFIRM` | 671614464 |  |
| `SETTING_FUNC_AI_ASSIST_LANE_CHANGE_WARNING` | 671614720 | AI_ASSIST_LANE_CHANGE_WARNING_VOICE=+1, AI_ASSIST_LANE_CHANGE_WARNING_VIBRATE=+2, AI_ASSIST_LANE_CHANGE_WARNING_BOTH=+3 |
| `SETTING_FUNC_IPK_SVA_AUDIBLE_WARNING_CFG` | 671678976 |  |

### `car.vehicle.IPAS` (59 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `PAS_FUNC_PAS_VOLUME` | 537723392 |  |
| `PAS_FUNC_PAS_STATUS` | 587268352 |  |
| `PAS_FUNC_PAS_MUTE` | 587268608 |  |
| `PAS_FUNC_PAS_TRAILER_PRESENT` | 587268864 |  |
| `PAS_FUNC_PAS_TOP_VIEW` | 587269120 |  |
| `PAS_FUNC_PAS_SHOW_GRAPHICS` | 587269376 |  |
| `PAS_FUNC_PAS_RADAR_FRONT_INNER_LEFT` | 587333888 |  |
| `PAS_FUNC_PAS_RADAR_FRONT_INNER_RIGHT` | 587334144 |  |
| `PAS_FUNC_PAS_RADAR_FRONT_OUT_LEFT` | 587334400 |  |
| `PAS_FUNC_PAS_RADAR_FRONT_OUT_RIGHT` | 587334656 |  |
| `PAS_FUNC_PAS_RADAR_FRONT_LEFT_SIDE` | 587334912 |  |
| `PAS_FUNC_PAS_RADAR_FRONT_RIGHT_SIDE` | 587335168 |  |
| `PAS_FUNC_PAS_RADAR_REAR_LEFT_SIDE` | 587335424 |  |
| `PAS_FUNC_PAS_RADAR_REAR_RIGHT_SIDE` | 587335680 |  |
| `PAS_FUNC_PAS_RADAR_REAR_OUT_LEFT` | 587335936 |  |
| `PAS_FUNC_PAS_RADAR_REAR_OUT_RIGHT` | 587336192 |  |
| `PAS_FUNC_PAS_RADAR_REAR_INNER_LEFT` | 587336448 |  |
| `PAS_FUNC_PAS_RADAR_REAR_INNER_RIGHT` | 587336704 |  |
| `PAS_FUNC_PAS_RADAR_MAX_DISTANCE` | 587336960 |  |
| `PAS_FUNC_PAS_RADAR_MIN_DISTANCE` | 587337216 |  |
| `PAS_FUNC_PAS_RADAR_WORK_MODE` | 587337728 | PAS_RADAR_WORK_MODE_STANDBY=+1, PAS_RADAR_WORK_MODE_FRONT_REAR_ACTIVE=+2, PAS_RADAR_WORK_MODE_FRONT_ACTIVE=+3, PAS_RADAR_WORK_MODE_REAR_ACTIVE=+4, PAS_RADAR_WORK_MODE_FAILURE=+5, PAS_RADAR_WORK_MODE_INHIBITED=+6 |
| `PAS_FUNC_PAS_RADAR_WORK_STATUS` | 587337984 | PAS_RADAR_WORK_STATUS_FRONT_ACTIVE_REAR_FAILURE=+1, PAS_RADAR_WORK_STATUS_REAR_ACTIVE_FRONT_FAILURE=+2, PAS_RADAR_WORK_STATUS_REAR_INACTIVE_FRONT_FAILURE=+3, PAS_RADAR_WORK_STATUS_FRONT_INACTIVE_REAR_FAILURE=+4 |
| `PAS_FUNC_PAC_ACTIVATION` | 587399424 |  |
| `PAS_FUNC_PAC_STEER_LINK` | 587399680 |  |
| `PAS_FUNC_PAC_AUTO_FRONT_ACTIV` | 587399936 |  |
| `PAS_FUNC_PAC_AUTO_REVERSE_CAMERA` | 587400192 | AUTO_REVERSE_CAMERA_REAR=+1, AUTO_REVERSE_CAMERA_TOP=+2 |
| `PAS_FUNC_PAC_CAMERA_TYPE` | 587400448 | PAC_CAMERA_REARONLY=+1, PAC_CAMERA_360CAM=+2 |
| `PAS_FUNC_PAC_OVERLAY_STEERPATH` | 587401216 |  |
| `PAS_FUNC_PAC_OVERLAY_TOWBAR` | 587401472 |  |
| `PAS_FUNC_PAC_OVERLAY_DSTINFO` | 587401728 |  |
| `PAS_FUNC_PAC_VIEW_SELECTION` | 587403520 | PAC_VIEW_SELECTION_FRONT=+1, PAC_VIEW_SELECTION_FRONT_JUNC=+2, PAC_VIEW_SELECTION_FRONT_TOP=+3, PAC_VIEW_SELECTION_TOP=+4, PAC_VIEW_SELECTION_REAR=+5, PAC_VIEW_SELECTION_REAR_JUNC=+6, PAC_VIEW_SELECTION_ZOOMED_REAR=+7, PAC_VIEW_SELECTION_REAR_TOP=+8, PAC_VIEW_SELECTION_DOUBLE_SIDE=+9, PAC_VIEW_SELECTION_3DVIEW=+10, PAC_VIEW_SELECTION_2DVIEW=+11, PAC_VIEW_SELECTION_REAR_LEFT_3D=+12, PAC_VIEW_SELECTION_REAR_RIGHT_3D=+13 |
| `PAS_FUNC_PAC_3DVIEW_POSITION` | 587403776 | PAC_3DVIEW_POSITION_FRONT_CENTER=+1, PAC_3DVIEW_POSITION_FRONT_RIGHT=+2, PAC_3DVIEW_POSITION_FRONT_LEFT=+3, PAC_3DVIEW_POSITION_LEFT=+4, PAC_3DVIEW_POSITION_RIGHT=+5, PAC_3DVIEW_POSITION_REAR_CENTER=+6, PAC_3DVIEW_POSITION_REAR_LEFT=+7, PAC_3DVIEW_POSITION_REAR_RIGHT=+8 |
| `PAS_FUNC_PAC_SYS_AVA_STATUS` | 587404032 | PAC_SYS_AVA_STATUS_AVAILABLE=+1, PAC_SYS_AVA_STATUS_UNAVAILABLE=+2, PAC_SYS_AVA_STATUS_SERVICE_REQUIRED=+3, PAC_SYS_AVA_STATUS_HADNSHAKE_TIMEOUT=+4 |
| `PAS_FUNC_PAC_3DVIEW_LOCK` | 587404288 |  |
| `PAS_FUNC_PAC_APP_INIT_COMPLETED` | 587404544 |  |
| `PAS_FUNC_PAC_CAR_MODE_TRANSPARENT` | 587407616 |  |
| `PAS_FUNC_PAC_NEARBY_OBJ_TRIGGER` | 587407872 |  |
| `PAS_FUNC_PAC_OBSTACLE_DETECTION` | 587408128 |  |
| `PAS_FUNC_PAC_TOP_VIEW_ZOOM_IN` | 587408384 |  |
| `PAS_FUNC_PAC_TOURING_VIEW` | 587408640 |  |
| `PAS_FUNC_SAP_ACTIVATION` | 587464960 |  |
| `PAS_FUNC_SAP_PARK_TYPE` | 587465216 | SAP_PARK_TYPE_IN=+1, SAP_PARK_TYPE_OUT=+2 |
| `PAS_FUNC_SAP_PARK_IN_TYPE` | 587465472 | SAP_PARK_IN_TYPE_PERP=+1, SAP_PARK_IN_TYPE_PARA=+2 |
| `PAS_FUNC_SAP_PARK_IN_RESUME` | 587465728 |  |
| `PAS_FUNC_SAP_PARK_OUT_COMFIRM` | 587465984 |  |
| `PAS_FUNC_SAP_PROGRESS` | 587466240 |  |
| `PAS_FUNC_SAP_PARK_IN_TYPE_RECOMMEND` | 587466496 | SAP_REC_PARK_IN_TYPE_PERP=+1, SAP_REC_PARK_IN_TYPE_PARA=+2 |
| `PAS_FUNC_SAP_PARK_IN_NOTI` | 587469056 | SAP_PARK_IN_NOTI_NO_NOTIFICATION=+1, SAP_PARK_IN_NOTI_SCAN_RIGHT=+2, SAP_PARK_IN_NOTI_SCAN_LEFT=+3, SAP_PARK_IN_NOTI_SLOT_FOUND_CON_FWD=+4, SAP_PARK_IN_NOTI_OVER_SPEED_SCAN=+5, SAP_PARK_IN_NOTI_PARK_FOUND_STOP=+6, SAP_PARK_IN_NOTI_PUT_REV_GEAR=+7, SAP_PARK_IN_NOTI_PUT_REV_GEAR_AGAIN=+8, SAP_PARK_IN_NOTI_LOOK_AROUND_LEFT=+9, SAP_PARK_IN_NOTI_LOOK_AROUND_RIGHT=+10, SAP_PARK_IN_NOTI_FIRST_REV_SLOWLY=+11, SAP_PARK_IN_NOTI_STOP_PUT_FWD_GEAR=+12, SAP_PARK_IN_NOTI_DRV_FWD_SLOWLY=+13, SAP_PARK_IN_NOTI_STOP_PUT_REV_GEAR=+14, SAP_PARK_IN_NOTI_REV_SLOWLY=+15, SAP_PARK_IN_NOTI_FINISHED=+16, SAP_PARK_IN_NOTI_FAIL_TOO_MANY_MOVES=+18, SAP_PARK_IN_NOTI_TEMP_NOT_AVAIL=+19, SAP_PARK_IN_NOTI_CANCEL_HIGH_SPEED=+20, SAP_PARK_IN_NOTI_CANCEL_SLIPPERY_ROAD=+21, SAP_PARK_IN_NOTI_CANCEL_TRAILER_CONNECT=+22, SAP_PARK_IN_NOTI_CANCEL_STEERING_INTERVENTION=+23, SAP_PARK_IN_NOTI_CANCEL_DRIVER_DEACTIVATION=+24, SAP_PARK_IN_NOTI_CANCEL_WRONG_GEAR=+25, SAP_PARK_IN_NOTI_RESUMED=+26, SAP_PARK_IN_NOTI_EMGY_BRK_ACTV=+27, SAP_PARK_IN_NOTI_EMGY_BRK_DIS=+28 |
| `PAS_FUNC_SAP_PARK_OUT_NOTI` | 587469312 | SAP_PARK_OUT_NO_NOTIFICATION=+1, SAP_PARK_OUT_SELECT_PARK_OUT_CONFIRM=+2, SAP_PARK_OUT_SELECT_PARK_OUT=+3, SAP_PARK_OUT_SCAN_LEFT=+4, SAP_PARK_OUT_SCAN_RIGHT=+5, SAP_PARK_OUT_CHANGE_GEAR=+6, SAP_PARK_OUT_CHANGE_GEAR_REV=+7, SAP_PARK_OUT_LOOK_AROUND_LEFT=+8, SAP_PARK_OUT_LOOK_AROUND_RIGHT=+9, SAP_PARK_OUT_REV_SLOWLY=+10, SAP_PARK_OUT_STOP_PUT_FWD_GEAR=+11, SAP_PARK_OUT_DRV_FWD_SLOWLY=+12, SAP_PARK_OUT_STOP_PUT_REV_GEAR=+13, SAP_PARK_OUT_FINISHED=+14, SAP_PARK_OUT_FAIL_TOO_MANY_MOVES=+15, SAP_PARK_OUT_FAIL_NO_PATH=+16, SAP_PARK_OUT_TEMP_NOT_AVAIL=+17, SAP_PARK_OUT_CANCEL_HIGH_SPEED=+18, SAP_PARK_OUT_CANCEL_SLIPPERY_ROAD=+19, SAP_PARK_OUT_CANCEL_TRAILER_CONNECT=+20, SAP_PARK_OUT_CANCEL_STEERING_INTERVENTION=+21, SAP_PARK_OUT_CANCEL_DRIVER_DEACTIVATION=+22, SAP_PARK_OUT_RESUMED=+23, SAP_PARK_OUT_SLOT_TOO_SMALL=+24, SAP_PARK_OUT_NOTI_EMGY_BRK_ACTV=+25, SAP_PARK_OUT_NOTI_EMGY_BRK_DIS=+26 |
| `PAS_FUNC_RCTA_ACTIVATION` | 587530496 |  |
| `PAS_FUNC_RCTA_LEFT_WARNING` | 587530752 |  |
| `PAS_FUNC_RCTA_RIGHT_WARNING` | 587531008 |  |
| `PAS_FUNC_RCTA_SHOW_GRAPHICS` | 587531264 |  |
| `PAS_FUNC_RCTA_WARNING_VOLUME` | 587531520 | RCTA_WARNING_VOLUME_LOW=+1, RCTA_WARNING_VOLUME_MID=+2, RCTA_WARNING_VOLUME_HIGH=+3 |
| `PAS_FUNC_APA_SELF_RECOMMENDED` | 587596032 |  |
| `PAS_FUNC_APA_RPA_SWITCH` | 587596288 |  |
| `PAS_FUNC_PAS_ACTIVATED` | 587661568 |  |
| `PAS_FUNC_AVP_ACTIVATED` | 588251392 |  |
| `PAS_FUNC_ELE_MIRROR_SYS_ACTIVATED` | 588251648 |  |

### `car.vehicle.IHUD` (17 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_HUD_ACTIVE` | 537985280 |  |
| `SETTING_FUNC_HUD_CALIBRATION` | 537985536 |  |
| `SETTING_FUNC_HUD_BRIGHTNESS` | 654377216 |  |
| `SETTING_FUNC_HUD_BRIGHTNESS_MAX` | 654377472 |  |
| `SETTING_FUNC_HUD_BRIGHTNESS_MIN` | 654377728 |  |
| `SETTING_FUNC_HUD_BRIGHTNESS_STEP` | 654377984 |  |
| `SETTING_FUNC_HUD_BRIGHTNESS_ADJUST` | 654378240 |  |
| `SETTING_FUNC_HUD_POSITION_ADJUST` | 654378496 |  |
| `SETTING_FUNC_HUD_ANGLE_ADJUST` | 654378752 |  |
| `SETTING_FUNC_HUD_ANGLE_RESET` | 654379008 |  |
| `SETTING_FUNC_HUD_SNOW_MODE` | 654442752 |  |
| `SETTING_FUNC_HUD_AR_ENGINE` | 654443008 |  |
| `SETTING_FUNC_HUD_DISPLAY_SAFETY` | 654508288 |  |
| `SETTING_FUNC_HUD_DISPLAY_MEIDA` | 654508544 |  |
| `SETTING_FUNC_HUD_DISPLAY_NAVI` | 654508800 |  |
| `SETTING_FUNC_HUD_DISPLAY_BTPHONE` | 654509056 |  |
| `SETTING_FUNC_HUD_DISPLAY_DRIVE_ENVIRONMENT` | 654509312 |  |

### `car.vehicle.IUnits` (8 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `FUNC_UNIT_AVG_FUEL` | 620822784 | UNIT_AVG_FUEL_L_100KM=+1, UNIT_AVG_FUEL_KM_L=+2, UNIT_AVG_FUEL_US_MPG=+3, UNIT_AVG_FUEL_UK_MPG=+4 |
| `FUNC_UNIT_DRIVEN_DISTANCE` | 620823040 | UNIT_DRIVEN_DISTANCE_KM=+1, UNIT_DRIVEN_DISTANCE_MILES=+2 |
| `FUNC_UNIT_TEMPERATURE` | 620823296 | UNIT_TEMPERATURE_C=+1, UNIT_TEMPERATURE_F=+2 |
| `FUNC_UNIT_TIRE_PRESSURE` | 620823552 | UNIT_TIRE_PRESSURE_KPA=+1, UNIT_TIRE_PRESSURE_BAR=+2, UNIT_TIRE_PRESSURE_PSI=+3 |
| `FUNC_UNIT_SPEED` | 620823808 | UNIT_SPEED_KM_H=+1, UNIT_SPEED_MPH=+2 |
| `FUNC_UNIT_TIME_INDICATION` | 620888320 | UNIT_TIME_INDICATION_AM_PM=+1, UNIT_TIME_INDICATION_24H=+2 |
| `FUNC_UNIT_DATE_FORMAT` | 620888576 | UNIT_DATE_FORMAT_YMD=+1, UNIT_DATE_FORMAT_DMY=+2, UNIT_DATE_FORMAT_MDY=+3 |
| `FUNC_UNIT_WARN_SPEED` | 620888832 |  |

### `car.vehicle.IHybrid` (4 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `HYBRID_FUNC_POWER_FLOW` | 604045568 | POWER_FLOW_MAIN_CHARGE=+1, POWER_FLOW_BOOST=+2, POWER_FLOW_EAWD=+3, POWER_FLOW_ENGINEONLY=+4, POWER_FLOW_ENGINEONLY_CHARGE=+5, POWER_FLOW_ELEC=+6, POWER_FLOW_STILL_ENGINEOFF=+7, POWER_FLOW_STILL_ENGINEON=+8, POWER_FLOW_STILL_ENGINEON_CHARGE=+9, POWER_FLOW_ENGINEOFF_REGBRAKE=+10, POWER_FLOW_ENGINEON_REGBRAKE=+11, POWER_FLOW_ENGINEON_REGBRAKE_CHARGE=+12, POWER_FLOW_SAILING=+13, POWER_FLOW_CHARGE_AC=+14, POWER_FLOW_CHARGE_DC=+15, POWER_FLOW_DISCHARGE=+16, POWER_FLOW_PURE_ELE_AWD=+17, POWER_FLOW_FRONT_ELE_DRIVE=+18, POWER_FLOW_REAR_ELE_DRIVE=+19, POWER_FLOW_STANDSTILL=+20, POWER_FLOW_REGENERATION=+21 |
| `HYBRID_FUNC_BATTERY_SAVE_MODE` | 604111104 |  |
| `HYBRID_FUNC_BATTERY_CHARGE_MODE` | 604111360 |  |
| `HYBRID_FUNC_BATTERY_SOC` | 604176640 |  |

### `car.vehicle.IWpc` (2 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `WPC_FUNC_WORK_MODE` | 637600000 | WORK_MODE_AUTO=+1 |
| `WPC_FUNC_CHARGE_STATES` | 637665536 | CHARGE_STATE_NO_DEVICE=+1, CHARGE_STATE_STANDBY=+2, CHARGE_STATE_CHARGING=+3, CHARGE_STATE_FULLY_CHARGED=+4, CHARGE_STATE_ERROR=+5, CHARGE_STATE_OVERHEAT=+6, CHARGE_STATE_OVERPOWER=+7, CHARGE_STATE_FOD=+8, CHARGE_STATE_PEPS_INTERRUPT=+9, CHARGE_STATE_OVERVOLTAGE=+16 |

### `car.hev.ICharging` (55 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `CHARGE_FUNC_CHARGING` | 605028608 |  |
| `CHARGE_FUNC_CHARGING_SOC` | 605028864 |  |
| `CHARGE_FUNC_CHARGING_SOC_MAX` | 605029120 |  |
| `CHARGE_FUNC_CHARGING_SOC_MIN` | 605029376 |  |
| `CHARGE_FUNC_CHARGING_SOC_STEP` | 605029632 |  |
| `CHARGE_FUNC_CHARGING_CURRENT` | 605029888 |  |
| `CHARGE_FUNC_CHARGING_CURRENT_MAX` | 605030144 |  |
| `CHARGE_FUNC_CHARGING_CURRENT_MIN` | 605030400 |  |
| `CHARGE_FUNC_CHARGING_CURRENT_STEP` | 605030656 |  |
| `CHARGE_FUNC_MAINTAIN_BATTERY_TEMP` | 605030912 | WARM_UP_ECO=+17, WARM_UP_SPORT=+18 |
| `CHARGE_FUNC_WARM_UP_LEVEL` | 605030928 | WARM_UP_ECO=+1, WARM_UP_SPORT=+2 |
| `CHARGE_FUNC_WARM_UP` | 605030944 |  |
| `CHARGE_FUNC_ADJUST_MAX_CURRENT` | 605031168 |  |
| `CHARGE_FUNC_EXTENDED_BATTERY_LIFE` | 605031424 |  |
| `CHARGE_FUNC_BATTERY_STABILITY` | 605031680 |  |
| `CHARGE_FUNC_EXTERNAL_CHARGING_LIGHT` | 605031936 |  |
| `CHARGE_FUNC_BATTERY_TEMP_MAINTAIN_STATUS` | 605032192 | BATTERY_TEMP_MAINTAIN_STATUS_MAINTAIN=+1, BATTERY_TEMP_MAINTAIN_STATUS_STANDBY=+2 |
| `CHARGE_FUNC_PRE_CHARGING` | 605094144 |  |
| `CHARGE_FUNC_PRE_CHARGING_SOC` | 605094400 |  |
| `CHARGE_FUNC_PRE_CHARGING_CURRENT` | 605094656 |  |
| `CHARGE_FUNC_PRE_CHARGING_STATUS` | 605094912 | PRE_CHARGING_STATUS_SUCCEED=+1, PRE_CHARGING_STATUS_FAILED=+2, PRE_CHARGING_STATUS_FAILURE=+3, PRE_CHARGING_STATUS_SCHEDULING=+4, PRE_CHARGING_STATUS_CHARGING=+5, PRE_CHARGING_STATUS_CANCELED=+6, PRE_CHARGING_STATUS_CANCEL_FAILED=+7 |
| `CHARGE_FUNC_DISCHARGING_SWITCH_V2V` | 605159680 |  |
| `CHARGE_FUNC_DISCHARGING_SWITCH_V2L` | 605159936 |  |
| `CHARGE_FUNC_DISCHARGING_SOC` | 605160192 |  |
| `CHARGE_FUNC_DISCHARGING_SOC_MAX` | 605160448 |  |
| `CHARGE_FUNC_DISCHARGING_SOC_MIN` | 605160704 |  |
| `CHARGE_FUNC_DISCHARGING_SOC_STEP` | 605160960 |  |
| `CHARGE_FUNC_AUTO_DISCHARGE_LEVEL` | 605161216 |  |
| `CHARGE_FUNC_CHARGING_PLUG_TYPE` | 605225216 | CHARGE_PLUG_AC=+1, CHARGE_PLUG_DC=+2, CHARGE_PLUG_DISCHARGE=+3, CHARGE_PLUG_NOTMATCH=+4 |
| `CHARGE_FUNC_CHARGING_PLUG_STATE` | 605225472 | CHARGE_PLUG_STATE_PREPARED=+1, CHARGE_PLUG_STATE_CHARGING=+2, CHARGE_PLUG_STATE_COMPLETED=+3, CHARGE_PLUG_STATE_MULTI=+4, CHARGE_PLUG_STATE_ERROR=+5, CHARGE_PLUG_STATE_DISCHARGING=+6, CHARGE_PLUG_STATE_DISCHARGING_COMPLETED=+7, CHARGE_PLUG_STATE_HEATING=+8, CHARGE_PLUG_STATE_CONNECTED=+9, CHARGE_PLUG_STATE_DISCONNECTED=+10, CHARGE_PLUG_STATE_CHARGING_PAUSE=+11, CHARGE_PLUG_STATE_DISCHARGING_PAUSE=+12, CHARGE_PLUG_STATE_RESERVE_WAITING=+13, CHARGE_PLUG_STATE_CHARGING_FAIL=+14, CHARGE_PLUG_STATE_DISCHARGING_FAIL=+15 |
| `CHARGE_FUNC_CHARGING_STATE` | 605225728 |  |
| `CHARGE_FUNC_CHARGING_WORK_VOLTAGE` | 605290752 |  |
| `CHARGE_FUNC_CHARGING_WORK_CURRENT` | 605291008 |  |
| `CHARGE_FUNC_CHARGING_ESTIMATED_TIME` | 605291264 |  |
| `CHARGE_FUNC_CHARGING_SPEED` | 605291520 |  |
| `CHARGE_FUNC_CHARGING_ENERGY` | 605291776 |  |
| `CHARGE_FUNC_DISCHARGING_WORK_VOLTAGE` | 605356288 |  |
| `CHARGE_FUNC_DISCHARGING_WORK_CURRENT` | 605356544 |  |
| `CHARGE_FUNC_DISCHARGING_ESTIMATED_TIME` | 605356800 |  |
| `CHARGE_FUNC_DISCHARGING_ENETGY` | 605357056 |  |
| `CHARGE_FUNC_PHEV_BATTERY_CHARGING_MODE` | 605357568 | PHEV_CHARGE_MODE_ACTIVE=+1, PHEV_CHARGE_MODE_SOC_HOLD=+2, PHEV_CHARGE_MODE_OFF=+3 |
| `CHARGE_FUNC_PHEV_RARELY_CHARGING_MODE` | 605357824 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING` | 606077184 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_MODE` | 606077440 | TRAVEL_APPOINT_CHARGING_MODE_ONCE=+1, TRAVEL_APPOINT_CHARGING_MODE_WEEKDATS=+2, TRAVEL_APPOINT_CHARGING_MODE_ALLWEEK=+3, TRAVEL_APPOINT_CHARGING_MODE_CUSTOM=+4 |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_ONCE` | 606077696 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_RESTORE` | 606077952 |  |
| `CHARGE_FUNC_TRAVEL_HVAC` | 606078208 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_MON` | 606081280 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_TUE` | 606081536 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_WED` | 606081792 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_THUR` | 606082048 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_FRI` | 606082304 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_SAT` | 606082560 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_CUS_SUN` | 606082816 |  |
| `CHARGE_FUNC_TRAVEL_APPOINT_CHARGING_PEAK_VALLEY_PERIOD` | 606085120 |  |

### `car.hev.ITripData` (4 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `TRIP_FUNC_RESET_OPTION` | 612368640 | RESET_OPTION_AUTO=+1, RESET_OPTION_MANUAL=+2 |
| `TRIP_FUNC_RESET` | 612368896 |  |
| `TRIP_FUNC_AUTO_RESET_OPTION` | 612369152 | AUTO_RESET_OPTION_4_HOURS=+1, AUTO_RESET_OPTION_CHARGING=+2, AUTO_RESET_OPTION_PARKING_OIL=+3, AUTO_RESET_OPTION_PARKING=+4 |
| `TRIP_FUNC_DRIVING_COMPUTER` | 612369920 | TRIP_DC_SUBTOTAL_MILEAGE=+1, TRIP_DC_AVERAGE_SPEED=+2, TRIP_DC_TRAVEL_TIME=+3, TRIP_DC_AVERAGE_FUEL_CONSUMPTION=+4, TRIP_DC_AVERAGE_POWER_CONSUMPTION=+5, TRIP_DC_ALL=+6 |

### `dvr.forp.ISettings` (15 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `SETTING_FUNC_VIDEO_RESOLUTION` | 1048832 | RESOLUTION_720P_30FPS=+1, RESOLUTION_1080P_30FPS=+2, RESOLUTION_360P_30FPS=+3 |
| `SETTING_FUNC_CRASH_SENSITIVITY_LEVEL` | 1049088 | CRASH_SENSITIVITY_LEVEL_LOW=+1, CRASH_SENSITIVITY_LEVEL_MIDDLE=+2, CRASH_SENSITIVITY_LEVEL_HIGH=+3 |
| `SETTING_FUNC_MIC_MUTE` | 1049344 |  |
| `SETTING_FUNC_PARK_MONITOR` | 1049600 |  |
| `SETTING_FUNC_RECORDING_DURATION` | 1049856 | RECORDING_DURATION_1_MINUTES=+1, RECORDING_DURATION_3_MINUTES=+2, RECORDING_DURATION_5_MINUTES=+3 |
| `SETTING_FUNC_SHUTDOWN_DELAY` | 1050112 | SHUTDOWN_DELAY_10S=+1, SHUTDOWN_DELAY_30S=+2, SHUTDOWN_DELAY_60S=+3, SHUTDOWN_DELAY_180S=+4, SHUTDOWN_DELAY_300S=+5 |
| `SETTING_FUNC_AUTO_EMERGENCY_OVERRIDE` | 1050368 |  |
| `SETTING_FUNC_SHOW_TIME` | 1050624 |  |
| `SETTING_FUNC_PREVIEW_OPT` | 1050880 | PREVIEW_OPT_FRONT=+1, PREVIEW_OPT_INNER=+2, PREVIEW_OPT_PIP_FRONT=+3, PREVIEW_OPT_PIP_INNER=+4 |
| `SETTING_FUNC_WDR` | 1051136 |  |
| `SETTING_FUNC_EMER_RECORDING_DURATION` | 1051392 | EMER_RECORDING_DURATION_10S=+1, EMER_RECORDING_DURATION_20S=+2, EMER_RECORDING_DURATION_30S=+3 |
| `SETTING_FUNC_SDCARD_TOTAL_SIZE` | 1051648 |  |
| `SETTING_FUNC_SDCARD_REMAINED_SIZE` | 1051904 |  |
| `SETTING_FUNC_SDCARD_USED_PERCENTAGE` | 1051920 |  |
| `SETTING_FUNC_GENERAL_RECORDING_ENABLE` | 1052160 |  |

### `diminteraction.IClimateInteraction` (4 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `HVAC_FUNC_TEMP` | 268828928 |  |
| `HVAC_FUNC_TEMP_MAX` | 268829184 |  |
| `HVAC_FUNC_TEMP_MIN` | 268829440 |  |
| `HVAC_FUNC_TEMP_UNIT` | 268830208 | TEMPERATURE_UNIT_C=+1, TEMPERATURE_UNIT_F=+2 |

### `car.vendor.IVendorFuncs` (1 hàm)

| Hàm | ID | Giá trị |
|---|---|---|
| `VENDOR_FUNC_CUSTOM_KEY` | -2147417856 | CUSTOM_KEY_TYPE_DVR=+1, CUSTOM_KEY_TYPE_360_PANORAMA=+2, CUSTOM_KEY_TYPE_NAVIGATION=+3, CUSTOM_KEY_TYPE_DIM_FULL_SCREEN_MAP=+4, CUSTOM_KEY_TYPE_SOUND_SWITCH=+5, CUSTOM_KEY_TYPE_COLLECT_FAV=+6, CUSTOM_KEY_TYPE_CAR_SETTING=+7, CUSTOM_KEY_TYPE_WHEEL_HEAT=+8 |

---

## Phụ lục B: Thuộc tính VHAL ghi được (410)

| Tên | ID | Quyền |
|---|---|---|
| `0x21e00666` | `0x21e00666` | chỉ ghi |
| `AP_POWER_STATE_REPORT` | `0x11410a01` | chỉ ghi |
| `DISPLAY_BRIGHTNESS` | `0x11400a03` | đọc/ghi · vùng 0: 0–100 |
| `HVAC_AC_ON` | `0x15200505` | đọc/ghi |
| `HVAC_AUTO_ON` | `0x1520050a` | đọc/ghi |
| `HVAC_AUTO_RECIRC_ON` | `0x15200512` | đọc/ghi |
| `HVAC_DEFROSTER` | `0x13200504` | đọc/ghi |
| `HVAC_DUAL_ON` | `0x15200509` | đọc/ghi |
| `HVAC_FAN_DIRECTION` | `0x15400501` | đọc/ghi |
| `HVAC_FAN_SPEED` | `0x15400500` | đọc/ghi · vùng 75: 1–7 |
| `HVAC_MAX_AC_ON` | `0x15200506` | đọc/ghi |
| `HVAC_MAX_DEFROST_ON` | `0x15200507` | đọc/ghi |
| `HVAC_POWER_ON` | `0x15200510` | đọc/ghi |
| `HVAC_RECIRC_ON` | `0x15200508` | đọc/ghi |
| `HVAC_STEERING_WHEEL_HEAT` | `0x1140050d` | đọc/ghi · vùng 0: -2–2 |
| `HVAC_TEMPERATURE_DISPLAY_UNITS` | `0x1140050e` | đọc/ghi |
| `HVAC_TEMPERATURE_SET` | `0x15600503` | đọc/ghi · vùng 31: 16.0–32.0 vùng 44: 16.0–32.0 |
| `INFO_ID_ACON` | `0x28700011` | đọc/ghi |
| `INFO_ID_AC_AUTODRYING` | `0x28700026` | đọc/ghi |
| `INFO_ID_AC_AUTO_AIR_PURIFICATION_SWITCH` | `0x287001a7` | đọc/ghi |
| `INFO_ID_AC_AUTO_ION_CONFIRM` | `0x28700248` | chỉ ghi |
| `INFO_ID_AC_AUTO_RATING_SETTING` | `0x287001a8` | đọc/ghi |
| `INFO_ID_AC_BTCALLING_STATUS` | `0x28700020` | chỉ ghi |
| `INFO_ID_AC_CABINCLEAN` | `0x28700027` | đọc/ghi |
| `INFO_ID_AC_DRVVENT` | `0x28700017` | đọc/ghi |
| `INFO_ID_AC_ECOCLIMATESTS` | `0x28700022` | đọc/ghi |
| `INFO_ID_AC_FRAGERANCE_LEVEL` | `0x28700025` | đọc/ghi |
| `INFO_ID_AC_FRAGERANCE_SWICTH` | `0x28700023` | đọc/ghi |
| `INFO_ID_AC_FRAGERANCE_TASTE` | `0x28700024` | đọc/ghi |
| `INFO_ID_AC_FWASHHEAT` | `0x2870001d` | đọc/ghi |
| `INFO_ID_AC_GCLEAN` | `0x2870001e` | đọc/ghi |
| `INFO_ID_AC_KEY` | `0x28700001` | đọc/ghi |
| `INFO_ID_AC_MAX` | `0x28700002` | đọc/ghi |
| `INFO_ID_AC_PARKINGCLIMATESET` | `0x2870001f` | đọc/ghi |
| `INFO_ID_AC_PASVENT` | `0x28700018` | đọc/ghi |
| `INFO_ID_AC_PM25POPUPREQ` | `0x28700019` | đọc/ghi |
| `INFO_ID_AC_PM25_SWITCH` | `0x287001a6` | đọc/ghi |
| `INFO_ID_AC_POWER` | `0x28700000` | chỉ ghi |
| `INFO_ID_AC_SWHEAT` | `0x2870001c` | đọc/ghi |
| `INFO_ID_AC_TIMEVENT_AFTERNOON_SET` | `0x2870002b` | đọc/ghi |
| `INFO_ID_AC_TIMEVENT_EVENING_SET` | `0x2870002c` | đọc/ghi |
| `INFO_ID_AC_TIMEVENT_MORNING_SET` | `0x2870002a` | đọc/ghi |
| `INFO_ID_AC_TIMEVENT_ONSWITCH` | `0x28700028` | đọc/ghi |
| `INFO_ID_AC_TIMEVENT_SET` | `0x28700029` | đọc/ghi |
| `INFO_ID_ADAS_DOW_ENABLE_STATE` | `0x287000fc` | đọc/ghi |
| `INFO_ID_ADAS_ELK_SWITCH` | `0x28700111` | đọc/ghi |
| `INFO_ID_ADAS_FCS_ALAD_TYPE` | `0x28700106` | đọc/ghi |
| `INFO_ID_ADAS_FCS_SLIF_MISC_STS` | `0x2870010b` | đọc/ghi |
| `INFO_ID_ADAS_FCS_SLIF_SWITCH` | `0x28700109` | đọc/ghi |
| `INFO_ID_ADAS_FCS_SLIF_WARN_SWITCH` | `0x28700108` | đọc/ghi |
| `INFO_ID_ADAS_FCW_SEN_LEVEL` | `0x28700110` | đọc/ghi |
| `INFO_ID_ADAS_FRS_ALOD_CONTROL_TYPE` | `0x2870010c` | đọc/ghi |
| `INFO_ID_ADAS_IHBC` | `0x28700220` | đọc/ghi |
| `INFO_ID_ADAS_RSRS_LCA_ACTIVE_STS` | `0x28700100` | đọc/ghi |
| `INFO_ID_ADAS_RSRS_RCTA_STS` | `0x28700101` | đọc/ghi |
| `INFO_ID_ADAS_RSRS_RCW_STS` | `0x28700104` | đọc/ghi |
| `INFO_ID_AD_KEYAD` | `0x2870025f` | đọc/ghi |
| `INFO_ID_AD_KEYID` | `0x2870025e` | đọc/ghi |
| `INFO_ID_AD_TEMPVALUE` | `0x28700260` | đọc/ghi |
| `INFO_ID_AMP_STS` | `0x28700281` | đọc/ghi |
| `INFO_ID_APA_CONFIRMATION_REQUEST` | `0x2870024b` | chỉ ghi |
| `INFO_ID_APA_ENTER_EXIT` | `0x2870024a` | đọc/ghi |
| `INFO_ID_AQS` | `0x2870000f` | đọc/ghi |
| `INFO_ID_ATE_3D_SOUND` | `0x28700172` | đọc/ghi |
| `INFO_ID_ATE_4G_SETDIAGNOSE` | `0x2870014b` | chỉ ghi |
| `INFO_ID_ATE_4G_SETINTENSITY` | `0x28700149` | chỉ ghi |
| `INFO_ID_ATE_AUDIO_CHANNEL` | `0x2870016e` | đọc/ghi |
| `INFO_ID_ATE_BT_AUTOCONNECT` | `0x28700158` | đọc/ghi |
| `INFO_ID_ATE_BT_CLEARRECORD` | `0x28700174` | đọc/ghi |
| `INFO_ID_ATE_BT_ONOFF` | `0x28700151` | đọc/ghi |
| `INFO_ID_ATE_BT_SEARCHPAIR` | `0x28700157` | đọc/ghi |
| `INFO_ID_ATE_BT_SETLINKSTS` | `0x28700156` | chỉ ghi |
| `INFO_ID_ATE_BT_SETMACADDR` | `0x28700153` | chỉ ghi |
| `INFO_ID_ATE_BT_VOLUME` | `0x28700154` | đọc/ghi |
| `INFO_ID_ATE_CLEARDATA_FLASH` | `0x2870013e` | chỉ ghi |
| `INFO_ID_ATE_CheckVI` | `0x28700179` | đọc/ghi |
| `INFO_ID_ATE_CloseUsbPopup` | `0x2870017a` | đọc/ghi |
| `INFO_ID_ATE_FACTORY_MODE` | `0x2870017b` | đọc/ghi |
| `INFO_ID_ATE_FACTORY_SET` | `0x28700167` | đọc/ghi |
| `INFO_ID_ATE_GENERAL_CMD` | `0x2870013d` | đọc/ghi |
| `INFO_ID_ATE_GPS_INFO` | `0x28700159` | đọc/ghi |
| `INFO_ID_ATE_GPS_SYSCHECK` | `0x28700169` | đọc/ghi |
| `INFO_ID_ATE_GYRO_SETCHIPID` | `0x2870016b` | chỉ ghi |
| `INFO_ID_ATE_ID_GOHOME` | `0x2870016c` | đọc/ghi |
| `INFO_ID_ATE_ID_WRRDTEST` | `0x2870016d` | đọc/ghi |
| `INFO_ID_ATE_IO_READSTATE` | `0x28700175` | đọc/ghi |
| `INFO_ID_ATE_IO_SETSTATE` | `0x28700176` | đọc/ghi |
| `INFO_ID_ATE_LANGUAGE_SET` | `0x2870013f` | đọc/ghi |
| `INFO_ID_ATE_MEDIA_PLAYPAUSE` | `0x28700140` | đọc/ghi |
| `INFO_ID_ATE_MEDIA_SETSOURCE` | `0x28700150` | đọc/ghi |
| `INFO_ID_ATE_MUTE_STS` | `0x28700160` | đọc/ghi |
| `INFO_ID_ATE_NAVI_SETACTIVESTS` | `0x2870015e` | chỉ ghi |
| `INFO_ID_ATE_NAVI_SIMULATE` | `0x2870015c` | đọc/ghi |
| `INFO_ID_ATE_NAVI_UPDATE` | `0x28700165` | đọc/ghi |
| `INFO_ID_ATE_NonSignal` | `0x2870016f` | đọc/ghi |
| `INFO_ID_ATE_PLAY_TRACK` | `0x28700166` | đọc/ghi |
| `INFO_ID_ATE_PLAY_VIDEO` | `0x28700168` | đọc/ghi |
| `INFO_ID_ATE_RADIO_SEEKUPDN` | `0x2870014e` | đọc/ghi |
| `INFO_ID_ATE_RADIO_SETFREQ` | `0x2870014d` | đọc/ghi |
| `INFO_ID_ATE_SATELLITE_SETINFO` | `0x2870015b` | chỉ ghi |
| `INFO_ID_ATE_SCREEN_BRIGHT` | `0x28700171` | đọc/ghi |
| `INFO_ID_ATE_SET_EFUSE` | `0x28700245` | chỉ ghi |
| `INFO_ID_ATE_SHOW_PICTURE` | `0x28700141` | đọc/ghi |
| `INFO_ID_ATE_SOUND_BALANCE` | `0x2870015f` | đọc/ghi |
| `INFO_ID_ATE_TBOX_AUDIO` | `0x28700173` | đọc/ghi |
| `INFO_ID_ATE_TP_TEST` | `0x28700170` | đọc/ghi |
| `INFO_ID_ATE_USERMANUAL_IMPORT` | `0x28700177` | đọc/ghi |
| `INFO_ID_ATE_USERMANUAL_MD5` | `0x28700178` | đọc/ghi |
| `INFO_ID_ATE_VERSION_SETINFO` | `0x28700143` | chỉ ghi |
| `INFO_ID_ATE_VOICE_RECOGN` | `0x28700164` | đọc/ghi |
| `INFO_ID_ATE_VOLUME_DEFAULTSET` | `0x28700162` | đọc/ghi |
| `INFO_ID_ATE_VOLUME_RECORDPLAY` | `0x28700163` | đọc/ghi |
| `INFO_ID_ATE_VOLUME_VALUE` | `0x28700161` | đọc/ghi |
| `INFO_ID_ATE_WIFI_PAIR` | `0x28700147` | đọc/ghi |
| `INFO_ID_ATE_WIFI_SETINTENSITY` | `0x28700146` | chỉ ghi |
| `INFO_ID_ATE_WIFI_SWITCH` | `0x28700144` | đọc/ghi |
| `INFO_ID_ATMOSPHERE_LIGHT_SOUND_SOURCE` | `0x2870022e` | chỉ ghi |
| `INFO_ID_ATWS_JUDGE` | `0x28700247` | đọc/ghi |
| `INFO_ID_AUTO` | `0x2870000d` | đọc/ghi |
| `INFO_ID_AUTOMATIC_PARKING` | `0x287001f9` | chỉ ghi |
| `INFO_ID_AUTO_HOLD_SWITCH` | `0x2870019d` | đọc/ghi |
| `INFO_ID_AVASSwitch` | `0x28700118` | đọc/ghi |
| `INFO_ID_AVASVolume` | `0x28700119` | đọc/ghi |
| `INFO_ID_AVM_CAL` | `0x2870027b` | đọc/ghi |
| `INFO_ID_AVM_CAL_STATE` | `0x2870027c` | đọc/ghi |
| `INFO_ID_AVM_COORDINATE_DATA` | `0x287001d8` | chỉ ghi |
| `INFO_ID_AVM_FRONT` | `0x28700277` | đọc/ghi |
| `INFO_ID_AVM_LEFT` | `0x28700278` | đọc/ghi |
| `INFO_ID_AVM_REAR` | `0x2870027a` | đọc/ghi |
| `INFO_ID_AVM_RESPONSE_PDC` | `0x28700263` | chỉ ghi |
| `INFO_ID_AVM_RIGHT` | `0x28700279` | đọc/ghi |
| `INFO_ID_AVM_TRANSPARENT_UB` | `0x28700262` | đọc/ghi |
| `INFO_ID_BACKLIGHT_CIRCUIT_ERROR` | `0x2870022a` | chỉ ghi |
| `INFO_ID_BACKLIGHT_CIRCUIT_OPEN` | `0x28700229` | chỉ ghi |
| `INFO_ID_BACKLIGHT_CIRCUIT_SHORT_TO_GND` | `0x28700228` | chỉ ghi |
| `INFO_ID_BACKLIGHT_SIGNAL_LOST_WHEN_FIRST_IGN` | `0x2870022c` | chỉ ghi |
| `INFO_ID_BCM_TURN_LEVER_STATUS` | `0x287001df` | đọc/ghi |
| `INFO_ID_BLIND_CAMERA_SWITCH` | `0x287001c3` | đọc/ghi |
| `INFO_ID_BLIND_SPOT_MONITORING_SOUND_ALARM_SWITCH` | `0x287001a3` | đọc/ghi |
| `INFO_ID_BLIND_SPOT_MONITORING_SWITCH` | `0x287001a2` | đọc/ghi |
| `INFO_ID_BLOWER_LEVEL` | `0x28700005` | đọc/ghi |
| `INFO_ID_BLUETOOTH_ACTIVE_STATUS` | `0x28700221` | đọc/ghi |
| `INFO_ID_BT_AGREEMENT` | `0x287001ef` | chỉ ghi |
| `INFO_ID_CAMERATYPE` | `0x2870006d` | chỉ ghi |
| `INFO_ID_CHARGE_CONNECTOR_STS` | `0x28700113` | đọc/ghi |
| `INFO_ID_CURRTROAD_CONDITION` | `0x28700257` | chỉ ghi |
| `INFO_ID_CURRTROAD_CONGESTIONLEV` | `0x28700258` | chỉ ghi |
| `INFO_ID_CURR_CONGESTROAD_LENGTH` | `0x28700259` | chỉ ghi |
| `INFO_ID_DESTINATIONDISTANCEINDICATE` | `0x2870005b` | chỉ ghi |
| `INFO_ID_DESTINATIONTIME_DAY` | `0x2870005d` | chỉ ghi |
| `INFO_ID_DESTINATIONTIME_HOUR` | `0x2870005e` | chỉ ghi |
| `INFO_ID_DESTINATIONTIME_MINUTE` | `0x2870005f` | chỉ ghi |
| `INFO_ID_DEVICEWARN` | `0x28700138` | chỉ ghi |
| `INFO_ID_DIAG_DISPALYLIGHT_INFO` | `0x2870013b` | đọc/ghi |
| `INFO_ID_DIAG_MIX_BT` | `0x2870019b` | chỉ ghi |
| `INFO_ID_DIAG_MIX_DisplayCheckoutError` | `0x287001e9` | chỉ ghi |
| `INFO_ID_DIAG_MIX_SOCVideoError` | `0x287001e8` | chỉ ghi |
| `INFO_ID_DIAG_MIX_TBOX` | `0x2870019a` | chỉ ghi |
| `INFO_ID_DIAG_MIX_TEM` | `0x28700184` | chỉ ghi |
| `INFO_ID_DISPLAYSET_BLIND_CAMERA_SWITCH` | `0x28700186` | đọc/ghi |
| `INFO_ID_DISPLAYSOURCE_VIDEO_SOURCE` | `0x2870012f` | đọc/ghi |
| `INFO_ID_DISPLAY_CONTENT_CHECKOUT_ERROR` | `0x287001f2` | chỉ ghi |
| `INFO_ID_DISTANCE_TO_NEXT_CONGESTROAD` | `0x2870025b` | chỉ ghi |
| `INFO_ID_DMM_AmbLightBright` | `0x28700086` | đọc/ghi |
| `INFO_ID_DMM_AmbLightColor` | `0x28700087` | đọc/ghi |
| `INFO_ID_DMM_AmbLightCorrelative` | `0x28700085` | đọc/ghi |
| `INFO_ID_DMM_AmbLightSelected` | `0x28700084` | chỉ ghi |
| `INFO_ID_DMM_BackLightBright` | `0x28700089` | đọc/ghi |
| `INFO_ID_DMM_BacklightSelected` | `0x28700088` | chỉ ghi |
| `INFO_ID_DMM_Bootup` | `0x2870006f` | chỉ ghi |
| `INFO_ID_DMM_DMMRestoreFactorySettings` | `0x28700073` | đọc/ghi |
| `INFO_ID_DMM_FrFragranceSelected` | `0x2870007e` | chỉ ghi |
| `INFO_ID_DMM_FrHUDSelected` | `0x28700083` | chỉ ghi |
| `INFO_ID_DMM_FrOprCode` | `0x28700074` | đọc/ghi |
| `INFO_ID_DMM_FrSeatMirrorSelected` | `0x28700082` | chỉ ghi |
| `INFO_ID_DMM_FrUserID` | `0x28700075` | đọc/ghi |
| `INFO_ID_DMM_FragranceConcentration` | `0x28700081` | đọc/ghi |
| `INFO_ID_DMM_FragranceSpecies` | `0x28700080` | đọc/ghi |
| `INFO_ID_DMM_FragranceSwitchEnable` | `0x2870007f` | đọc/ghi |
| `INFO_ID_DMM_IPKThemeColor` | `0x2870008b` | đọc/ghi |
| `INFO_ID_DMM_IPKThemeCorrelative` | `0x2870008c` | đọc/ghi |
| `INFO_ID_DMM_IPKThemeSelected` | `0x2870008a` | chỉ ghi |
| `INFO_ID_DMM_RegisterCancel` | `0x2870007c` | chỉ ghi |
| `INFO_ID_DMM_SetFatiguedetectionSts` | `0x28700071` | đọc/ghi |
| `INFO_ID_DMM_SetUnsafeActSts` | `0x28700072` | đọc/ghi |
| `INFO_ID_DMM_SetrecognitionSts` | `0x28700070` | đọc/ghi |
| `INFO_ID_DOOR_CONTROL_LIGHT_SWITCH` | `0x287001a0` | đọc/ghi |
| `INFO_ID_DOUBLE_TEMPRETURE` | `0x28700009` | chỉ ghi |
| `INFO_ID_DOW_VOLUME_VALUE` | `0x28700264` | đọc/ghi |
| `INFO_ID_DRIVINGDIRECTION` | `0x28700061` | chỉ ghi |
| `INFO_ID_DRV_HEAT` | `0x2870000b` | đọc/ghi |
| `INFO_ID_DSP_STS` | `0x2870028c` | chỉ ghi |
| `INFO_ID_DUAL` | `0x2870000a` | đọc/ghi |
| `INFO_ID_DVR_BROWSE_INSTRUCTION` | `0x287001d0` | chỉ ghi |
| `INFO_ID_DVR_ENTRY_AND_EXIT_RESPONSE` | `0x287001d2` | đọc/ghi |
| `INFO_ID_DVR_INSTRUCTION_LIST` | `0x287001d1` | đọc/ghi |
| `INFO_ID_DVR_RECORDING` | `0x287001c9` | đọc/ghi |
| `INFO_ID_DVR_RECORDING_TIME` | `0x287001cb` | đọc/ghi |
| `INFO_ID_DVR_REQUEST_TO_ENTER_DVR` | `0x287001d6` | chỉ ghi |
| `INFO_ID_DVR_RESOLUTION` | `0x287001ca` | đọc/ghi |
| `INFO_ID_DVR_RESTORE_FACTORY_SETTING` | `0x287001cd` | đọc/ghi |
| `INFO_ID_DVR_RETURN_TO_MAIN_MENU` | `0x287001cf` | đọc/ghi |
| `INFO_ID_DVR_SDCARD_FORMATTING` | `0x287001cc` | đọc/ghi |
| `INFO_ID_DVR_SYNC_INFO` | `0x287001d9` | chỉ ghi |
| `INFO_ID_DVR_UPDATE_MODE` | `0x287001ce` | đọc/ghi |
| `INFO_ID_EVA_LEVEL` | `0x2870004d` | đọc/ghi |
| `INFO_ID_EVA_SWITCH` | `0x2870004c` | đọc/ghi |
| `INFO_ID_EXTERNAL_DISCHARGE` | `0x287001b2` | đọc/ghi |
| `INFO_ID_FAILURE_REASON` | `0x28700282` | chỉ ghi |
| `INFO_ID_FINISHREMIND` | `0x28700057` | chỉ ghi |
| `INFO_ID_FLOWMODE` | `0x28700006` | đọc/ghi |
| `INFO_ID_FOG_MODULE_ERROR` | `0x28700225` | chỉ ghi |
| `INFO_ID_FOG_MODULE_VOL_SUPPLY_ERROR` | `0x2870022d` | chỉ ghi |
| `INFO_ID_FRESHRECI` | `0x2870000e` | đọc/ghi |
| `INFO_ID_FRNTWNHTKEY` | `0x2870027d` | đọc/ghi |
| `INFO_ID_F_DEFROST` | `0x28700003` | đọc/ghi |
| `INFO_ID_GESTURE2_SWITCH_SETTING` | `0x28700200` | đọc/ghi |
| `INFO_ID_GESTURE3_SWITCH_SETTING` | `0x28700201` | đọc/ghi |
| `INFO_ID_GESTURE4_SWITCH_SETTING` | `0x28700202` | đọc/ghi |
| `INFO_ID_GPS_ELEVATION_INFO` | `0x28700191` | chỉ ghi |
| `INFO_ID_GPS_GGA_STS` | `0x28700190` | chỉ ghi |
| `INFO_ID_GPS_GSA_STS` | `0x2870018f` | chỉ ghi |
| `INFO_ID_GPS_INFO` | `0x28700192` | chỉ ghi |
| `INFO_ID_GPS_LATITUDE_FIXEDSTS` | `0x2870018c` | chỉ ghi |
| `INFO_ID_GPS_LATITUDE_INFO` | `0x2870018e` | chỉ ghi |
| `INFO_ID_GPS_LATITUDE_TYPE` | `0x2870018b` | chỉ ghi |
| `INFO_ID_GPS_LONGITUDE_FIXEDSTS` | `0x2870018a` | chỉ ghi |
| `INFO_ID_GPS_LONGITUDE_INFO` | `0x2870018d` | chỉ ghi |
| `INFO_ID_GPS_LONGITUDE_TYPE` | `0x28700189` | chỉ ghi |
| `INFO_ID_HDC_SWITCH` | `0x2870019e` | đọc/ghi |
| `INFO_ID_HIGH_TRUNNING_ENTER_AVM_FUNC_SWITCH` | `0x287001c5` | đọc/ghi |
| `INFO_ID_HUB_RESET` | `0x2870028d` | chỉ ghi |
| `INFO_ID_HUDACTIVEREQ` | `0x28700065` | đọc/ghi |
| `INFO_ID_HUDBTINFODISPREQ` | `0x28700053` | đọc/ghi |
| `INFO_ID_HUDNAVIINFODISPREQ` | `0x28700052` | đọc/ghi |
| `INFO_ID_HUDSAFETYINFODISPREQ` | `0x28700051` | đọc/ghi |
| `INFO_ID_HUDSNOWINGMODEACTREQ` | `0x28700050` | đọc/ghi |
| `INFO_ID_HUDVEHDRVGDISPREQ` | `0x28700058` | đọc/ghi |
| `INFO_ID_HVAC_BUTTON_SOUND` | `0x287001dc` | đọc/ghi |
| `INFO_ID_INTELLIGENT_AIR_CONDITIONER` | `0x28700249` | đọc/ghi |
| `INFO_ID_IN_CAR_VISUAL_AIDS` | `0x287001fa` | đọc/ghi |
| `INFO_ID_IONS` | `0x28700010` | đọc/ghi |
| `INFO_ID_IPKINFO_AVERAGE_INSTANT_FUEL` | `0x287000a9` | đọc/ghi |
| `INFO_ID_IPKSET_CMD` | `0x287000b0` | đọc/ghi |
| `INFO_ID_IPKSKIN_CORRECT_LATIVE_STATE` | `0x287000b2` | đọc/ghi |
| `INFO_ID_IPKSKIN_SKIN_MODE` | `0x287000b3` | đọc/ghi |
| `INFO_ID_IPKTIME` | `0x287000aa` | chỉ ghi |
| `INFO_ID_IPKTIME_INFO` | `0x287000ab` | đọc/ghi |
| `INFO_ID_IPK_SYNC_CURRENT_STATUS` | `0x2870004f` | chỉ ghi |
| `INFO_ID_LANE_CHANGE_AUXILIARY_SOUND` | `0x287001a1` | đọc/ghi |
| `INFO_ID_LCDDISPLAY_SCREEN_BRIGHT_LEVEL` | `0x2870012d` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_BRIGHTNESS` | `0x287000f5` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_BRIGHTNESS_3B` | `0x287001e7` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_COLOR` | `0x287000f2` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_COLOR_3B` | `0x287001e6` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_MEDIA_SOURCE` | `0x28700205` | chỉ ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_MODE` | `0x287000f4` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_MODE_3B` | `0x287001e5` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_MUSIC_SWITCH_3B` | `0x287001e3` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_SMART_SWITCH_3B` | `0x287001e4` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_SOUND_INTENSITY` | `0x2870021d` | chỉ ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_SWITCH` | `0x287000f1` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_SWITCH_3B` | `0x287001e2` | đọc/ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_VOICE_MODE` | `0x28700206` | chỉ ghi |
| `INFO_ID_LIGHTINSIDE_ATMOSPHERE_LAMP_WELCOME` | `0x287000f6` | đọc/ghi |
| `INFO_ID_LOCAL_SIGNAL_ERROR_WHEN_FIRST_IGN` | `0x2870022b` | chỉ ghi |
| `INFO_ID_L_TEMPRETURE` | `0x28700007` | đọc/ghi |
| `INFO_ID_MAPENABLESTS` | `0x28700285` | đọc/ghi |
| `INFO_ID_MAP_FAILUREREASON` | `0x28700284` | chỉ ghi |
| `INFO_ID_MCUDEVICEINFO` | `0x2870011a` | chỉ ghi |
| `INFO_ID_MCUDEVICEINFO_HW_VERSION` | `0x2870011b` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_IHU_ID_NUM` | `0x28700121` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_LOCAL_CONFIG` | `0x28700126` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_LOCAL_CONFIG_FLAG` | `0x28700127` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_MPU_SOFT_VERSION` | `0x2870011f` | chỉ ghi |
| `INFO_ID_MCUDEVICEINFO_VECH_CONFG` | `0x28700122` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_VECH_NETWORK_CONFIG` | `0x28700124` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_VECH_TYPE` | `0x28700123` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_WIFI_NETWORK_CONFIG` | `0x28700125` | đọc/ghi |
| `INFO_ID_MCUDEVICEINFO_XDSN_NUM` | `0x28700120` | đọc/ghi |
| `INFO_ID_MIC_CHANNEL_SWITCH_STATUS` | `0x28700222` | đọc/ghi |
| `INFO_ID_MMI_NAVSYNCDISPLAY` | `0x2870006c` | chỉ ghi |
| `INFO_ID_MMI_PAS_VOLUMELEVELRESPONSE` | `0x28700272` | đọc/ghi |
| `INFO_ID_MPUPOWER_AP_READY` | `0x2870012c` | chỉ ghi |
| `INFO_ID_MPUPOWER_FAST_SLEEP_CMD` | `0x2870012b` | chỉ ghi |
| `INFO_ID_MPUPOWER_POWER_STATUS` | `0x2870012a` | đọc/ghi |
| `INFO_ID_NAVCOUNTRYID` | `0x2870006b` | chỉ ghi |
| `INFO_ID_NAVDESTINATIONINDICATION` | `0x28700063` | chỉ ghi |
| `INFO_ID_NAVIGATION_MOD_INDICATOR` | `0x28700266` | chỉ ghi |
| `INFO_ID_NAVRAMPSTS` | `0x28700068` | chỉ ghi |
| `INFO_ID_NAVROADTYPE` | `0x28700069` | chỉ ghi |
| `INFO_ID_NAVSPEEDLIMIT` | `0x28700067` | chỉ ghi |
| `INFO_ID_NAVSTATUS` | `0x2870006a` | chỉ ghi |
| `INFO_ID_NAVTUNNELINDICATION` | `0x28700064` | chỉ ghi |
| `INFO_ID_NEXTCROSSDISTANCEINDICATE` | `0x28700059` | chỉ ghi |
| `INFO_ID_NEXTROAD_CONGESTIONLEV` | `0x2870025a` | chỉ ghi |
| `INFO_ID_NEXT_CONGEST_ROAD_LENGTH` | `0x2870025c` | chỉ ghi |
| `INFO_ID_NOPLUGIN_MODE` | `0x28700116` | đọc/ghi |
| `INFO_ID_OFFWORKMODESTS` | `0x28700287` | chỉ ghi |
| `INFO_ID_PARKING_AUXILIARY_SWITCH` | `0x2870019f` | chỉ ghi |
| `INFO_ID_PAS_HEAT` | `0x2870000c` | đọc/ghi |
| `INFO_ID_PDC_UART_REQUEST` | `0x287001c1` | đọc/ghi |
| `INFO_ID_PDC_VOLUME_REQUEST` | `0x287001c2` | đọc/ghi |
| `INFO_ID_QDRIVE_ENABLE_STS` | `0x2870027f` | đọc/ghi |
| `INFO_ID_REROUTINGREMIND` | `0x28700056` | chỉ ghi |
| `INFO_ID_RESTORE_FACTORY_STS` | `0x28700196` | chỉ ghi |
| `INFO_ID_REVERSE_LINE_SWITCH` | `0x287001c4` | đọc/ghi |
| `INFO_ID_ROADCAMERADISTANCEINDICATION` | `0x2870005c` | chỉ ghi |
| `INFO_ID_RVC_VIEW_DISPLAY_SWITCH` | `0x287001c6` | đọc/ghi |
| `INFO_ID_R_DEFROST` | `0x28700004` | đọc/ghi |
| `INFO_ID_R_TEMPRETURE` | `0x28700008` | đọc/ghi |
| `INFO_ID_SAVE_Mode` | `0x28700117` | đọc/ghi |
| `INFO_ID_SECOND_ROW_AC_BUTTON` | `0x287001a5` | đọc/ghi |
| `INFO_ID_SERVICEAREADISTANCE` | `0x2870005a` | chỉ ghi |
| `INFO_ID_SERVICEAREATYPE` | `0x28700060` | chỉ ghi |
| `INFO_ID_SKYLIGHT_SUNSHADE_SWITCH` | `0x287001b4` | chỉ ghi |
| `INFO_ID_SOC_BALANCE_POINT_SETTING` | `0x287001a4` | đọc/ghi |
| `INFO_ID_SOC_VIDEO_SIGNAL_ERROR` | `0x287001f1` | chỉ ghi |
| `INFO_ID_SYNC_DAY_NIGHT_MODE_TO_IPK` | `0x28700208` | chỉ ghi |
| `INFO_ID_SYSTEM_MCU_UPDATE` | `0x2870026a` | đọc/ghi |
| `INFO_ID_TAXIING_FEEDBACK_LEVEL` | `0x287001b1` | đọc/ghi |
| `INFO_ID_TBOXCFG_ICCID_VALUE` | `0x28700133` | chỉ ghi |
| `INFO_ID_TBOXCFG_IMEI_NUM` | `0x28700135` | chỉ ghi |
| `INFO_ID_TBOXCFG_IMSI_NUM` | `0x28700134` | chỉ ghi |
| `INFO_ID_TBOXCFG_TBOX_NUM` | `0x28700136` | chỉ ghi |
| `INFO_ID_TBOX_AGREEMENT` | `0x287001ee` | chỉ ghi |
| `INFO_ID_TEMPERATURE_STATE` | `0x287001f0` | chỉ ghi |
| `INFO_ID_THEME_LINK_SWITCH_STATUS` | `0x28700261` | đọc/ghi |
| `INFO_ID_TOLLGATEINDICATION` | `0x28700062` | chỉ ghi |
| `INFO_ID_TOUCHPANEL_CIRCUIT_SHORT_TO_GND` | `0x28700226` | chỉ ghi |
| `INFO_ID_TOUCHPANEL_MODULE_ERROR` | `0x28700227` | chỉ ghi |
| `INFO_ID_TOUCH_PAD_KEY_SOUND` | `0x287001e0` | đọc/ghi |
| `INFO_ID_TUNNEL_OPENING_INTERNAL_CIRCULATION` | `0x287001b5` | chỉ ghi |
| `INFO_ID_TURNINGDIRECTIONTYPE` | `0x28700054` | chỉ ghi |
| `INFO_ID_TURNINGDISTANCE` | `0x28700055` | chỉ ghi |
| `INFO_ID_TYPEWRITINGENABLESTS` | `0x28700283` | đọc/ghi |
| `INFO_ID_USBMODE` | `0x28700268` | chỉ ghi |
| `INFO_ID_USBOVERCURRENT` | `0x2870026b` | đọc/ghi |
| `INFO_ID_USBSET_CMD_TYPE` | `0x28700131` | đọc/ghi |
| `INFO_ID_USBSTATUS` | `0x28700269` | chỉ ghi |
| `INFO_ID_USBTYPE` | `0x28700267` | đọc/ghi |
| `INFO_ID_USB_HUB_UPGRADE` | `0x2870013a` | đọc/ghi |
| `INFO_ID_USB_HUB_UPGRADE_INFO` | `0x28700139` | đọc/ghi |
| `INFO_ID_VDRIVEINFO_DRIVER_MODE` | `0x287000ea` | đọc/ghi |
| `INFO_ID_VDRIVEINFO_EMS_SSM_SWITCH` | `0x287000e9` | đọc/ghi |
| `INFO_ID_VEHICLECONTROL_SWCUSERIGHT` | `0x287000fb` | đọc/ghi |
| `INFO_ID_VEHICLE_MODE_REQ` | `0x28700265` | đọc/ghi |
| `INFO_ID_VEHIC_ADAPTIVE_FLIGHT_SYSTEM` | `0x2870004e` | đọc/ghi |
| `INFO_ID_VEHIC_ANY_DOOR_LOCK_CAR_WARNING` | `0x28700038` | đọc/ghi |
| `INFO_ID_VEHIC_AUTONOMOUS_EMERGENCY_BRAKING` | `0x2870010e` | đọc/ghi |
| `INFO_ID_VEHIC_AUTO_CLOSE_WINDOW` | `0x28700032` | đọc/ghi |
| `INFO_ID_VEHIC_AUTO_LOCK_WHEN_LEAVE_DOOR` | `0x28700036` | đọc/ghi |
| `INFO_ID_VEHIC_AUTO_UNLOCK_WHEN_NEAR_BACK_DOOR` | `0x28700034` | đọc/ghi |
| `INFO_ID_VEHIC_AUTO_UNLOCK_WHEN_NEAR_DOOR` | `0x28700035` | đọc/ghi |
| `INFO_ID_VEHIC_BACK_DOOR_AUTO_UNLOCK_DISTANCE` | `0x2870003a` | đọc/ghi |
| `INFO_ID_VEHIC_CORNREING_MODE` | `0x2870003d` | đọc/ghi |
| `INFO_ID_VEHIC_DAY_RUNNING_LAMP` | `0x28700030` | đọc/ghi |
| `INFO_ID_VEHIC_ESP` | `0x28700033` | đọc/ghi |
| `INFO_ID_VEHIC_FOLLOWME_HOME_CLOSEFUNC` | `0x28700041` | đọc/ghi |
| `INFO_ID_VEHIC_FOLLOWME_HOME_TIMESELECT` | `0x2870003b` | đọc/ghi |
| `INFO_ID_VEHIC_FORWARD_COLLISION_WARNING` | `0x2870010f` | đọc/ghi |
| `INFO_ID_VEHIC_IGNOFFUNLOCKDOOR` | `0x2870004a` | đọc/ghi |
| `INFO_ID_VEHIC_IGNOFFUNLOCKDOORSELECT` | `0x28700042` | đọc/ghi |
| `INFO_ID_VEHIC_INTELLIGENT_HIGH_BEAM_CONTROL` | `0x28700185` | đọc/ghi |
| `INFO_ID_VEHIC_KEYINREMINDER_OPTION` | `0x2870003f` | đọc/ghi |
| `INFO_ID_VEHIC_LOCK_BATTORY_ON` | `0x2870003e` | đọc/ghi |
| `INFO_ID_VEHIC_MIRROR_AUTO_FOLDING` | `0x2870002e` | đọc/ghi |
| `INFO_ID_VEHIC_MMI_EASYACCESSREQ` | `0x28700044` | đọc/ghi |
| `INFO_ID_VEHIC_MMRYSCSINDREQ` | `0x28700045` | đọc/ghi |
| `INFO_ID_VEHIC_MUSIC_RHYTHM` | `0x287001c8` | đọc/ghi |
| `INFO_ID_VEHIC_REARMIRROR_TILT` | `0x2870003c` | đọc/ghi |
| `INFO_ID_VEHIC_REARWIPERAUTOACTIVEATREVERSER` | `0x28700040` | đọc/ghi |
| `INFO_ID_VEHIC_RKE_LOCK_FEEDBACK` | `0x2870002f` | đọc/ghi |
| `INFO_ID_VEHIC_RLS` | `0x2870002d` | đọc/ghi |
| `INFO_ID_VEHIC_SAILINGSWITCHSTS` | `0x28700043` | đọc/ghi |
| `INFO_ID_VEHIC_SPEED_LOCK` | `0x28700187` | đọc/ghi |
| `INFO_ID_VEHIC_TRUNKACTIONREQ` | `0x28700049` | chỉ ghi |
| `INFO_ID_VEHIC_TRUNKUPBASICPOSITIONRESP` | `0x28700046` | đọc/ghi |
| `INFO_ID_VEHIC_WELCOME_LIGHT` | `0x28700037` | đọc/ghi |
| `INFO_ID_VEHIC_WINDOW_CLAMP_WARNING` | `0x28700039` | đọc/ghi |
| `INFO_ID_VEHIC_WINDOW_VENTILATE` | `0x28700031` | đọc/ghi |
| `INFO_ID_VIMS_ENTER_REQUEST` | `0x28700232` | đọc/ghi |
| `INFO_ID_VIMS_TOUCH_INFO` | `0x2870023c` | chỉ ghi |
| `INFO_ID_VIMS_VOICE_CONTROL` | `0x28700233` | chỉ ghi |
| `INFO_ID_VIN_INFO` | `0x2870008e` | đọc/ghi |
| `INFO_ID_VOICESET_MUTE_CMD` | `0x28700130` | đọc/ghi |
| `INFO_ID_VOL_HIGH` | `0x28700223` | chỉ ghi |
| `INFO_ID_VOL_LOW` | `0x28700224` | chỉ ghi |
| `INFO_ID_VSTEERWHEELINFO` | `0x287000ee` | chỉ ghi |
| `INFO_ID_WALLPAPER_STYLE` | `0x28700256` | chỉ ghi |
| `INFO_ID_WASH_CAR` | `0x28700246` | đọc/ghi |
| `INFO_ID_WELCOMELIGHT_MODE` | `0x28700273` | đọc/ghi |
| `INFO_ID_WINDOW_FL` | `0x2870009d` | đọc/ghi |
| `INFO_ID_WINDOW_FR` | `0x2870009e` | đọc/ghi |
| `INFO_ID_WINDOW_GROUP` | `0x287000a2` | chỉ ghi |
| `INFO_ID_WINDOW_RL` | `0x2870009f` | đọc/ghi |
| `INFO_ID_WINDOW_ROLLSHADE` | `0x2870009c` | đọc/ghi |
| `INFO_ID_WINDOW_RR` | `0x287000a0` | đọc/ghi |
| `INFO_ID_WINDOW_SUNROOF` | `0x2870009b` | đọc/ghi |
| `INFO_ID_WINDOW_SUNROOFTILE` | `0x287000a1` | đọc/ghi |
| `INFO_ID_WIRELESS_WIRELESS_CMD` | `0x28700132` | đọc/ghi |
| `INFO_SETTING_FUNC_FRONT_WIPER_IDLE` | `0x287001ec` | đọc/ghi |
| `INFO_SETTING_FUNC_LANE_KEEPING_AID_WARNING` | `0x287001eb` | đọc/ghi |
| `INFO_SETTING_FUNC_TRAFFIC_SIGN_RECOGNITION_ALERT` | `0x287001ea` | đọc/ghi |
| `INFO_SET_PHOTO_SOUND_AND_VOICE_CONTROL_CAMERA` | `0x28700203` | chỉ ghi |
| `INFO_VIMS_GESTURE_MUSIC_COLLECT` | `0x28700236` | đọc/ghi |
| `INFO_VIMS_GESTURE_MUTE` | `0x28700234` | đọc/ghi |
| `INFO_VIMS_GESTURE_RACE` | `0x28700235` | đọc/ghi |
| `OBD2_FREEZE_FRAME_CLEAR` | `0x11e00d03` | chỉ ghi |
| `VEHICLE_MAP_SERVICE` | `0x11e00c00` | đọc/ghi |
