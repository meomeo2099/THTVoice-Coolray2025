# THT Voice — Offline wake word

## Mục tiêu

THT Voice hỗ trợ wake word hoàn toàn offline và cho người dùng chọn **một** profile đang hoạt động tại một thời điểm:

- `Hey Geely` — **mặc định** (quyết định chủ repo 03/10/2026). Người dùng đã tự chọn profile khác thì giữ nguyên.
- `Hello Geely` — profile tùy chọn.
- `Hey THT` — profile tùy chọn.
- Có thể tắt wake word hoàn toàn trong Settings.

### Cách đọc được nhận (03/10/2026)

Mỗi profile Geely có nhiều dòng âm (cùng tag) trong `scripts/fetch-sherpa-kws-heytht.sh`:

| Phần | Cách đọc |
|---|---|
| Hey / Hi | "hây" `HH EY1`, "hai" `HH AY1` (chỉ Hey Geely) |
| Hello | "hơ-lâu" `HH AH0 L OW1`, "he-lô" `HH EH0 L OW1` |
| Geely | "ji-li" `JH IY1 L IY0`, "ghi-li" `G IY1 L IY0`, "ghê-li" `G EH1 L IY0`, "ghê-lây" `G EH1 L EY0`, "ghây-li" `G EY1 L IY0`; Hey Geely thêm "gi-li" `Z IY1 L IY0`, "je-li" `JH EH1 L IY0` |

Bộ nhận lệnh (ASR) hay ghi câu gọi thành "hegel", "hay galay", "hay ghe la", "hây ghê", "hay ghe lay",
"hay dây lay", "hay ghe đây"… `WakePhraseEcho` bỏ các cụm này ở đầu câu lệnh; nếu cả câu chỉ là câu
gọi thì app nghe lại thêm một lần.

### Mic của từ đánh thức

- Có cuộc gọi (kể cả qua CarPlay / Android Auto) hoặc Android 10+ báo bản ghi của app bị tắt tiếng vì
  app ưu tiên hơn đang dùng mic → nhả mic, kiểm tra lại sau 3 giây (`KwsMicPolicy`).
- Không mở được mic / mic lỗi → tự thử lại sau 2 s, 5 s, 10 s, 30 s rồi mỗi 60 s.
- Trạng thái: REFERENCE/UNVERIFIED cho đến khi thử trên xe.

KWS chỉ có nhiệm vụ đánh thức phiên nhận dạng lệnh; mọi command sau đó vẫn đi qua Sherpa 65M -> parser local -> SafetyGate -> router như trước.

## Runtime flow

```text
BOOT / service start
        |
        v
load selected wake profile
        |
        v
KWS_LISTENING
        |
selected wake phrase
        |
        v
stop KWS + release AudioRecord
        |
        v
ASR_LISTENING (Sherpa 65M)
        |
        v
PROCESSING / Safety / TTS
        |
        v
KWS_LISTENING
```

Khi đổi profile trong Settings:

```text
save preference
    |
ACTION_SETTINGS_CHANGED
    |
stop KWS + release mic
    |
close old KeywordSpotter
    |
load exactly one selected keywords file
    |
initialize new KeywordSpotter
    |
resume KWS
```

Nút ECARX dùng cùng cổng ASR nhưng không phụ thuộc wake word:

```text
ECARX -> stop KWS -> ASR -> PROCESSING/TTS -> resume KWS
```

`VoiceMicStateMachine` không cho KWS và ASR sở hữu microphone cùng lúc.

## KWS model

Model staging hiện dùng upstream Sherpa:

```text
sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20
```

- encoder INT8, chunk 8
- joiner INT8, chunk 8
- decoder FP32
- 16 kHz mono
- CPU, 1 thread mặc định
- archive SHA-256 được pin trong `scripts/fetch-sherpa-kws-heytht.sh`

Model KWS nhỏ được tách khỏi model ASR tiếng Việt 65M để giảm tải khi chờ wake word liên tục.

## Keyword profiles

Script staging tạo ba file riêng:

```text
keywords.txt                 -> @HEY_THT
keywords-hello-geely.txt     -> @HELLO_GEELY
keywords-hey-geely.txt       -> @HEY_GEELY
```

`Hey THT` dùng cách đọc tiếng Anh `hey tee aitch tee`.

Hai profile tùy chọn dùng phone tokens tiếng Anh của cùng model KWS. `Geely` được encode theo cách đọc `jee-lee`. Đây là baseline để test; độ chính xác, false wake và biến thể phát âm phải được đo trên microphone/cabin IHU689G trước khi coi là hardware-confirmed.

App **không load cả ba file cùng lúc**. `WakePhrase` quyết định đúng một `keywordsFile` khi tạo `KeywordSpotter`, giúp profile không được chọn không còn active nền.

## Packaging

Model lớn không commit vào Git history. Để stage model locally:

```bash
bash scripts/fetch-sherpa-kws-heytht.sh
```

GitHub Actions có input thủ công:

```text
include_wake_kws = true
```

để tạo APK test có đủ KWS profile assets.

Hardware-test workflow phải xác minh cả ba keyword files trước khi assemble/upload package.

Nếu model/profile được chọn không có trong APK, app vẫn build/chạy bình thường, `wake_ready=false`, ECARX/manual voice vẫn hoạt động và service không giả vờ wake word đã sẵn sàng.

## Safety / audio rules

- Không gửi audio KWS lên Internet.
- Mỗi thời điểm chỉ một wake profile được load.
- Không chạy KWS trong lúc ASR đang nghe.
- Không bật lại KWS trước khi command/TTS hoàn tất.
- Chuỗi Safety confirmation giữ KWS tạm dừng và mở lại ASR trực tiếp để nghe câu xác nhận.
- Khi tắt wake word trong Settings, KWS phải đóng AudioRecord ngay.
- Khi đổi wake profile, profile cũ phải release stream/model trước khi profile mới nghe.
- Khi service bị destroy/restart, KWS stream và AudioRecord phải được release.

## Hardware validation còn phải làm

Test **riêng từng profile**:

- Wake latency.
- CPU/RAM khi chờ liên tục.
- False wake / missed wake trong cabin yên tĩnh.
- Test khi quạt điều hòa đang chạy.
- Test khi media đang phát.
- Test nhiều vị trí người nói trong cabin.
- So sánh `Hey THT`, `Hello Geely`, `Hey Geely` trên cùng điều kiện.
- Tune `keywordsScore`, `keywordsThreshold` và chunk size nếu log thực tế yêu cầu.

Không thay đổi threshold chỉ dựa trên phỏng đoán; ghi log kết quả thực tế trước khi tune.
