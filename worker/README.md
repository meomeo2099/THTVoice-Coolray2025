# Máy chủ bản quyền THT Voice (Cloudflare Worker)

> **Bản Coolray dùng máy chủ riêng, tách hẳn khỏi bản EX2.** `wrangler.toml` / `admin.wrangler.toml`
> trong repo này đã đặt sẵn: worker `tht-voice-coolray-license`, trang quản lý `keythtvoice-coolray`,
> cơ sở dữ liệu D1 `tht-voice-coolray-license`, mã khóa `c1`, cập nhật app lấy từ repo
> `meomeo2099/THTVoice-Coolray`. Làm theo mục **Cài lần đầu** bên dưới với các tên này
> (`npx wrangler d1 create tht-voice-coolray-license`, `node scripts/gen-license-key.mjs c1`), tạo khóa ký
> **mới** (không dùng lại khóa của EX2), rồi sửa `config/license-server.properties` theo mục
> **Trỏ app sang máy chủ mới**. Khi đó mã kích hoạt của bản EX2 không dùng được trên Coolray và ngược lại.
> `GITHUB_TOKEN` cần quyền đọc Releases của repo `THTVoice-Coolray`.

Máy chủ mới cho kích hoạt, gia hạn, khóa thiết bị, cập nhật app và tải gói giọng đọc. Toàn bộ code nằm trong thư mục này; **khóa bí mật chỉ nằm trong Cloudflare secrets**, không bao giờ nằm trong repo.

## Có gì

| Chức năng | Cách hoạt động |
|---|---|
| Mã kích hoạt | 20 chữ số (`1234-5678-9012-3456-7890`), chữ số cuối là số kiểm tra Damm: gõ sai 1 số hoặc đảo 2 số cạnh nhau thì app báo ngay. Mã 16 ký tự A–F của máy chủ cũ vẫn dùng được sau khi nhập vào. |
| Bản quyền có chữ ký | Máy chủ ký bản quyền bằng ECDSA P-256; app kiểm tra offline bằng khóa công khai trong APK, nên sửa hay chép bản quyền sang máy khác đều không dùng được. Có mã khóa (`kid`) để đổi khóa. |
| Thời hạn | Số ngày tính từ lúc kích hoạt, đến một ngày cố định, hoặc vĩnh viễn. Gia hạn thêm ngày, đặt ngày mới, chuyển vĩnh viễn. |
| Loại mã | Thường hoặc VIP (VIP không cần VIN). |
| VIN | App gửi VIN khi đọc được. Máy chủ lưu VIN đã mã hóa (AES-GCM) và một mã tra cứu (HMAC); trang quản trị xem được VIN đầy đủ. Có thể tạo mã chỉ dùng cho một xe (VIN phải khớp, trừ mã VIP). |
| Khóa khi nhập sai | Theo thiết bị: 5 lần sai → 15 phút, 10 → 1 giờ, 15+ → 24 giờ. Theo IP: gấp 4 lần số đó. **Mã VIP không vượt qua được khóa này.** Mở khóa bằng một trong 3 cách: chờ hết giờ, admin bấm "Xóa lần sai", hoặc admin cấp mã mở khóa 8 số (dùng 1 lần, hết hạn sau 24 giờ, chỉ cho đúng thiết bị đó). |
| Khôi phục bản quyền | Cài lại app trên cùng thiết bị thì mã thiết bị không đổi. Bấm "Khôi phục bản quyền" để lấy lại bản quyền mà không cần nhập mã. Nếu đã khôi phục cài đặt gốc thì mã thiết bị đổi; admin dùng "Chuyển sang TB mới". |
| Thiết bị | Khóa/mở khóa thiết bị, xóa thiết bị (gỡ bản quyền khỏi nó), chuyển bản quyền sang mã thiết bị mới, đặt tên. |
| Mã | Thu hồi/bỏ thu hồi, gỡ khỏi mọi thiết bị, đổi số thiết bị tối đa, ghi chú, khách hàng, xóa hẳn. |
| Nhật ký | Mọi thao tác admin được ghi lại (VIN chỉ ghi 4 số cuối); lượt nhập mã đúng/sai được giữ 90 ngày. |
| Cập nhật app | `GET /api/update` đọc `update.json` trong GitHub Release mới nhất; APK được tải qua máy chủ, app không cần token GitHub. |
| Gói giọng đọc | `GET /api/model` và `/api/model/file` phục vụ gói Kokoro lấy thẳng từ GitHub Release do workflow phát hành tạo ra, nên không cần R2 và chạy được trên gói Cloudflare miễn phí. Tùy chọn khác: lưu gói trên R2 và đăng ký trong trang quản trị. |

Tất cả API của app nhận cả header mới `X-THT-Request` / `X-THT-License` lẫn header cũ `X-Geely-Request` / `X-Geely-License`.

## Cài lần đầu

Cần máy có Node.js 22 và tài khoản Cloudflare. Chạy trong thư mục gốc repo:

```bash
cd worker
npx wrangler login
npx wrangler d1 create tht-voice-coolray-license  # chép database_id vào wrangler.toml và admin.wrangler.toml
npx wrangler d1 execute tht-voice-coolray-license --remote --file=schema.sql
node scripts/gen-license-key.mjs c1                # in ra khóa ký, khóa VIN và khóa công khai
npx wrangler secret put LICENSE_SIGNING_KEY        # dán giá trị LICENSE_SIGNING_KEY vừa in
npx wrangler secret put VIN_KEY                    # dán giá trị VIN_KEY
npx wrangler secret put ADMIN_PASSWORD             # PIN / mật khẩu quản trị, tối thiểu 8 ký tự
npx wrangler secret put GITHUB_TOKEN               # fine-grained, repo THTVoice, quyền Contents: Read-only (đọc Releases)
npx wrangler deploy
```

Sau khi deploy, Wrangler in địa chỉ dạng `https://tht-voice-license.<tên-tài-khoản>.workers.dev`.

**Cất giữ `LICENSE_SIGNING_KEY` và `VIN_KEY` ở nơi an toàn**, ví dụ trình quản lý mật khẩu. Mất `VIN_KEY` thì không đọc lại được VIN đã lưu. Mất khóa ký thì phải phát hành APK mới có khóa công khai mới.

## Trang cơ bản trên máy chủ bản quyền

- `https://<máy chủ>/`: **Tạo mã kích hoạt**. Nhập mã yêu cầu trên xe, tên khách/xe, ghi chú, thời hạn (7/30/90/180/365 ngày, Vĩnh viễn, hoặc tùy chỉnh 1–3650 ngày), loại mã và PIN quản trị (`ADMIN_PASSWORD`). Thời hạn tính từ lúc xe kích hoạt. Nếu tạo mã mới cho xe đã có mã thì mã cũ bị thay, và xe tự nhận mã mới ở lần kiểm tra kế tiếp.
- `https://<máy chủ>/thiet-bi`: **Quản lý thiết bị**, đăng nhập bằng PIN, phiên dùng được 4 giờ. Gồm danh sách, tìm kiếm, sửa tên/ghi chú, KHÓA / MỞ KHÓA, CẤP LẠI, XÓA LẦN SAI, XÓA.
- `https://<máy chủ>/admin`: trang quản trị đầy đủ.

Đặt `ADMIN_UI = "off"` sẽ tắt cả 3 trang trên máy chủ bản quyền. Chỉ nên làm vậy khi đã dùng trang quản lý riêng bên dưới.

## Trang quản lý riêng

Trang quản lý có thể chạy ở một địa chỉ riêng, ví dụ `https://keythtvoice.<tên>.workers.dev`, và dùng chung cơ sở dữ liệu D1 với máy chủ bản quyền.

1. Mở `admin.wrangler.toml`, thay `REPLACE_WITH_D1_DATABASE_ID` bằng đúng `database_id` đang dùng trong `wrangler.toml`. Muốn đổi địa chỉ thì sửa dòng `name` (ví dụ `keythtvoice`).
2. Chạy các lệnh sau trong thư mục `worker`:
   ```bash
   npx wrangler deploy -c admin.wrangler.toml
   npx wrangler secret put ADMIN_PASSWORD -c admin.wrangler.toml
   npx wrangler secret put VIN_KEY -c admin.wrangler.toml   # cùng giá trị với máy chủ bản quyền
   ```
3. Khi trang riêng đã chạy, tắt `/admin` trên máy chủ bản quyền: đặt `ADMIN_UI = "off"` trong `wrangler.toml` rồi chạy `npx wrangler deploy`.

Tab **Cấp mã**:
- **Tạo mã cho một xe:** nhập mã yêu cầu (mã thiết bị trên màn kích hoạt), tên xe hoặc khách, ghi chú, chọn thời hạn (Vĩnh viễn / 30 / 90 / 365 ngày / Tùy chỉnh). Mã tạo ra chỉ dùng được cho đúng xe đó. Khách nhập mã hoặc chỉ cần bấm **Khôi phục bản quyền**. Thời hạn tính từ lúc kích hoạt.
- **Tạo mã rời để bán sau:** để trống mã yêu cầu và chọn số lượng.
- **XOAY KEY / CẤP LẠI:** tạo mã mới cho xe đang có bản quyền, giữ nguyên hạn dùng, và thu hồi mã cũ. Xe tự nhận mã mới ở lần kiểm tra máy chủ kế tiếp.

## Khách sắp hết hạn

- Tab **Tổng quan**: bấm ô "Sắp hết hạn (30 ngày)" hoặc "Đã hết hạn (30 ngày qua)" để mở danh sách tương ứng.
- Tab **Mã kích hoạt** → Trạng thái: "Sắp hết hạn (30 ngày)" (gần hết trước) hoặc "Đã hết hạn (90 ngày qua)".
  Mỗi mã hiện "còn N ngày" / "đã hết hạn N ngày", có nút **+30 ngày**, **+1 năm** (mã đã hết hạn tính lại từ hôm nay)
  và **Nhắc khách** để chép tin nhắn nhắc gia hạn.
- Các danh sách (mã, thiết bị, khóa, lượt thử, nhật ký, trang /thiet-bi) chia trang 20/50/100 mục.
- Mẫu tin nhắn sửa ở mục "Mẫu tin nhắc gia hạn" ngay dưới ô tìm; chỗ trống `{khach}`, `{ma}`, `{ngay}`, `{con}` tự điền.

## Khóa theo VIN

- **Tự gắn VIN:** mã thường cho 1 xe (tối đa 1 thiết bị) tự gắn VIN của xe ở lần kích hoạt hoặc lần kiểm tra
  đầu tiên đọc được VIN. Mã nhiều thiết bị và mã VIP không tự gắn. Trên admin, VIN tự gắn có nhãn "tự gắn".
- **Kiểm tra mỗi lần xe kiểm tra key:** VIN đọc được mà khác VIN của mã thì ghi vào thiết bị
  ("Không khớp VIN của mã: N lần").
- **Thiết bị → "Khi xe báo VIN khác VIN của mã":**
  - *Chỉ ghi lại (mặc định):* không chặn ai.
  - *Chặn xe đó:* trả `device_blocked` (app hiện tại dừng). Xe báo đúng VIN hoặc đổi lại "Chỉ ghi lại" thì
    lần kiểm tra kế tiếp dùng lại được.
- Không đọc được VIN thì không bao giờ bị chặn; mã VIP không kiểm tra VIN.
- VIN do admin tự nhập (nút **VIN** ở mã) giữ như cũ: lúc nhập mã kích hoạt phải đúng xe đó.
- **Tổng quan → "VIN bất thường"** / **Thiết bị → Lọc "VIN bất thường":** xe không khớp VIN, đổi VIN, hoặc
  7 ngày không đọc được VIN. Xem danh sách này vài tuần trước khi bật chặn.

## Phiên bản app và thông báo cho khách

- **Tổng quan → Phiên bản app trên xe:** số xe theo từng phiên bản (xe online trong 30 ngày), "Đã lên bản mới
  nhất: x/y xe", bấm **Xem xe** để mở danh sách thiết bị của bản đó. "Bản mới nhất" và nội dung cập nhật lấy
  từ `update.json` của GitHub Release mới nhất (máy chủ bản quyền đọc GitHub, lưu bản sao trong D1 mỗi 10 phút
  khi có xe kiểm tra cập nhật và mỗi ngày một lần, nên trang quản lý riêng cũng thấy).
- **Công cụ → Thông báo cho khách:** nội dung, gửi cho tất cả xe, chỉ xe dưới bản X, hoặc chỉ một mã thiết bị (để thử), hiện đến ngày, bật/tắt.
  Máy chủ gửi kèm trong lần kiểm tra bản quyền (`notice: {id, text, until}`); app 1.0.6+ hiện một lần trong màn
  hình chính khi xe đứng yên (số P, tốc độ 0), không đè app khác, không đọc ra loa. Sửa nội dung → hiện lại một lần.
  Nút **Gửi thông báo** / **Tắt thông báo**; "Đã gửi tới N xe · Đã xem M xe" và danh sách xe đã nhận (app gửi
  `X-THT-Notice-Seen` khi khách đã bấm Đã xem; xe bản cũ có thể "đã nhận" nhưng không hiện được).
  App cũ bỏ qua trường này.

## Sao lưu / khôi phục

Admin → **Công cụ** → **Sao lưu / khôi phục dữ liệu**.
- **Tải bản sao lưu:** tệp `.json` gồm mã, thiết bị, kích hoạt, mã mở khóa, cài đặt và 5000 dòng nhật ký gần nhất
  (không gồm lượt thử 90 ngày và khóa nhập sai). VIN vẫn mã hóa, chỉ đọc lại được trên máy chủ cùng `VIN_KEY`.
- **Khôi phục:**
  - *Gộp* — giữ dữ liệu đang có, thêm dòng còn thiếu (dùng khi chuyển sang tài khoản/máy chủ mới).
  - *Thay toàn bộ* — tự tải bản sao lưu hiện tại về trước, phải gõ `THAY THẾ` để xác nhận.
- Muốn xe dùng được trên máy chủ mới: giữ nguyên `VIN_KEY`, `LICENSE_SIGNING_KEY` (hoặc thêm khóa công khai mới
  vào app), và địa chỉ `serverUrl` mà app đang gọi.
- Ngoài ra Cloudflare D1 có **Time Travel** (khôi phục database về thời điểm trong 30 ngày).

## Trỏ app sang máy chủ mới

Sửa `config/license-server.properties` rồi build lại APK:

```properties
serverUrl=https://tht-voice-license.<tên-tài-khoản>.workers.dev
publicKeys=k1:MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...
```

Dòng `publicKeys` là dòng cuối mà `gen-license-key.mjs` in ra. Dòng này là khóa công khai nên commit được. Khi `publicKeys` còn trống, app chạy chế độ máy chủ cũ (không kiểm tra chữ ký).

## Chuyển khách từ máy chủ cũ

1. Xuất danh sách mã đang bán từ máy chủ cũ. Cần mã 16 ký tự, mã thiết bị đã kích hoạt, ngày hết hạn, VIP hay không.
2. Mở `https://<máy chủ mới>/admin` → **Công cụ** → **Nhập mã từ máy chủ cũ**. Mỗi dòng có dạng:
   `ABCD-EF01-2345-6789,0123-4567-89AB-CDEF,2027-09-30,vip,Anh A`
   Để trống ô ngày nếu mã chưa kích hoạt và tính theo ngày. Ghi `0` nếu mã vĩnh viễn.
3. Phát hành APK trỏ sang máy chủ mới.
   - Máy đã kích hoạt: tự đổi bản quyền cũ sang bản quyền có chữ ký ở lần kiểm tra máy chủ kế tiếp, không cần nhập lại mã.
   - Mã cũ chưa gắn thiết bị: gắn vào thiết bị đầu tiên dùng nó.
4. Giữ máy chủ cũ chạy thêm một thời gian cho các máy chưa cập nhật APK.

## Gói giọng đọc

Workflow **Release candidate APK** (bật `publish`) tự đính kèm gói giọng đọc và `voice-pack.json` vào GitHub Release. App bấm **Tải về** là máy chủ tìm đúng gói theo SHA-256 trong các Release rồi chuyển tiếp tệp. Chỉ cần secret `GITHUB_TOKEN`, không phải làm thêm gì.

### Gói phản hồi MP3 cho Coolray 2025

Nút **Tải gói** dùng `GET /api/voice/ngoc-huyen` hoặc `GET /api/voice/diem-trinh`, sau đó tải tệp bằng cùng license qua `/file?sha256=…`. Gói đã tải được lưu trên đầu xe để phát ngoại tuyến.

Worker mặc định đọc release `voice-mp3-packs` của `meomeo2099/THTVoice-Coolray2025`. Có thể đặt `MP3_GITHUB_REPO` và `MP3_RELEASE_TAG` để đổi nguồn. Khi repo MP3 là riêng tư, dùng `MP3_GITHUB_TOKEN` với quyền Contents Read trên repo đó, hoặc cấp quyền đọc tương ứng cho `GITHUB_TOKEN` hiện có. Đặt token dưới dạng secret trên Worker. Máy chủ dùng `GITHUB_REPO` hiện có cho OTA và Kokoro.

Muốn dùng R2 thay cho GitHub thì bỏ dấu `#` ở khối `[[r2_buckets]]` trong `wrangler.toml`, tải tệp lên R2 và đăng ký trong trang quản trị (**Công cụ** → **Gói giọng đọc**).

## Đổi khóa ký

1. Chạy `node scripts/gen-license-key.mjs k2`.
2. Thêm khóa công khai mới vào `publicKeys`, giữ khóa cũ: `publicKeys=k1:...,k2:...`.
3. Phát hành APK và chờ khách cập nhật.
4. Đổi secret `LICENSE_SIGNING_KEY` sang khóa mới, đặt `LICENSE_KEY_ID = "k2"` trong `wrangler.toml`, rồi deploy.

Bản quyền đã ký bằng `k1` vẫn hợp lệ đến lần kiểm tra máy chủ kế tiếp; lần đó app nhận bản quyền ký bằng `k2`.

## API cho app

| Đường dẫn | Mô tả |
|---|---|
| `POST /api/verify` | `{request_code, activation_code, vin?, app_version?}` → `{ok, token, activation_code, license_type, expires_at, server_time}` |
| `GET /api/status` | Header license (+ `X-THT-Vin` tùy chọn) → như trên, hoặc `device_blocked` / `license_revoked` / `license_moved` / `license_not_found` / `expired` |
| `POST /api/restore` | `{request_code}` → bản quyền đang gắn với thiết bị |
| `POST /api/unlock` | `{request_code, unlock_code}` → xóa khóa nhập sai |
| `GET /api/update`, `/api/update/apk` | Cập nhật app (cần license) |
| `GET /api/model`, `/api/model/file` | Gói giọng đọc (cần license) |
| `GET /api/health` | Kiểm tra máy chủ |

Khi bị khóa, máy chủ trả mã 429 kèm `retry_after_ms`. `/api/stt` và `/api/llm` trả 410 vì THT Voice chạy offline.

## Một tệp worker.js (sửa trên Dashboard)

Dùng `python3 worker/scripts/bundle-single-file.py` để gộp `src/` thành một tệp `worker/dist/worker.js`. Có thể dán tệp này vào Cloudflare Dashboard → Workers & Pages → tht-voice-license → **Edit code** rồi bấm **Deploy**. Cách này giữ nguyên các binding D1, biến và secret đang có.

## Kiểm thử

```bash
cd worker && npm test     # Node 22; dùng SQLite trong bộ nhớ thay cho D1
```

CI "Android build" chạy bộ test này ở mỗi PR.

## Chặn bản cài bị ký lại (RELEASE_CERT_SHA256)

Bản release gửi header `X-THT-Cert` (SHA-256 chứng chỉ ký của APK đang chạy). Đặt biến
`RELEASE_CERT_SHA256` cho Worker (Settings → Variables and Secrets, loại Text) bằng đúng giá trị
secret `THT_SIGN_CERT_SHA256` trên GitHub (có hoặc không dấu `:`; nhiều giá trị cách nhau
bằng dấu phẩy khi đổi khóa). Khi đã đặt, máy chủ trả 403 `tampered` cho mọi lời gọi `/api/*` từ bản
bị ký lại. Không đặt biến, hoặc bản build không gửi header (bản test, bản cũ) → không kiểm tra.

Trong app, bản release cũng tự so chữ ký lúc chạy (`AppSignature`); sai chữ ký → màn kích hoạt báo
"Bản cài không chính thức" và trợ lý không chạy. Bản debug/hardware-test không kiểm tra.


**Đổi khóa ký (16/10/2026, Coolray chuyển sang bộ secret `THT_SIGN_*`):** trong lúc các xe còn chạy bản ký
khóa cũ, đặt `RELEASE_CERT_SHA256` = `<sha256 khóa mới>,<sha256 khóa cũ>` để bản cũ vẫn kích hoạt / kiểm tra key
được. Khi mọi xe đã cài lại bản khóa mới thì bỏ giá trị cũ. Xe bản khóa cũ không tự cập nhật sang bản khóa mới
được (Android từ chối APK khác chữ ký): phải gỡ app rồi cài bản mới một lần.
