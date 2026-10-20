# THT Voice — ASR diagnostics / benchmark preparation

## Mục tiêu

Phần này chuẩn bị dữ liệu đo thật trên IHU689G cho Sherpa 65M mà không thay đổi parser, endpoint policy hoặc kết quả nhận dạng.

Mọi backend tạo bởi `SherpaAsrEngineFactory` đều được bọc bởi `InstrumentedOfflineAsrEngine`. Dữ liệu chỉ tồn tại process-local, tối đa 50 phiên gần nhất và không lưu raw microphone audio hoặc transcript.

## Dữ liệu mỗi phiên

`AsrSessionMetrics` ghi:

- backend (`online-transducer` / `offline-transducer`)
- `model-source.txt` nếu model có khai báo nguồn
- sample rate và tổng số audio sample đã đưa vào engine
- audio duration suy ra từ sample count
- wall time của phiên ASR
- tổng wall time nằm bên trong các ASR API (`acceptWaveform`, `partialText`, `isEndpoint`, `finishStream`)
- thời gian riêng của `finishStream`
- latency tới partial text đầu tiên nếu backend có partial
- process CPU delta trong phiên
- PSS trước/sau phiên và delta
- endpoint có được thấy hay không
- snapshot VAD/endpoint tại lúc bắt đầu phiên: `vadRms`, `minSpeech`, `silence`, `maxListen`
- số ký tự final text, nhưng không lưu nội dung câu nói
- outcome: `SUCCESS`, `EMPTY_RESULT`, `INPUT_REJECTED`, `CANCELLED`

Snapshot VAD được chụp **một lần khi `startStream()` thành công**. Nếu người dùng đổi Settings trong khi một phiên đang nghe, log của phiên đó vẫn giữ đúng cấu hình đã dùng lúc phiên bắt đầu; phiên sau mới ghi cấu hình mới.

## `computeRTF`

`computeRTF = ASR API compute wall time / microphone audio duration`.

Đây là chỉ số dùng để so sánh tương đối backend/model trong THT Voice. Nó **không phải full-system realtime factor** vì không cộng thời gian người dùng đang nói, UI, parser, command execution hoặc TTS.

Ví dụ:

- audio = 2000 ms
- tổng thời gian bên trong ASR API = 600 ms
- `computeRTF = 0.300`

Giá trị nhỏ hơn 1 cho thấy tổng thời gian compute được đo thấp hơn độ dài audio, nhưng kết luận model có phù hợp IHU689G hay không vẫn phải dựa trên nhiều phiên thật cùng CPU/RAM/latency.

## Đọc snapshot

Code debug/support có thể gọi:

```kotlin
val snapshot = AsrDiagnostics.snapshot(limit = 20)
```

Snapshot cung cấp:

- số sample đang giữ
- số phiên thành công
- average wall time
- average computeRTF
- average process CPU ms
- peak PSS
- các dòng formatter của những phiên gần nhất

Xóa dữ liệu process-local:

```kotlin
AsrDiagnostics.clear()
```

## Lấy số liệu trực tiếp bằng ADB / Logcat

Production `SherpaAsrEngineFactory` xuất đúng một dòng metrics sau mỗi phiên ASR với tag:

```text
THTVoiceASR
```

Có thể xem riêng tag này khi test xe:

```bash
adb logcat -s THTVoiceASR:I
```

Ví dụ dạng log:

```text
ASR backend=offline-transducer model=... outcome=SUCCESS audio=2100ms wall=2360ms compute=640ms computeRTF=0.305 finish=35ms cpu=510ms pss=218000KB pssDelta=420KB endpoint=true vadRms=0.012 minSpeech=180ms silence=700ms maxListen=12000ms chars=18
```

`chars=18` chỉ là **số ký tự** của kết quả cuối. Log không chứa câu đã nói, transcript hoặc raw microphone audio.

Các field `vadRms/minSpeech/silence/maxListen` cho phép đối chiếu trực tiếp session với profile Settings đang thử. Không cần suy đoán người test đã chọn profile nào từ ghi chú thủ công.

Nếu Logcat không khả dụng, endpoint config provider lỗi hoặc publisher phát sinh exception, diagnostics bị bỏ qua phần metadata tương ứng và kết quả nhận dạng vẫn tiếp tục bình thường. Diagnostics không được phép trở thành dependency bắt buộc của voice pipeline.

## Quy trình benchmark IHU689G sau này

1. Build APK full ASR 65M đúng model cần test.
2. Khởi động xe/app ổn định, không benchmark ngay trong lúc model vừa copy lần đầu.
3. Mở `adb logcat -s THTVoiceASR:I` trên máy tính.
4. Bắt đầu với profile mặc định: RMS `0.012`, min speech `180 ms`, silence `700 ms`, max listen `12 s`.
5. Chạy cùng một bộ lệnh trong nhiều tình huống: cabin yên, quạt điều hòa, media đang phát, giọng Bắc/Nam.
6. Khi thử profile khác, chỉ đổi một nhóm setting mỗi lần và xác nhận field VAD trong log đã thay đổi đúng.
7. Lấy nhiều session metrics, không kết luận từ một mẫu đơn lẻ.
8. So sánh latency, computeRTF, CPU ms, PSS, missed/false speech và độ chính xác câu lệnh.
9. Chỉ thử 30M fallback nếu số liệu 65M trên IHU689G thật chứng minh tải quá nặng hoặc latency không đạt yêu cầu.

## Giới hạn

- Process CPU ms là CPU time của process trong khoảng ASR session, không phải phần trăm CPU toàn hệ thống.
- PSS là snapshot process tại boundary, không phải peak memory liên tục giữa phiên.
- Offline Gipformer có thể decode trong `partialText()` khi local endpoint đạt điều kiện; vì vậy chỉ nhìn `finishMs` sẽ không phản ánh toàn bộ decode cost. Hãy dùng `asrComputeMs` / `computeRTF` để so sánh.
- Với online-transducer, native Sherpa endpoint vẫn tồn tại song song local endpoint; tăng `silence` không đảm bảo native endpoint sẽ chờ lâu hơn.
- Dữ liệu hiện là công cụ chẩn đoán; chưa được coi là hardware validation cho tới khi thu từ IHU689G thật.
