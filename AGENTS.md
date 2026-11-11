# THT Voice (Coolray 2025) — AGENTS.md

> **Repo này là bản riêng cho Coolray 2025 (đầu xe ECARX `SX11-LA` / `IHU730P`, Android 9), dựng từ THTVoice-Coolray.**
> - Số hiệu VHAL: `car/CoolrayProperties.kt`; hàm ECARX AdaptAPI: `car/Coolray2025Functions.kt`; bảng: `docs/COOLRAY2025_PROPERTIES.md`.
> - Đặc tả lệnh + danh mục đầy đủ hàm AdaptAPI 1.1.18.9: `docs/COOLRAY2025_VOICE_SPEC.md`.
> - Nhóm lệnh được phép ghi xuống xe: `voice/CoolrayCapabilities.kt`. Nhóm chưa có mapping Coolray 2025 phải để tắt.
> - Không lấy mapping từ Coolray cũ (IHU624G/IHU689G: kính 591405227, REF-WIDGET…). Dùng cấu hình đầu xe 2025, kể cả khi giá trị area trùng model khác.
> - Không thêm mã/luồng EV (sạc, regen, cốp trước).

Đây là chỉ dẫn bắt buộc cho mọi AI/code agent làm việc trong repository này.

## 0. Khi người dùng nói "tiếp tục" / "làm tiếp" / "việc tiếp theo"

Không hỏi lại. Làm ngay theo [`docs/AGENT_PROMPT.md`](docs/AGENT_PROMPT.md):
1. `git fetch --prune`, xem `main` và PR đang mở.
2. Đọc `docs/HANDOFF_WORKFLOW.md`; chọn mục chưa tick đầu tiên trong `docs/COOLRAY_DEPLOYMENT_PLAN.md` (thứ tự C2 → C6).
3. Làm đúng một mục theo quy trình mục 3 của `docs/HANDOFF_WORKFLOW.md`, chờ CI xanh, báo cáo theo mẫu trong `docs/AGENT_PROMPT.md`.
4. Hết việc phần mềm → báo lại; không tự bịa việc mới hoặc làm mục DEFERRED.

Dữ liệu cần thiết đều nằm trong `docs/`; không dùng nguồn bên ngoài.

## 1. Mục tiêu
THT Voice bản Coolray: trợ lý điều khiển/trạng thái xe Geely Coolray, chạy offline. Lõi voice (ASR/KWS/TTS/UI/parser) kế thừa từ THT Voice; phần xe viết riêng theo phần Coolray của GalaxyVoiceAssistant.

## 2. Danh tính cố định
- Tên ứng dụng: `THT Voice`
- Tên repo: `THTVoice-Coolray2025`
- Android application ID: `com.tht.voice.coolray2025` (namespace/R class vẫn `com.tht.voice`; tách khỏi bản EX2 và Coolray cũ để license và OTA riêng)
- Khởi đầu: `versionCode=1`, `versionName=1.0`
- Không dùng tên thương hiệu hãng xe làm tên app, logo, badge, release branding hoặc package.
- Ngoại lệ chức năng đã chốt: Settings/KWS có thể cung cấp các wake phrase compatibility `Hey THT`, `Hello Geely`, `Hey Geely`. Đây chỉ là keyword kích hoạt; không được suy diễn thành đổi branding app.
- Không copy branding, asset, signing key, activation secret hoặc source riêng của ứng dụng khác.
- Các mã kỹ thuật cần thiết như SX11-LA, IHU730P, VHAL/property ID chỉ được dùng trong lớp adapter/reference kỹ thuật, không dùng làm branding.

## 3. Kiến trúc bắt buộc
`Voice input -> Offline ASR -> TextNormalizer -> Alias/Synonym -> DynamicCommandParser -> Typed VoiceCommand -> VoiceCommandCoordinator -> CommandRouter -> Handler -> VehicleAccess/Android subsystem -> Read-back -> Result TTS`

- STT mục tiêu: `sherpa-onnx-streaming-zipformer-vi-65M-int8`.
- Wake word/KWS và VAD phải chạy offline.
- Wake profile mặc định là `Hey Geely` (chủ repo chốt 03/10/2026); profile tùy chọn hiện có `Hello Geely` và `Hey THT`.
- Mỗi thời điểm chỉ một wake profile được load; đổi profile phải release KWS/mic cũ trước khi profile mới bắt đầu nghe.
- Weather và general Q&A online không thuộc scope chính.
- Câu ngoài phạm vi xe không được tự gọi Internet; phản hồi local ngắn gọn.
- Trong giai đoạn chuyển đổi, Groq cũ chỉ là thành phần tạm thời và phải được loại khỏi đường voice chính khi Sherpa 65M ổn định.
- LLM không được trực tiếp thực thi raw VHAL property ID, OEM function ID, shell command hoặc actuator write.

## 4. Parser local trước
- Chuẩn hóa Unicode, dấu câu và lỗi ASR tiếng Việt.
- Parse số tiếng Việt trước keyword endpoint.
- Alias phải quy về target canonical.
- Temperature/fan/window percent dùng pattern có giới hạn.
- Handler không parse lại free-form text.
- Mơ hồ -> `NeedClarification`, không đoán thao tác xe.

### HVAC bắt buộc
- `20`, `hai mươi`, `20 độ` -> 20°C.
- `22`, `hai hai` trong ngữ cảnh HVAC -> 22°C.
- `cao nhất`, `tối đa`, explicit `hi` -> HI.
- `thấp nhất`, `tối thiểu`, explicit `lo` -> LO.
- Không substring hoặc lỗi nhận dạng liên quan chữ/số 2 nào được phép kích hoạt HI.
- Phải có regression test 16..32 + HI + LO (parser). Coolray 2025: executor chỉ ghi 16..32 °C bước 0,5; HI = 32, LO = 16; "22 rưỡi" / "22,5" = 22,5 °C; số ngoài khoảng bị từ chối, không ghi.

## 5. VehicleAccess
- Chỉ một facade production sở hữu truy cập xe thật.
- Android Car/VHAL là đường chính khi đã xác minh hoặc đã có mapping/reference kỹ thuật đủ rõ trong tài liệu/source của dự án.
- ECARX/Flyme/OEM fallback theo từng tính năng có thể triển khai từ tài liệu, source, firmware/API reference, Coolray reference implementation (GalaxyVoiceAssistant), public property catalog và catalog kỹ thuật đã lưu trong repo.
- Không suy đoán mapping raw VHAL ID sang OEM function ID; mapping phải xuất phát từ reference/tài liệu/source cụ thể đang có trong dự án.
- Kết quả write phân biệt `DENIED`, `FAILED`, `ACCEPTED`, `CONFIRMED`.
- Ưu tiên read-back trước khi báo thành công.
- Binder/DeadObject chỉ reconnect/retry có giới hạn.
- MOCK không chứng minh xe thật.

### Quy tắc triển khai — không chờ evidence trên xe thật
- **Cứ triển khai đầy đủ các chức năng theo tài liệu/source/reference hiện có; không dừng công việc để chờ evidence, log hoặc test trực tiếp trên xe Coolray thật.**
- Tài liệu/source/reference kỹ thuật đã có trong repo và các nguồn tham chiếu của dự án được xem là đủ để tiếp tục code, parser, handler, adapter, backend, fallback, test MOCK/unit, CI và packaging.
- Có thể thêm property ID, area ID, function ID, method mapping, reflection catalog hoặc backend route mới khi chúng được lấy từ tài liệu/source/reference kỹ thuật hiện có; không cần chờ log xe thật trước khi code.
- Những phần chưa test trên xe thật phải ghi rõ trạng thái `REFERENCE`/`UNVERIFIED`; chỉ chuyển sang `REAL`/`CONFIRMED` sau khi có hardware test/read-back thực tế.
- Thiếu hardware evidence không phải blocker cho development, merge, CI, build test APK hay triển khai feature tiếp theo.
- Không được dùng lý do “chưa có evidence trên xe thật” để dừng, trì hoãn hoặc yêu cầu người dùng chờ trước khi triển khai phần tiếp theo.

### Coolray 2025 reference
- Nguồn: dữ liệu lấy trực tiếp từ đầu xe (getprop, `dumpsys car_service`, `AdapterAPI.jar`), lưu trong `docs/COOLRAY2025_PROPERTIES.md` và `docs/COOLRAY2025_VOICE_SPEC.md`.
- VHAL: chỉ dùng property có trong config dump của đầu xe, đúng area/min/max ở đó. Capture 11/11/2026 xác nhận HVAC area `0x75 = 117`, nhiệt độ `0x31 = 49` / `0x44 = 68`, quạt 1..7, `HVAC_DEFROSTER` 1/2. Bản HAL dump in area theo hệ 16; `CarPropertyConfig.mSupportedAreas` in hệ 10.
- ECARX AdaptAPI: gọi qua `ICarFunction` (`car/EcarxFunctionAccess.kt`), không ghi function ID qua CarPropertyManager. Luôn `isFunctionSupported` trước khi ghi và đọc lại sau khi ghi. Giá trị enum = `functionId + n`.
- Không dùng số của EX2, E5, Starship 7 hay Coolray IHU624G/IHU689G.

ID/function mapping mới được phép thêm từ tài liệu/source/reference kỹ thuật đã có. Nếu chưa test trên xe thật thì giữ nhãn `REFERENCE`/`UNVERIFIED`, không tự ghi là hardware-confirmed.

## 6. Handler boundaries
Duy trì handler độc lập cho HVAC, windows, tailgate, vehicle query, media, app launch, navigation, Wi-Fi/display/overlay. Nhóm mới (cửa sổ trời, đèn, ghế, màn hình…) thêm executor riêng khi có mapping Coolray.

## 7. Voice response lifecycle — bắt buộc
Mọi command có actuator hoặc thay đổi trạng thái phải đi theo:

`ACK -> EXECUTE -> VERIFY -> RESULT`

- `FULL`: đọc lại yêu cầu trước khi thực hiện, sau đó đọc kết quả.
- `SHORT`: chỉ đọc kết quả cuối.
- `SILENT`: không TTS, vẫn log/execute.
- Chỉ `CommandExecutionResult.Confirmed` được phép phát câu khẳng định `Đã ...` với nghĩa thao tác đã hoàn tất.
- `AcceptedUnverified` phải nói rõ đã gửi lệnh nhưng chưa xác nhận được trạng thái.
- `Rejected`/`Failed` phải phát phản hồi thất bại phù hợp, không dùng success template.
- Vehicle command ưu tiên `VEHICLE_READ_BACK` hoặc `VEHICLE_CALLBACK`.
- App/media dùng `ANDROID_STATE`/`MEDIA_SESSION` khi có thể.
- Handler không tự phát success TTS; TTS tập trung tại `VoiceCommandCoordinator` và `VoiceResponseFormatter`.
- Đọc `docs/VOICE_WORKFLOW.md` trước khi thay đổi luồng thực thi/phản hồi.

## 8. Reliability
- ECARX voice key phải hoạt động khi Activity không mở.
- Foreground/keep-alive chỉ dùng khi cần.
- Watchdog bounded + debounced.
- Restore actuator state phải rõ ràng và cho người dùng chọn.

## 9. Testing
Mỗi command family cần parser tests, alias tests, boundary tests và MOCK handler tests. Hardware log/read-back dùng để nâng trạng thái từ `REFERENCE`/`UNVERIFIED` lên `REAL`/`CONFIRMED`, nhưng **không phải điều kiện để tiếp tục triển khai hoặc merge code**.

Phản hồi voice phải có test tối thiểu cho:
- FULL phát ACK + RESULT.
- SHORT chỉ phát RESULT.
- SILENT không phát TTS.
- `AcceptedUnverified` không được nói thành công.
- Exception từ executor phải thành `Failed`, không crash voice pipeline.

ASR offline mục tiêu benchmark latency, RAM, CPU và realtime factor trên head unit Coolray; 65M là mặc định mục tiêu, 30M chỉ fallback khi benchmark thực tế yêu cầu. Chưa có benchmark trên xe không được dùng làm blocker cho việc tiếp tục phát triển.

KWS nên hardware-test riêng cho từng wake profile đang ship: latency, false wake, missed wake, media/quạt nền và mic handoff. Trước khi có test xe thật vẫn tiếp tục triển khai từ model/config/reference hiện có và giữ trạng thái chưa xác minh phần hardware-specific.

## 10. Git/version
- Repo khởi tạo từ nền THT Voice (01/10/2026).
- `versionCode` bắt đầu 1 và tăng 1 cho mỗi APK release/test cần nâng cấp.
- `versionName` bắt đầu 1.0.
- Commit nhỏ theo feature family.
- Không commit API key, Cloudflare token, activation secret, VIN/phone, keystore hoặc signing key.

## 11. Definition of done
Command hoàn tất về mặt development khi typed command đúng, target/value được log, handler/backend đúng, write acceptance rõ ràng, retry/fallback bounded, phản hồi TTS đúng trạng thái và tài liệu ghi rõ MOCK/REFERENCE/REAL. Hardware read-back thực tế là bước nâng mức xác nhận lên `REAL`/`CONFIRMED`, không phải blocker để hoàn tất implementation và chuyển sang feature tiếp theo.
