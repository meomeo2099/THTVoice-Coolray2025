# REF-SYSUI — nguồn tham chiếu từ thành phần hệ thống của head unit (16/10/2026)

**REF-SYSUI** = thanh hệ thống / plugin SystemUI (`com.flyme.auto.systemuiplugin`) và launcher
(`com.flyme.auto.launcher`) của nền tảng ECARX trên Coolray. Phân tích tĩnh (đọc dex), chủ repo yêu cầu
lưu ngày 16/10/2026. Các đầu Coolray dùng chung một bộ ID, nên không tách theo mã head unit.

Không chép mã, asset hay chuỗi giao diện. Chỉ lấy số hiệu thuộc tính, function ID, area, giá trị và
tên API hệ thống. Mọi giá trị ở trạng thái **REFERENCE** (chưa thử trên xe thật).

**Chưa dùng trong code.** Muốn dùng một mục: chuyển vào `docs/COOLRAY_IHU624G_PROPERTIES.md` và
`car/CoolrayProperties.kt` theo quy trình trong file đó, kèm test.

Mức tin cậy: **[A]** = mã hệ thống tự gắn nhãn tên ↔ ID (bảng ánh xạ trong mã), **[B]** = suy ra từ cách dùng.

Thông tin xe chỉ đọc (xăng, ODO, chuyến đi, nước làm mát, ắc quy, lốp, bảo dưỡng) đã có ở
`07_RefWidget/README.md`, kèm công thức quy đổi; không lặp lại ở đây.

---

## 1. Function ID ECARX phải phân giải, không đọc/ghi thẳng

Các số dạng `0x10xxxxxx` / `0x20xxxxxx` là **function ID trừu tượng**, không phải property VHAL.
Mã hệ thống phân giải chúng lúc chạy rồi mới gọi `CarPropertyManager`:

```
IWrapper w = com.ecarx.xui.adaptapi.car.Car.createWrapper(Context)
IWrapper.IPropertyId p = w.getWrappedPropertyId(int funcId, int zone)   // null => không hỗ trợ
int realPropId   = p.getPropertyId()
Integer carValue = p.getPropertyValue(int abstractValue)    // khi GHI
Integer absValue = p.getPropertyAdaptValue(int carValue)    // khi ĐỌC
CarPropertyManager.get*/set*(realPropId, zone, ...)
```

Kiểm tra hỗ trợ trước khi dùng (theo mã hệ thống):
1. `funcId == 0` → không hỗ trợ.
2. `getCarPropertyConfig(realPropId) == null` → không hỗ trợ.
3. `getProperty(realPropId, zone)` → kiểm tra `getAreaId() == zone` và `getStatus()`.
4. Trước khi ghi: đọc giá trị hiện tại, bằng giá trị cần ghi thì bỏ qua.

> **Lưu ý cho code hiện tại:** `HVAC_FRONT_DEFROST_STATE = 268698880` (`0x10040500`, area 117) trong
> `CoolrayProperties.kt` có dạng function ID ECARX. Nếu đọc thẳng bằng `CarPropertyManager` không ra giá trị,
> cần phân giải qua `getWrappedPropertyId(0x10040500, 117)` như trên.

Quyền: ghi HVAC cần `android.car.permission.CONTROL_CAR_CLIMATE`; quạt có thêm
`com.flyme.auto.hvac.permission.FAN_SPEED_CONTROL`.

---

## 2. Điều hòa — property chuẩn AAOS [A]

Area lấy từ `getAreaId()` của từng widget điều hòa hệ thống.

| ID | Chức năng | Kiểu | Area | Giá trị |
|---|---|---|---|---|
| `354419984` (`0x15200510`) | Công tắc tổng | bool | 5 | |
| `354419973` (`0x15200505`) | A/C | bool | | |
| `354419978` (`0x1520050a`) | Auto | bool | 1 | |
| `358614275` (`0x15600503`) | Đặt nhiệt độ | float | 1 (lái) | có mức LO / HI ở hai đầu |
| `358614274` (`0x15600502`) | Nhiệt độ hiện tại | float | | |
| `356517120` (`0x15400500`) | Tốc độ quạt | int | 5 | 1..8 |
| `356517121` (`0x15400501`) | Hướng gió | int | | |
| `354419976` (`0x15200508`) | Lấy gió trong / ngoài | bool | 117 | |
| `354419975` (`0x15200507`) | Sấy kính trước (max defrost) | bool | 117 | |
| `354419988` (`0x15200514`) | Sấy kính sau | bool | 2 | |
| `354419974` (`0x15200506`) | Làm lạnh cực nhanh (max A/C) | bool | 117 | |
| `354419977` (`0x15200509`) | Đồng bộ nhiệt độ hai bên | bool | | |
| `356517131` (`0x1540050b`) | Sưởi ghế | int | 1 lái / 4 phụ | 0..3 |
| `356517139` (`0x15400513`) | Thông gió ghế | int | 1 lái / 4 phụ | 0..3 |
| `289408269` (`0x1140050d`) | Sưởi vô lăng (mức) | int | 0 | 0..3 |
| `289408270` (`0x1140050e`) | Đơn vị nhiệt độ | int | | |

Area: `1` = hàng 1 trái (lái), `4` = hàng 1 phải (phụ), `5` = hàng trước (1|4), `2` = kính sau,
`117` (`0x75`) = tất cả ghế, `0` = toàn xe.

---

## 3. Điều hòa / ghế — ECARX function ID [A]

Phân giải qua mục 1.

| Function ID | Chức năng | Zone | Giá trị / ghi chú |
|---|---|---|---|
| `0x10080100` | ECO | 117 | bool |
| `0x10141100` | Sưởi nhanh | 117 | bool |
| `0x10030100` | Chế độ tuần hoàn gió | 0 | int |
| `0x10080500` | Ion / lọc không khí | 1 | bool |
| `0x10080501` | Khử mùi tự động | 0 | bool |
| `0x100b0300` | Nước hoa (mức) | 0 | 0..3 |
| `0x10080200` | Chất lượng không khí (IAQC) | | |
| `0x100c0100` / `0x100c0200` / `0x100c0300` | Lọc không khí thông minh: cài đặt / yêu cầu / xác nhận | | |
| `0x10050700` | Massage ghế (mức) | 1 / 4 | `0x10050701`, `0x10050702`, `0x10050703` |
| `0x10050a00` | Công tắc massage | 1 / 4 | |
| `0x10050b00` | Chương trình massage | | |
| `0x10050500` / `0x10141700` | Sưởi ghế tự động | | |
| `0x10050600` | Thời gian sưởi ghế tự động | | |
| `0x10050300` | Thông gió ghế tự động | | |
| `0x10050400` | Thời gian thông gió ghế tự động | | |
| `0x10090400` | Tự động sưởi vô lăng | | |
| `0x10141900` | Biểu tượng sưởi vô lăng tự động | | |
| `0x10070200` | Chế độ quét gió | | |
| `0x10070300` | Chế độ hướng gió | | thủ công `0x10070304`, tập trung `0x10070301`, thích ứng `0x10070306`, né người `0x10070302`, quét `0x10070305` |
| `0x10070400` / `0x10070500` | Vị trí cánh gió vô cấp X / Y | | |
| `0x10140100` | Cửa gió điện tử | | |
| `0x10020200` | Quạt ở chế độ AUTO | | |
| `0x10142200` | Trạng thái quạt | | |
| `0x100d0300` | Tự sấy khô sau khi khóa xe | | |
| `0x10143200` | Nhắc khi đỗ xe lúc nhiệt độ thấp | | |
| `0x100f0100` / `0x100f0200` / `0x100f0300` | Nhắc đóng cửa sổ: cài đặt / yêu cầu / xác nhận | | |
| `0x20308c00` | Chế độ đỗ giữ điều hòa (hệ thống kiểm tra trước khi bật max A/C) | 117 | |

---

## 4. Function ID hệ thống khác [A]

| Function ID | Ý nghĩa |
|---|---|
| `0x200f0800` | Định dạng giờ trên thanh trạng thái |
| `0x200f0c00` | Chế độ trình diễn khi đánh thức xe |
| `0x20309900` | Chế độ kéo rơ-moóc (biểu tượng trạng thái) |
| `0x20290d00` | Giao diện theo thời gian trong ngày (`0x20290d01` … `0x20290d09`) |
| `0x2030d600` | Cấu hình phím tắt thanh hệ thống |

---

## 5. Trạng thái xe bổ sung (chỉ đọc)

| ID | Kiểu | Ý nghĩa | Tin cậy |
|---|---|---|---|
| `286261504` (`0x11100100`) | string | VIN (`INFO_VIN`) | A |
| `289408001` (`0x11400401`) | int | Số hiện tại: 1 N, 2 R, 4 P, 8 D, 16 số tiến | A |
| `291504647` (`0x11600207`) | float | Tốc độ | A |
| `557875214` (`0x2140800e`) | int | Động cơ: `0x201604` đang chạy, `0x201601` tắt | B |
| `557850837` (`0x214020d5`) | int | Chế độ lái (Adaptive, Custom, Off-road, Normal, Snow, Sport, Comfort, Eco). Giá trị số từng chế độ chưa xác định | B |

---

## 6. Dịch vụ HVAC hệ thống (AIDL)

```
Intent("com.flyme.auto.HVAC_SERVICE").setPackage("com.flyme.auto.hvac") -> bindService
interface com.flyme.auto.hvac.HvacApiService
  get/set{Int,Float,Boolean}Property(...)
  get/set{Int,Float,Boolean}FlymeProperty(String key, int area[, value])   // key ví dụ "IHvac.HVAC_FUNC_ECO_SWITCH"
  registerApiCallback / registerFlymeApiCallback
```

Không bắt buộc: các widget hệ thống cũng gọi thẳng `CarPropertyManager` (mục 2–3).
Mở màn hình điều hòa: package `com.flyme.auto.hvac`, extra `extra_start_on`; các action
`com.flyme.auto.hvac.action.ACTION_CLIMATE_HOME`, `ACTION_SEAT_SET`, `ACTION_FRAGRANCE`,
`ACTION_AUTO_DEODORIZE`, `ACTION_WINDOW_CLOSE_TIP`.

---

## 7. Media — ECARX MediaCenter

Service: package `ecarx.xsf.mediacenter`, `ecarx.xsf.mediacenter.MediaCenterService`.

```
MediaCenterAPI api = com.ecarx.eas.sdk.mediacenter.MediaCenterAPI.get(Context)
IMediaControllerAPI c = api.getMediaControllerApi()
token = c.register(String pkg, MediaController cb); c.requestControl(token)
c.playCtrlPlay(token) / c.playCtrlPause(token)
c.playCtrlPlayByContent(token, type, String) / c.playCtrlPlayByMediaID(token, type, id)
c.getMusicPlaybackInfo(token) / c.getSourceType(token) / c.getMediaList(token)
```

Thanh hệ thống dùng `EasMediaController.getTransportControls().transportControl(type, 0)`:
`0` = tạm dừng, `1` = phát. `setMediaSource(pkg, sourceType)` chọn nguồn (ví dụ `com.flyme.auto.music`, type 6).
Mã next / previous chưa xác định.

SDK còn có `getVrMusicApi()`, `getVrLocalRadioApi()`, `getVrNewsApi()` (`declare*CtrlCapability`,
`declareVrCtrlPriority`), là cách app khai báo khả năng cho trợ lý giọng nói gốc.
`MediaCtrlAction` = `OPEN`, `MOVE_BACK`, `PLAY_BACK`, `EXIT`.

---

## 8. Dẫn đường — ECARX Navi

Service `ecarx.naviservice`, action `ecarx.naviservice.intent.action.EAS_SUPPORT_SERVICE`.
Hàm: `launchMap`, `backMap`, `searchByKeyword(SearchByKeyword, cb)`, `routePlanOrNavi(NaviRoutePlan, cb)`,
`getRoutePlanStrategy`, `invokeAPISync/Async(NaviBaseModel)`.
Hằng số hành động: `ACTION_NAVI`, `ACTION_NAVI_NOCONFIRM`, `ACTION_ROUTE_PLAN`, `ACTION_ROUTE_PLAN_NOCONFIRM`,
`ACTION_SHOW_IN_MAP`, `ACTION_ZOOM_IN`, `ACTION_ZOOM_OUT`.
App bản đồ: `com.ecarx.navi` (`com.ecarx.navi.MainActivity`), `com.autonavi.minimap`.

---

## 9. Cảnh / chế độ không gian

Mở bằng `startActivity(Intent(action).addFlags(FLAG_ACTIVITY_NEW_TASK))`, không phải broadcast.

| Action | Cảnh |
|---|---|
| `android.intent.action.START_REST` | Nghỉ ngơi |
| `android.intent.action.START_CAMPING` | Cắm trại |
| `android.intent.action.START_WASH` | Rửa xe |
| `android.intent.action.START_KTV` | Karaoke |
| `android.intent.action.START_GODDESS` | Chưa rõ chức năng |

---

## 10. Launcher / hệ thống

| Mục | Chi tiết |
|---|---|
| Mở app chiếu/liên kết | broadcast `com.flyme.auto.launcher.flow.action.OPEN_APP`, extra `packageName`. Chỉ có tác dụng với app trong danh sách launcher-flow, không mở app thường |
| Đóng app chiếu | broadcast `com.flyme.auto.launcher.flow.action.CLOSE_APP`, extra `packageName` |
| Ẩn / hiện widget trợ lý | broadcast `com.flyme.auto.launcher.action.TOGGLE_AICY_WIDGET`, extra `is_hide` (bool) |
| Bảng điều khiển nhanh | bind `com.geely.controlcenter.action.ControlCenterService` (package `com.geely.controlcenter`): `expandPanel`, `collapsePanel`, `isPanelExpanded` |
| Tắt / bật head unit | `android.intent.action.ACTION_SHUTDOWN_HU` / `ACTION_BOOT_HU` (hệ thống phát) |
| Đóng app nền | `ActivityManager.forceStopPackage` (quyền hệ thống). Không đóng: `com.android.systemui`, `com.android.settings`, `com.android.packageinstaller`, `com.android.car`, launcher, `com.flyme.auto.coreservice`, `ecarx.power.service` |
| Trợ lý giọng nói gốc | `com.ecarx.vrservice`, `com.flyme.auto.voiceinteract`, TTS `com.ecarx.xcttsservice` — tránh tranh micro / audio focus |

Package app: nhạc `com.flyme.auto.music`, `com.netease.cloudmusic`, `cn.kuwo.kwmusichd`; nhạc USB
`com.flyme.auto.localmusic.usb`; điện thoại `com.android.dialer`; camera 360 `cn.carlinx.avm`, `com.ts.avm.carcamera`;
thời tiết `com.flyme.auto.weather`; cài đặt `com.flyme.auto.settings`; cửa hàng `com.flyme.auto.appstore`;
CarLink / HiCar `com.geely.carlink`, `com.geely.hicar`.

---

## 11. Cần thử trên xe

1. Dump `getPropertyList()` (ID, area, kiểu, min/max) một lần.
2. Gọi `getWrappedPropertyId` cho các function ID ở mục 3 với zone 1 và 4.
3. Kiểm tra đọc `0x10040500` trực tiếp so với qua `IWrapper` (lưu ý mục 1).
4. Khoảng nhiệt độ thực từ `getCarPropertyConfig(358614275)` (repo đang dùng 15,5..28,5 °C từ REF-VOICE).
5. Mã next / previous của `transportControl`; giá trị số của chế độ lái `0x214020d5`.
6. App có được cấp `CONTROL_CAR_CLIMATE` hay không.
