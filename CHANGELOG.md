# Changelog

## 1.0 — đang phát triển

- Khởi tạo dự án **THT Voice**.
- Android application ID public: `com.tht.voice`.
- Kiến trúc typed-command local-first với chu kỳ `ACK -> EXECUTE -> VERIFY -> RESULT`.
- HVAC nền tảng EX2: nhiệt độ 16–32°C, A/C ON/OFF, quạt 0–8, tăng/giảm quạt, gió trong/ngoài, sấy kính trước/sau và các mask hướng gió IHU629G đã xác minh.
- Kính: 4 vị trí + ALL + phần trăm + read-back; dùng `WINDOW_POS=322964416` và area EX2 đã xác minh.
- Drive mode và regeneration: typed executor + read-back.
- Vehicle status query read-only mở rộng: tốc độ, gear, SOC, range, ODO, trip, nhiệt độ ngoài, cửa/khóa, trạng thái sạc, dòng/điện áp sạc, ghế phụ và dây an toàn trước.
- Android commands: mở app, tìm media, điều khiển media, navigation.
- Media mặc định được gộp trong UI nhưng vẫn giữ semantic MUSIC/VIDEO riêng trong parser/router.
- YouTube routing ưu tiên stock -> Vanced -> ReVanced -> Morphe -> SmartTube và không fallback sang trình duyệt chung.
- Navigation hỗ trợ Google Maps / Waze / Vietmap; app nói trực tiếp sẽ ghi đè lựa chọn mặc định.
- Play/Pause/Toggle media xác nhận bằng `AudioManager.isMusicActive`; volume xác nhận bằng stream-volume read-back.
- Next/Previous dùng MediaSession metadata read-back: chỉ xác nhận thành công khi track identity thay đổi trên cùng package + cùng session; nếu không có quyền/metadata hoặc chưa chứng minh được thay đổi thì giữ `AcceptedUnverified`.
- Sherpa-ONNX Vietnamese/Gipformer 65M INT8 đã có pipeline staging/build full-ASR; model lớn không nằm trong Git history thường.
- ASR diagnostics process-local bọc cả online/offline backend: giữ tối đa 50 phiên, đo audio/session/ASR compute/finish/first-partial, process CPU, PSS và `computeRTF`; không lưu raw audio hoặc transcript, dùng để benchmark IHU629G thật trước khi cân nhắc fallback 30M.
- ASR diagnostics được xuất thêm qua Logcat tag `THTVoiceASR` sau mỗi session; chỉ ghi số liệu hiệu năng/số ký tự, không ghi transcript/raw audio, và lỗi publisher không được làm ảnh hưởng nhận dạng.
- Offline wake word `Hey THT`: Sherpa KWS model nhỏ riêng, microphone state machine không cho KWS/ASR chạy đồng thời, ECARX tạm dừng KWS trước ASR và KWS chỉ resume sau command/TTS; còn chờ benchmark/tune trên IHU629G thật.
- Part 2.7 VAD/endpoint: thêm runtime config có clamp an toàn cho cả online/offline Sherpa; mặc định vẫn RMS `0.012`, min speech `180 ms`, trailing silence `700 ms`, max listen `12 s`; Settings có độ nhạy mic, ngắt câu sau im lặng và thời gian nghe tối đa. Các profile non-default vẫn chờ xác nhận trên IHU629G thật.
- Part 2.8 VAD diagnostics: mỗi ASR session chụp snapshot `vadRms`, `minSpeech`, `silence`, `maxListen` vào metrics/Logcat để đối chiếu profile khi test IHU629G; metadata chỉ là số cấu hình, không ghi raw audio hay transcript và provider lỗi không được làm gián đoạn nhận dạng.
- Floating voice overlay: card nhỏ kéo được, hiển thị listening/partial/final/processing/result/error trực tiếp từ foreground voice service; auto-hide có generation guard để phiên cũ không đóng nhầm phiên mới; còn chờ kiểm tra bố cục/touch trên IHU629G thật.
- Bộ hardware-validation IHU629G mới gồm checklist chuẩn và PowerShell collector cho Windows; thu Logcat, ASR metrics, meminfo, MediaSession và car-service snapshot để xác minh KWS/VAD/ASR/HVAC/kính/media mà không đánh đồng MOCK/CI với xe thật.
- Offline TTS Sherpa VITS + AudioTrack và sound cues khi tắt phản hồi giọng nói.
- SafetyGate dùng SPEED + GEAR thật của IHU629G, fail-closed nếu telemetry nhạy cảm không đọc được.
- `Window`, `DriveMode`, `Regen` yêu cầu xác nhận khi xe không ở trạng thái parked; pending command hết hạn sau 8 giây và SPEED/GEAR được re-check trước khi execute.
- `PARKED_ONLY` yêu cầu `gear=P` và `speed<1 km/h`, không thể vượt qua bằng câu “đồng ý”.
- Safety audit giữ tối đa 100 sự kiện process-local và có formatter/snapshot phục vụ debug.
- Updater modular: license Worker -> trusted `/api/update/apk?asset_id=...` -> giới hạn kích thước -> SHA-256 -> package -> versionCode -> chữ ký -> parked gate -> Android installer.
- APK update có thể tải khi xe đang chạy nhưng cài đặt bị defer; deferred update được lưu trong app-private storage và re-verify đầy đủ mỗi lần thử cài lại.
- Kích thước asset từ Worker được giữ xuyên suốt metadata/deferred state và kiểm tra lại trước SHA/install.
- Bỏ Weather và general Q&A online khỏi scope chính; THT Voice tập trung vào điều khiển/trạng thái xe.
- Không dùng tên thương hiệu hãng xe trong branding, UI, release hoặc tài liệu public.
