# Coolray 2025: nhóm lệnh Lunaris và thực thi trong THT Voice 1.0.9

Nguồn: APK LunarisApp 2026.09.18, SHA256 `37c972d5206fa42525b5248d7ac3e0dadc6210007167c4656efebb3696b80992`; framework gốc `AdapterAPI.jar`/`AdapterAPIImpl` của IHU730P; cấu hình `car_service` lấy từ xe. Không cần cài Lunaris để gọi các API gốc trong bản THT này. Các route mới giữ nhãn REFERENCE/UNVERIFIED cho tới khi có phản hồi xe thật.

## Các không gian ID

| Loại | Ví dụ | Cách truy cập |
| --- | --- | --- |
| VHAL chuẩn | HVAC_TEMP_SET `0x15600503`, speed `0x11600207` | Android CarPropertyManager với kiểu/area từ cấu hình xe |
| ECARX function | nhiệt độ `0x10060100`, chế độ lái `0x22010100` | ICarFunction, không gửi ID này như property VHAL |
| Vendor MCU BYTES | nhóm kính `0x287000A2`, xăng `0x28700207` | CarImpl send/get bytes; giữ payload/kiểu dữ liệu OEM |
| Sensor AdaptAPI | ODO `0x00100700`, quãng đường còn lại `0x00100800` | ISensor.getSensorLatestValue, chỉ đọc |
| Khóa tích hợp Lunaris | `ru.mark99.prop.*` | Settings.Global do Lunaris cập nhật; không phải mã VHAL trên xe |

Thứ tự bắt buộc: tạo CarImpl → connect → chờ sẵn sàng → lấy ICarFunction/ISensor → kiểm tra hỗ trợ → gửi typed command → đọc lại → phản hồi.

## Nhóm lệnh đưa vào THT

| Nhóm | Ví dụ tiếng Việt | Cơ chế |
| --- | --- | --- |
| Điều hòa cơ bản | bật/tắt điều hòa; 22,5 độ; tăng nhiệt độ; quạt mức 3 | HVAC OEM facade, 16..32 °C bước 0,5, quạt 1..7 |
| AUTO / ECO HVAC | bật điều hòa tự động; bật điều hòa tiết kiệm | function AUTO/ECO toàn xe, đọc lại sau khi đặt |
| Quạt AUTO | chỉnh quạt tự động sang êm/bình thường/mạnh | AUTO_FAN_SETTING +1/+2/+3, khác mức quạt thủ công |
| Kính cửa | hạ kính lái 50 phần trăm; đóng tất cả kính | MCU 8 byte; một byte cho mỗi kính; -1 giữ nguyên |
| Cửa sổ trời/rèm | mở cửa sổ trời 40 phần trăm; đóng rèm; nghiêng cửa sổ trời | byte4/6; tilt function riêng, 0/1; đóng vị trí0 cũng xử lý trạng thái nghiêng đang mở |
| Chế độ lái | chế độ lái eco/comfort/sport | function +1/+2/+3; tách khỏi phanh tái sinh bị vô hiệu hóa |
| Đèn ban ngày | bật/tắt đèn ban ngày | function 0x20040800, 0/1 |
| Start/Stop | bật/tắt start stop | function 0x20020100, không phải lệnh khởi động/tắt động cơ trực tiếp |
| Auto Hold | bật/tắt auto hold | function 0x20060400, 0/1 |
| ESC Sport | bật/tắt esc sport | chức năng OEM cụ thể 0x20020300, không suy diễn thành tắt toàn bộ ESC |
| Đọc công tắc | auto hold có bật không; kiểm tra đèn ban ngày | chỉ đọc trạng thái, không ghi |
| Trạng thái xe | xăng còn bao nhiêu; quãng đường còn lại; ODO; điện áp ắc quy; tình trạng xe | ưu tiên nguồn chuẩn có dữ liệu hợp lệ, bổ sung ISensor/MCU đúng loại |
| Đèn khác, sấy kính, thông gió ghế | dùng nhóm lệnh có sẵn | giữ route hiện có, kiểm tra khả dụng trên xe; không dùng heating ngoài trang bị |

Công tắc mới DRL/StartStop/AutoHold/ESC Sport yêu cầu xe đỗ/P qua SafetyPolicy. Chế độ lái và cửa sổ trời tiếp tục theo chính sách xác nhận khi di chuyển. Mọi công tắc mới phải được API báo hỗ trợ; unknown/false không được phép ghi. Các câu có phủ định hoặc nêu nhiều chế độ lái không được chọn một mode để thực thi.

## Danh mục 47 action công bố trong APK

Đây là inventory tham khảo, không phải khẳng định cả 47 action đều khả dụng trên xe người dùng. Toggle trong Lunaris tương đương đọc trạng thái rồi chọn giá trị đối lập; THT dùng câu bật/tắt rõ ràng để tránh đảo trạng thái khi nhận dạng lặp.

| Nhóm | Action Lunaris | ID | Tham số / backend | THT |
| --- | --- | --- | --- | --- |
| Đèn ban ngày | `ru.mark99.carapp.func.drl.enable` | 0x20040800 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Đèn ban ngày | `ru.mark99.carapp.func.drl.disable` | 0x20040800 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Đèn ban ngày | `ru.mark99.carapp.func.drl.toggle` | 0x20040800 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Sưởi vô lăng | `ru.mark99.carapp.func.wheelheat.enable` | 0x10090100 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi vô lăng | `ru.mark99.carapp.func.wheelheat.disable` | 0x10090100 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi vô lăng | `ru.mark99.carapp.func.wheelheat.toggle` | 0x10090100 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi vòi phun | `ru.mark99.carapp.func.nozzleheat.enable` | 0x10140400 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi vòi phun | `ru.mark99.carapp.func.nozzleheat.disable` | 0x10140400 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi vòi phun | `ru.mark99.carapp.func.nozzleheat.toggle` | 0x10140400 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi kính trước | `ru.mark99.carapp.func.windshieldheat.enable` | 0x10140500 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi kính trước | `ru.mark99.carapp.func.windshieldheat.disable` | 0x10140500 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sưởi kính trước | `ru.mark99.carapp.func.windshieldheat.toggle` | 0x10140500 | không; ICarFunction.setFunctionValue(id,value) | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Sấy kính sau | `ru.mark99.carapp.func.defrostrear.enable` | 0x10040300 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Sấy kính sau | `ru.mark99.carapp.func.defrostrear.disable` | 0x10040300 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Sấy kính sau | `ru.mark99.carapp.func.defrostrear.toggle` | 0x10040300 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Khử mờ kính trước | `ru.mark99.carapp.func.defrostfront.enable` | 0x10040100 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Khử mờ kính trước | `ru.mark99.carapp.func.defrostfront.disable` | 0x10040100 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Khử mờ kính trước | `ru.mark99.carapp.func.defrostfront.toggle` | 0x10040100 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| ESC Sport | `ru.mark99.carapp.func.escsport.enable` | 0x20020300 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| ESC Sport | `ru.mark99.carapp.func.escsport.disable` | 0x20020300 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| ESC Sport | `ru.mark99.carapp.func.escsport.toggle` | 0x20020300 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Start/Stop | `ru.mark99.carapp.func.startstop.enable` | 0x20020100 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Start/Stop | `ru.mark99.carapp.func.startstop.disable` | 0x20020100 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Start/Stop | `ru.mark99.carapp.func.startstop.toggle` | 0x20020100 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Auto Hold | `ru.mark99.carapp.func.autohold.enable` | 0x20060400 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Auto Hold | `ru.mark99.carapp.func.autohold.disable` | 0x20060400 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Auto Hold | `ru.mark99.carapp.func.autohold.toggle` | 0x20060400 | không; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| HVAC | `ru.mark99.carapp.func.climat.state` | 0x10010100 | enabled:int; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| A/C | `ru.mark99.carapp.func.climat.ac` | 0x10010300 | enabled:int; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| AUTO | `ru.mark99.carapp.func.climat.auto` | 0x10010200 | enabled:int; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| A/C MAX | `ru.mark99.carapp.func.climat.ac_max` | 0x10010400 | enabled:int; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| ECO điều hòa | `ru.mark99.carapp.func.climat.eco` | 0x10080100 | enabled:int; ICarFunction.setFunctionValue(id,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Nhiệt độ | `ru.mark99.carapp.func.climat.temp` | 0x10060100 | value:float; setCustomizeFunctionValue(id,1,value) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Quạt | `ru.mark99.carapp.func.climat.fan_speed` | 0x10020100 | value:int; setFunctionValue(id,mAirVolumes[value]) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Hướng gió | `ru.mark99.carapp.func.climat.flow_mode` | 0x10070100 | value:int; setFunctionValue(id,mFlowModes[value]) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Tuần hoàn | `ru.mark99.carapp.func.climat.cycle` | 0x10030100 | enabled:int; setFunctionValue(id,enum) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Cường độ AUTO | `ru.mark99.carapp.func.climat.rating` | 0x10020200 | value:String; setFunctionValue(id,enum) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Thông gió ghế | `ru.mark99.carapp.func.climat.seatvent` | 0x10050100 | posId:int; value:int; setFunctionValue(id,posId,mSeatVent[value]) | Có sẵn; chỉ khi API xác nhận trang bị |
| Sưởi ghế | `ru.mark99.carapp.func.climat.seatheat` |  | posId:int; value:int; ExternalHeatBoardService.pushExternalSeatHeatAction | Loại khỏi routing theo yêu cầu bỏ sưởi |
| Kính cửa | `ru.mark99.carapp.func.window` | 0x287000A2 | windowId:int; value:int; CarImpl.sendBytesToMcuByPropID(id,byte[8]) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Cửa sổ trời % | `ru.mark99.carapp.func.sunroof` | 0x287000A2 | value:int; CarImpl.sendBytesToMcuByPropID(id,byte[8]) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Rèm | `ru.mark99.carapp.func.rollshade` | 0x287000A2 | value:int; CarImpl.sendBytesToMcuByPropID(id,byte[8]) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Cửa sổ trời trạng thái | `ru.mark99.carapp.func.sunroof.state` | 0x21030400 | state:int; state=1 → setWindowPos(4,100); state=0/2 → setFunctionValue(TILT,0/1) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Chế độ lái ECO | `ru.mark99.carapp.func.drive_mode.eco` | 0x22010100 | không; setFunctionValue(id,enum) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Chế độ lái Comfort | `ru.mark99.carapp.func.drive_mode.comfort` | 0x22010100 | không; setFunctionValue(id,enum) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Chế độ lái Sport | `ru.mark99.carapp.func.drive_mode.sport` | 0x22010100 | không; setFunctionValue(id,enum) | Đã có route / bổ sung trong 1.0.9; runtime support + read-back |
| Chế độ lái Adaptive | `ru.mark99.carapp.func.drive_mode.adaptive` | 0x22010100 | không; setFunctionValue(id,enum) | Không bật: bản xe chỉ cấu hình 3 mode ECO/COMFORT/SPORT |

## Sửa dữ liệu và giới hạn

- INFO_ID_REMAINING_OIL chứa một byte **phần trăm**, không phải số lít. Nguồn: bytecode Sensors decoder lấy byte /100 × dung tích bình. Dùng FUEL_LEVEL/FUEL_CAPACITY hợp lệ trước; MCU phần trăm chỉ đổi thành lít khi biết dung tích, không mặc định bình50L.
- Fan/direction callbacks của Lunaris ở zone1; lệnh đặt dùng overload toàn xe tương ứng zone0. THT đọc zone1 trước, fallback zone0 khi không có dữ liệu hợp lệ.
- Không nói kính đóng hết khi mới đọc được một phần số kính. Ưu tiên vị trí MCU thực tế.
- Không mang min17/maxfan8 của bản Lunaris sang xe người dùng: giữ min16/maxfan7 từ dump gốc.
- Không đưa hệ sưởi ghế/bo ngoài, sưởi vô lăng/vòi phun/kính điện trở vào routing. Khử mờ HVAC và sấy kính sau là nhóm OEM riêng.
- EV/sạc/regen/cốp trước và tính năng không có mapping vẫn bị chặn. Không sao chép SDK stub có thân hàm trả0/null từ APK.

## Bằng chứng

Các JSON bytecode và Java tham chiếu ở `../voice/evidence/lunaris-20260918/`. Những phương thức đã kiểm tra trực tiếp: kết nối/callback, CarWindows setWindowPos và setSunRoofState, CarClimat setTemperature/setAutoMode/setClimatEco/setClimatRating, CarFunctionRouter driveModeControl, funcId của DRL/StartStop/AutoHold/ESC Sport, Sensors decoder xăng và MCASensorObservable.getCurrentValue. Ledger `analysis.cany` lưu evidence IDs; `KET_QUA_DOI_CHIEU.md` ghi SHA/giới hạn decompiler.

Source/tests mới độc lập với Lunaris. Unit/MOCK/CI chứng minh đường thực thi phần mềm; không chứng minh actuator đã di chuyển trên xe.
