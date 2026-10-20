# Chia màn hình qua THT Split

THT Voice không tự chia màn hình. Lệnh nói được gửi sang app riêng **THT Split**
(`com.tht.split`, repo `meomeo2099/THT-Split`) qua API v2. Tài liệu gốc phía Split:
`docs/VOICE_API.md` và `docs/THTVOICE_INTEGRATION.md` trong repo đó.

Trạng thái: **REFERENCE / UNVERIFIED**. Đã có test parser và executor với client giả, chưa thử trên xe.

## Câu nói
| Câu nói | Lệnh gửi Split |
|---|---|
| chia đôi màn hình, bật/mở (lại) chia đôi màn hình, chia đôi lại | `start` (cặp đã nhớ trong Split) |
| chia Vietmap với YouTube, chia màn hình Google Maps với YouTube | `start` + `left`/`right` + `ratio=50` |
| thoát/tắt/hủy/dừng chia (đôi) màn hình, thoát chia đôi | `stop` |
| đổi bên | `swap` |
| bên trái 40 phần trăm, bên phải 60 phần trăm, chia đều màn hình | `ratio` (trái 30–70; ngoài khoảng bị từ chối, không gửi) |
| đổi bên phải sang Spotify | `replace` + `side` + `package` |
| bên trái toàn màn hình, YouTube toàn màn hình | `fullscreen` + `side` |
| khôi phục chia màn hình | `restore` |
| ẩn / hiện thanh chia màn hình | `hide` / `show` |

Nói theo vùng miền / nghe nhầm vẫn hiểu: "bậc/tắc chia màn hình", "cho tui chia đôi màn hình",
"chia màn hình giùm cái", "đổi bên dùm", "bên trái bốn chục phần trăm", "màng hình", "mà hình", "chí đôi",
"tách đôi màn hình", "ra khỏi chia đôi", tên app nghe nhầm ("chia gu mát với diu túp"). Câu hỏi
("tắt chia màn hình hông") không bị coi là lệnh.

Tên app chỉ nhận app có trong danh mục (`AndroidAppCatalog`); tên lạ thì không phải lệnh chia.
YouTube chọn bản đã cài theo thứ tự YouTube → Vanced → ReVanced → Morphe → SmartTube.
"YouTube toàn màn hình" chỉ biết bên nào khi Voice vừa gửi cặp đó; chưa rõ thì hỏi lại bên trái/phải.

## Kết nối
- Cùng chữ ký (Android cấp `com.tht.split.permission.CONTROL_SPLIT`): gửi tới `SignatureVoiceReceiver`, không cần token.
- Khác chữ ký: gửi tới `VoiceReceiver` kèm token. Trong THT Split: Kết nối THT Voice → bật
  "Cho phép nhận lệnh từ THT Voice" → sao chép token. Trong THT Voice: Cài đặt → "Chia màn hình (THT Split)" → Dán mã.
  Token không ghi log, không đọc ra loa.
- Kết quả về qua PendingIntent một lần tới `SplitResultReceiver` (không exported). Chờ tối đa 12 giây;
  hết giờ thì hủy PendingIntent, phản hồi muộn bị bỏ.

## Phản hồi
| Split trả | Voice nói |
|---|---|
| `success` | "Đã …" (Confirmed) |
| `requested` | đã gửi, chưa xác nhận (AcceptedUnverified) |
| không phản hồi trong 12 giây | đã gửi, nhắc kiểm tra "Cho phép nhận lệnh" và mã kết nối |
| `error` API_DISABLED / OVERLAY_REQUIRED / UNSUPPORTED_OPERATION / ACCESS_BLOCKED / INVALID_* | không thể thực hiện + lời của Split |
| `error` khác (BUSY, OPERATION_FAILED…) | thực hiện không thành công + lời của Split |
| chưa cài Split / chưa có mã | không thể thực hiện + hướng dẫn |

Voice không tự gửi lại `start` khi chưa rõ trạng thái để tránh mở trùng.

## Mã nguồn
`SplitScreenCommandParser`, `SplitScreenExecutor` (+ `SplitPairMemory`), `ThtSplitClient` + `SplitResultReceiver`,
test `SplitScreenCommandTest`. PiP và cửa sổ tự do vẫn DEFERRED.
