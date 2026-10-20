# Tổng hợp REF-DIAG + REF-VOICE cho Geely Coolray / THTVoice

## 1. Phạm vi
Gói này tổng hợp kết quả phân tích hai APK do người dùng cung cấp:

- `ref-diag.apk` — SHA-256 `3e642b9ee5519f84f18978b5c2f680ce2206c74b038de1773b186bc0c65c43fb`
- `ref-voice.apk` — SHA-256 `36e0024abef318ac58a92c3a858af15ae9c1ca9c17d849737a14100be5ed3e41`

Không kèm lại hai APK gốc để tránh làm ZIP phình thêm ~245 MB; ZIP này chứa **dữ liệu phân tích, mapping và các config SX11A5 đã trích xuất**.

## 2. Kết luận chính

### REF-DIAG
- Có profile riêng `assets/configs/SX11A5/`.
- Profile SX11A5 gồm **12 ECU, 514 DID và 82 operation**.
- Các lớp/chuỗi liên quan gồm `CanService`, `VhalAccessManager`, `VehicleAutomationService`, `IVehicle`, `CarPropertyManager`.
- Phân tích cho thấy REF-DIAG có đường **UDS hai chiều qua VHAL** với property `0x2170A634`, area `0x01000000`.
- Dịch vụ UDS dùng: `0x22` đọc DID, `0x2E` ghi DID, `0x2F` actuator/I/O control, `0x31` routine, `0x10` session, `0x27` security access; extended session `0x10 03`.

### REF-VOICE
- Có trực tiếp các chuỗi `COOLRAY`, `IHU624G`, `Geely CoolRay`, `isCoolray`.
- Có `VoiceAssistantService` và nhận broadcast nút voice `ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT`.
- Dùng `CarPropertyManager` và có các nhánh Property riêng cho Coolray.
- Chứa Vosk, ONNX Runtime và Sherpa-ONNX; có `assets/model-cmd/model.onnx` (~118 MB), model Vosk tiếng Nga và TTS offline.

## 3. Kiến trúc kết hợp khuyến nghị

```text
THTVoice Coolray
   |
   +-- Direct VHAL (ưu tiên khi Property/Area đã xác nhận)
   |      +-- HVAC
   |      +-- Windows / Sunroof
   |      +-- Trunk
   |      +-- Seat heat / ventilation
   |      +-- Screen / cabin lights
   |
   +-- UDS bridge (fallback / diagnostics)
          +-- Property 0x2170A634 / Area 0x01000000
          +-- BCM 0x720
          +-- AC  0x7C6
          +-- các ECU SX11A5 khác
```

## 4. Coolray direct VHAL đáng ưu tiên
Xem đầy đủ tại `02_Combined_Mapping/ref_voice_coolray_vhal_mapping.csv`.

- HVAC temperature: `0x15600503`, area `1`, Float.
- Fan: `0x15400500`, area `5`, Coolray max 8.
- Air distribution: `0x15400501`, area `1`.
- Windows/sunroof Coolray: **`0x234020AB`**; areas `0x10`, `0x40`, `0x100`, `0x400`, `0x10000`, `0x20000`.
- Trunk Coolray: **`0x21111900`**, area `0`, `1=open`.
- Seat heat: `0x1540050B`, areas `1/4`.
- Seat ventilation: `0x15400513`, areas `1/4`.
- Screen brightness Coolray: **`0x29020400`**, area `2`.
- Interior light Coolray: **`0x15400F04`**, multiple areas.

## 5. REF-DIAG SX11A5 voice-relevant operations
BCM = `0x720`. Các operation trực tiếp trong config gồm:

- `61A6`: trunk=`1`, central lock=`2`, central unlock=`4`.
- `61A8`: mirror fold=`1`, mirror open=`2`.
- `61A7`: rear defrost=`1`, front defrost=`2`.
- `61A1`: position=`1`, low beam=`2`, high beam=`4`, front fog=`8`, DRL=`16`, turn signals=`32`.
- `61A4`: front wash=`1`, front high=`2`, front low=`4`, rear wiper=`8`, rear wash=`16`.
- `61AD`: sunroof control=`2`.

AC = `0x7C6`:
- Driver temp `6300`.
- Passenger temp `6312`.
- Fan `630B` (0..8).
- Air distribution `630C`.
- Recirculation `6308`.
- Compressor `630F`.
- Rear demist `630E`.

## 6. Quy tắc triển khai cho THTVoice
1. Ưu tiên Direct VHAL khi Property + Area + value đã xác nhận.
2. Dùng UDS/SX11A5 như fallback hoặc cho dữ liệu/operation chưa có direct property.
3. Lệnh cửa/cốp/kính cần đọc trạng thái trước, kiểm tra xe không chạy, gửi lệnh, rồi **read-back** để chỉ báo thành công khi xe xác nhận.
4. Giữ mapping theo model; `REF-VOICE` nhận Coolray qua `IHU624G`, vì vậy xe Việt Nam dùng model khác phải test/override detector thay vì giả định cùng chuỗi model.
5. Area chưa xác định chính xác tên vị trí phải log trên xe trước khi đưa vào bản khách hàng.

## 7. Không đưa vào Voice
Profile REF-DIAG còn có nhiều chức năng xưởng/chẩn đoán. Không expose qua parser Voice:
- TCU forced gear / neutral requests.
- EMS throttle, fuel pump, injector, turbo actuator tests.
- EPS calibration / end-stop routines.
- ESC/EPB maintenance routines.
- SRS routines.
- VIN write / ECU adaptation.

## 8. Cấu trúc ZIP
- `01_Report/`: báo cáo + hash APK.
- `02_Combined_Mapping/`: CSV/JSON tổng hợp để code dùng.
- `03_RefDiag_SX11A5_Raw/`: config SX11A5 gốc trích từ APK.
- `04_RefDiag_CSV/`: 12 ECU, toàn bộ 514 DID, 82 operation làm phẳng.
- `05_RefVoice_Evidence/`: chuỗi chứng cứ Coolray/Voice + inventory model/libs.
- `06_Implementation/`: đề xuất module controller và checklist test.

> Lưu ý: mapping `confidence=Candidate/Medium` phải được xác nhận bằng log trên đúng IHU/phiên bản xe trước khi cho phép điều khiển thật.
