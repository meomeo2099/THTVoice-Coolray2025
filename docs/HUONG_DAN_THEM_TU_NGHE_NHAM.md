# Hướng dẫn: thêm từ app hay nghe nhầm (cho chủ repo)

Dùng khi app nghe sai một lệnh, ví dụ bạn nói "mở YouTube" nhưng app nghe thành "mở you tụ bay".
Bạn chỉ cần dạy app: **"cụm nghe nhầm" → "từ đúng"**. Làm hoàn toàn trên trang GitHub, không cần
cài gì.

## Cách nhanh nhất: nhắn cho AI

Gửi theo mẫu, mỗi dòng một câu:

```
mở you tụ bay → mở youtube
sấy cừ → sấy kính
```

AI sẽ thêm vào code, viết test, tạo PR và merge khi CI xanh.

## Tự sửa trên GitHub

### 1. Mở file cần sửa

Vào repo → `app/src/main/java/com/voicegeely/app/voice/VoiceTextNormalizer.kt` → bấm biểu tượng
cây bút (**Edit this file**).

Bấm Ctrl+F, tìm dòng gần với lệnh bạn muốn sửa. Các nhóm có sẵn chú thích: `// Apps.`,
`// Windows:`… Ví dụ dòng YouTube:

```kotlin
Regex("${B}(?:you (?:tụ|tu|tú|tù|tủ) bay|diu tụ bay|you tụp bay|you tụi bay|youtu bay|you tụp|you túp|yoube|you tube|diu túp|yu túp)$E") to "youtube",
```

### 2. Thêm cách nghe nhầm

**Thêm vào dòng có sẵn:** chèn cách nghe nhầm mới vào trong ngoặc `(?: … )`, ngăn cách bằng dấu
`|`. Ví dụ thêm "diu tu bê":

```kotlin
Regex("${B}(?:diu tu bê|you (?:tụ|tu|tú|tù|tủ) bay|…)$E") to "youtube",
```

**Thêm dòng mới:** copy một dòng có sẵn và sửa hai chỗ: chữ nghe nhầm và từ đúng.

```kotlin
Regex("${B}(?:sai cừ|sấy cừ)$E") to "sấy kính",
```

### 3. Quy tắc

| Quy tắc | Vì sao |
|---|---|
| Viết **chữ thường, có dấu**, đúng như app nghe ra | App so khớp chữ thường |
| Cụm **dài viết trước**, cụm ngắn viết sau (`you tụp bay` trước `you tụp`) | Tránh khớp nửa chừng |
| Giữ nguyên `${B}` ở đầu và `$E` ở cuối | Chỉ khớp nguyên từ, không khớp giữa chữ khác |
| Không thêm cụm quá ngắn hay quá phổ biến (ví dụ `bay`, `mở`) | Sẽ làm sai các lệnh khác |
| Mỗi dòng kết thúc bằng dấu phẩy `,` | Sai cú pháp thì build lỗi |
| Không dùng các ký tự `( ) [ ] . ? * + \` trong chữ nghe nhầm | Đây là ký tự đặc biệt của Regex |

### 4. Tạo commit và PR

1. Bấm **Commit changes…** (góc phải trên).
2. Commit message: ghi ngắn gọn, ví dụ `Alias: mở diu tu bê → mở YouTube`.
3. Chọn **Create a new branch for this commit and start a pull request**, rồi bấm **Propose changes**.
4. Bấm **Create pull request**.
5. Chờ CI chạy (khoảng 10 phút). Có dấu ✅ xanh thì bấm **Squash and merge**.
   Nếu là ❌ đỏ, mở mục **Details** để xem lỗi, thường do thiếu dấu phẩy hoặc thiếu ngoặc; sửa lại
   ngay trên nhánh đó.

**Không sửa thẳng vào `main`.** Đi qua PR thì CI kiểm tra trước, lỗi không lọt vào bản build.

### 5. (Nên làm) thêm test

Mở `app/src/test/java/com/voicegeely/app/voice/OwnerAsrSlipRegressionTest.kt`, thêm câu nghe
nhầm vào danh sách gần đúng nhóm. Ví dụ YouTube:

```kotlin
for (text in listOf("mở you tụp bay", …, "mở diu tu bê")) {
```

Test đảm bảo sau này không ai vô tình làm mất lệnh của bạn.

### 6. Lấy APK mới để thử

Vào **Actions** → **IHU689G hardware test APK** → **Run workflow** (nhánh `main`). Khoảng 15 phút
sau tải tại Release `hardware-test-latest`:
`https://github.com/meomeo2099/THTVoice/releases/tag/hardware-test-latest`

## Tên ứng dụng (không cần sửa code)

Mở app bằng tên ("mở Zalo", "mở Spotify") đã tự tìm theo tên các app cài trên máy. Chỉ thêm vào
`InstalledAppMatcher.kt` khi app có tên tiếng Anh hay bị đọc theo kiểu tiếng Việt (ví dụ
`"du túp" to "youtube"`).

## Cách nói theo vùng miền (09/10/2026)

App đã hiểu sẵn (trong `regionalSpeech` của `VoiceTextNormalizer.kt`, test ở `RegionalSpeechRegressionTest.kt`):

| Nói | Hiểu là |
|---|---|
| kiếng, kéo kiếng lên, hạ kiếng xuống hết | kính, đóng hết kính, hạ hết kính |
| máy lạnh | điều hòa |
| tắc / bậc (đầu câu), quạc | tắt / bật, quạt |
| dô, gió dô chân | vào, gió xuống chân |
| tui, cho tui …, mở giùm cái …, … dùm tui | tôi, bỏ phần nhờ vả |
| … nha / nhen / nghen / hỉ / nè / với / vậy / hả | bỏ tiếng đệm cuối câu |
| … hông / hổng (cuối câu hỏi) | không |
| kính nên, gió nên (miền Bắc n/l) | kính lên, gió lên |
| xài google map | mở google maps |

Muốn thêm: nhắn AI theo mẫu "cách nói → cách chuẩn", hoặc thêm dòng vào `regionalSpeech` như hướng dẫn ở trên.

## Từ nghe nhầm gần âm (09/10/2026)

Lỗi sai dấu hoặc gần âm ở các từ lệnh, sửa trong `nearSoundSlips` (`VoiceTextNormalizer.kt`), test ở `NearSoundSlipRegressionTest.kt`:

| Nghe thành | Sửa thành |
|---|---|
| bặt / bất (đầu câu) | bật |
| tất / tát / tặt (đầu câu) | tắt |
| mỡ / mớ (đầu câu) | mở |
| dảm / giám (đầu câu) | giảm |
| đông / đống hết kính | đóng hết kính |
| xấy kính, sấy kín | sấy kính |
| điều hoa / khoa / hào | điều hòa |
| nhiệt đổ / đồ / đô | nhiệt độ |
| tăng/giảm quạ, quát | quạt |
| mở/tắt nhạt, nhặc | nhạc |
| đèn viên / viện, đen viền | đèn viền |
| lấy dó / giò, gió chong | lấy gió, gió trong |
| bảng đồ, bản độ | bản đồ |
| âm lượn / lương | âm lượng |
| bày tiếp | bài tiếp theo |

## Tên ứng dụng nghe nhầm (09/10/2026)
Động từ đầu câu: `mơ / mờ / mợ / mỡ / mớ` → `mở`.
Tên app chung (đổi ở `VoiceTextNormalizer`, dùng cho cả mở app, nhạc, chỉ đường, chia màn hình):

| Nghe thành | Hiểu là |
|---|---|
| gu mát, gú gồ mát, goo gồ mát, gút/gúc gồ mát, gu gồ máp/mắp, go gồ ma/mát/mép, gâu gồ ma/mát/mép | Google Maps |
| du/diu/giu/yêu/iu/dù/ru túp (tuýp) | YouTube |
| youtube miu dích / mu dích | YouTube Music |
| việt máp, việt mép, viết map, viết mát, vi ét map | Vietmap |
| (sờ/sì) pô ti phai, spô ti phai, sport ti phai | Spotify |
| din/dinh/ding/zin em pê ba, din mp3 | Zing MP3 |

Chỉ ở vị trí tên app sau "mở …" (`InstalledAppMatcher.spokenAliases`): quây dơ/guây dơ/uây dơ → Waze;
tít tốc, tích tắc, tik tốc → TikTok; nét phờ lích, nét flix → Netflix; crôm, khờ rôm → Chrome;
vê lê xê → VLC; kì ki, kí ki → Kiki; za lô, da lồ → Zalo; phây búc → Facebook; mét xen giơ → Messenger;
gờ meo, gi meo → Gmail; sóp pi, sốp pi → Shopee; gờ ráp → Grab; chợ play, plây sto → Play Store.
Test: `AppNameSlipRegressionTest`.
