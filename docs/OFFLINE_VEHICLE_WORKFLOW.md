# THT Voice — Offline Vehicle Workflow

Tài liệu này là sơ đồ làm việc chính thức của THT Voice sau khi bỏ Weather và general Q&A online. Mục tiêu là ưu tiên điều khiển/trạng thái xe chạy offline, dùng Sherpa-ONNX Streaming Zipformer tiếng Việt 65M INT8 làm STT chính.

## 1. Mục tiêu kiến trúc

- Wake word chạy offline.
- VAD/endpoint chạy offline.
- STT chạy offline bằng `sherpa-onnx-streaming-zipformer-vi-65M-int8`.
- Parser/alias/synonym chạy local.
- Vehicle command luôn đi qua typed command, không cho AI sinh raw VHAL.
- Vehicle write phải theo chu kỳ `ACK -> EXECUTE -> VERIFY -> RESULT`.
- Chỉ `Confirmed` mới được nói `Đã ...`.
- Khi tắt phản hồi giọng nói, dùng sound effect thay cho TTS.
- Một setting `Ứng dụng Media mặc định` dùng chung cho nhạc + video; parser vẫn giữ `MUSIC` và `VIDEO` riêng.
- Một setting `Ứng dụng bản đồ mặc định` cho navigation.
- Weather và general Q&A online không thuộc core.

## 2. Sơ đồ tổng thể

```text
MIC / ECARX VOICE KEY
        |
        v
+-----------------------+
| Offline KWS           |
| Selected wake profile |
+-----------+-----------+
            |
            v
+-----------------------+
| VAD / Endpoint        |
| speech start/end      |
+-----------+-----------+
            |
            v
+-----------------------------------------------+
| Sherpa-ONNX Streaming Zipformer VI 65M INT8  |
| AUDIO -> TEXT                                  |
+----------------------+------------------------+
                       |
                       v
               TextNormalizer
                       |
                       v
          VietnameseNumberParser
                       |
                       v
                Alias/Synonym
                       |
                       v
              Local Typed Parser
                       |
                       v
                 VoiceCommand
                       |
                       v
          VoiceCommandCoordinator
                       |
            +----------+----------+
            |                     |
            v                     v
      Vehicle Router        Android Router
            |                     |
      +-----+------+      +-------+--------+
      |     |      |      |       |        |
      v     v      v      v       v        v
    HVAC  Window Drive   Media   Apps   Navigation
                 /Regen    |                |
                           v                v
                    MediaPreference   NavigationPreference
                           |                |
                           v                v
                    PackageResolver    PackageResolver
                           |                |
                           +-------+--------+
                                   |
                                   v
                          Vehicle/Android Backend
                                   |
                                   v
                         Read-back / Callback
                                   |
                    +--------------+--------------+
                    |                             |
                    v                             v
                Confirmed                 AcceptedUnverified
                    |                             |
                    +--------------+--------------+
                                   |
                                   v
                         Voice Feedback Setting
                          /                 \
                        ON                  OFF
                        |                    |
                        v                    v
                  Offline TTS            Sound FX
```

Trong Settings, chọn một profile: `Hey THT` (mặc định), `Hello Geely` hoặc `Hey Geely`. KWS chỉ nạp keyword file của profile đang chọn.

## 3. Luồng lệnh xe

Ví dụ: `đặt nhiệt độ 22 độ`

```text
Sherpa 65M
  -> "đặt nhiệt độ 22 độ"
  -> TextNormalizer
  -> VietnameseNumberParser
  -> SetTemperature(22)
  -> ACK
  -> VHAL write
  -> VHAL read-back
  -> Confirmed
  -> TTS: "Đã chỉnh nhiệt độ về 22 độ."
```

Nếu write đã gửi nhưng chưa xác minh được:

```text
AcceptedUnverified
  -> không được nói "Đã ..." như thể hoàn tất
  -> phải nói dạng "Đã gửi lệnh nhưng chưa xác nhận được trạng thái."
```

## 4. Nhóm Vehicle Command

### HVAC
- Bật/tắt A/C.
- Nhiệt độ tuyệt đối.
- Tăng/giảm nhiệt độ.
- HI/LO explicit.
- Quạt 0..8.
- Tăng/giảm quạt.
- Gió trong/gió ngoài.
- Sấy kính trước/sau.
- Hướng gió mặt/chân/kính.

### Windows
- 4 kính riêng lẻ.
- ALL.
- OPEN/CLOSE/SET_PERCENT.
- STOP best-effort.

### Drive/Regen
- ECO/COMFORT/SPORT.
- Regen LOW/MEDIUM/HIGH.

### Vehicle Status
Chỉ đọc property đã xác minh:
- tốc độ;
- gear;
- nhiệt độ HVAC;
- mức quạt;
- drive mode;
- regen.

Battery/range/ODO/outside temperature chỉ thêm sau khi có Property ID xác nhận.

### Door/Lock và AVAS
Chưa write cho tới khi có mapping/backend đủ chắc. Không đoán Property ID.

## 5. Media — gộp nhạc và video trong Cài đặt

Cài đặt chỉ có một mục:

```text
Ứng dụng Media mặc định
- Tự động
- YouTube
- Vanced
- ReVanced
- Morphe
- SmartTube
- YouTube Music
- Zing MP3
- Spotify
```

Bên trong parser vẫn tách:

```text
MediaContentType.MUSIC
MediaContentType.VIDEO
```

Mục đích là tránh lỗi voice và vẫn giữ đúng ngữ nghĩa của câu lệnh.

Ví dụ:

```text
"mở nhạc"
  -> MUSIC
  -> default_media_app

"mở video"
  -> VIDEO
  -> default_media_app

"mở nhạc trên Zing MP3"
  -> MUSIC
  -> explicit provider Zing MP3
  -> explicit provider luôn ưu tiên hơn setting mặc định
```

### Package candidates

- YouTube: `com.google.android.youtube`
- Vanced: `com.vanced.android.youtube`
- ReVanced: `app.revanced.android.youtube`, `app.rvx.android.youtube`, `com.revanced.android.youtube`
- Morphe: `app.morphe.android.youtube`
- SmartTube: `org.smarttube.stable`, `org.smarttube.beta`, `com.liskovsoft.smarttubetv`, `com.liskovsoft.smarttubetv.beta`, `com.teamsmart.videomanager.tv`
- YouTube Music: `com.google.android.apps.youtube.music`, `app.revanced.android.apps.youtube.music`, `anddea.youtube.music`
- Zing MP3: `com.zing.mp3`
- Spotify: `com.spotify.music`

Resolver phải loại package trùng và chỉ mở một target cuối cùng.

## 6. Navigation

Cài đặt:

```text
Ứng dụng bản đồ mặc định
- Tự động
- Google Maps
- Vietmap Live
- Waze
```

Package:

- Google Maps: `com.google.android.apps.maps`
- Vietmap Live: `vn.vietmap.live`
- Waze: `com.waze`

Nếu câu nói có tên app cụ thể, app trong câu nói luôn ưu tiên hơn setting mặc định.

## 7. Voice Feedback

Cài đặt:

```text
Phản hồi bằng giọng nói
- Bật
- Tắt
```

### Khi bật

```text
ACK -> Offline TTS
RESULT -> Offline TTS
```

Giọng phản hồi cho phép chọn:

```text
- Ngọc Huyền — Nữ Bắc
- Diễm Trinh — Nữ Nam
```

Có nút nghe thử giọng.

### Khi tắt

Không tắt trợ lý. Chỉ thay TTS bằng hiệu ứng âm thanh:

```text
listen/start sound
processing sound
success sound
error sound
```

Logic execute/verify vẫn giữ nguyên.

## 8. Offline-first

Core mục tiêu:

```text
Wake word          -> Offline
VAD                -> Offline
STT                -> Offline Sherpa 65M
Parser             -> Offline
Vehicle control    -> Offline
Vehicle read-back  -> Offline
TTS                -> Offline
Weather            -> Removed
General AI Q&A     -> Removed
Groq STT           -> Transitional only; remove after Sherpa 65M stable
Groq LLM command   -> Transitional only; remove after local parser coverage is sufficient
```

YouTube/Maps/Zing/Spotify có thể cần Internet do chính ứng dụng đó, nhưng THT Voice không phụ thuộc cloud để nghe và hiểu lệnh xe.

## 9. Git / rollback policy

Mọi thay đổi phải commit theo từng feature family. Không sửa nhiều module lớn trong một commit nếu không cần thiết.

Mốc an toàn trước khi chuyển Sherpa 65M:

```text
branch: backup/pre-sherpa65m-offline-core
commit: 22dee9d5bff54ec02f7fe10ea6ba77252da45ee6
```

Có thể quay về mốc này bằng Git khi cần.

Ví dụ local:

```bash
git fetch origin
git switch backup/pre-sherpa65m-offline-core
```

Hoặc tạo nhánh khôi phục từ commit:

```bash
git switch -c restore/pre-sherpa65m 22dee9d5bff54ec02f7fe10ea6ba77252da45ee6
```

Không force-reset `main` trong quá trình test nếu chưa cần; ưu tiên tạo branch hoặc revert commit để giữ lịch sử.

## 10. Thứ tự triển khai tiếp theo

```text
1. Hoàn thiện Android baseline buildable
2. Tích hợp Sherpa JNI/runtime
3. Tích hợp streaming Zipformer VI 65M INT8
4. VAD/endpoint
5. Mic streaming pipeline
6. Regression test số tiếng Việt + lỗi "hi/hai"
7. Benchmark CPU/RAM/latency trên IHU689G
8. Nếu 65M quá nặng -> fallback 30M INT8
9. Tích hợp offline TTS + chọn giọng
10. Tích hợp Voice Feedback ON/OFF + sound FX
11. Tích hợp Default Media App
12. Tích hợp Default Navigation App
13. Test full command flow trên head unit thật
14. GitHub Actions build APK
```
