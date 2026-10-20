# THT Voice — Security / Activation UI

> Trạng thái: **APPROVED / AUTHORITATIVE VISUAL + FLOW**
>
> Mockup đã chốt: `docs/ui/SECURITY_ACTIVATION_UI_APPROVED.jpg`.

## Mục tiêu

Màn kích hoạt là **security gate toàn màn hình**. Khi license chưa hợp lệ, người dùng không được đi vào navigation 6 menu và Voice core không được khởi động.

## Luồng bắt buộc

```text
Mở THT Voice
      ↓
Kiểm tra local license + binding
      ↓
License hợp lệ? ── Có ──> mở UI chính + cho phép Voice core khởi động
      │
      Không
      ↓
Security / Activation full-screen
      ↓
Hiển thị mã thiết bị + thông tin binding có thể xác minh
      ↓
Nhập mã XXXX-XXXX-XXXX-XXXX
      ↓
Xác thực server
      ↓
Thành công -> lưu license -> mở UI chính -> khởi động Voice core
Thất bại   -> vẫn ở màn activation, không bypass
```

## Quy tắc khóa

Khi chưa kích hoạt / license hết hạn / bị khóa / binding sai:

- Không khởi động KWS `Hey THT`.
- Không nhận nút Voice trên xe.
- Không cho bấm mic để chạy ASR.
- Không điều khiển xe.
- Không mở app/media/navigation qua Voice.
- Không cho truy cập màn Apps để dùng launcher như đường vòng.
- Không tạo navigation shell 6 menu phía sau activation screen.
- Không có nút `Đóng` để quay vào app chưa kích hoạt; chỉ cho thoát ứng dụng hoặc tiếp tục kích hoạt.

## Bố cục visual

- Landscape 1920×1080, dark automotive, cyan/blue accent.
- Cột trái: hero THT Voice + hình xe trung tính.
- Cột phải: card `KÍCH HOẠT ỨNG DỤNG`.
- Hiển thị:
  - trạng thái binding,
  - VIN khi backend đọc được thật,
  - định danh thiết bị,
  - request code dạng `XXXX-XXXX-XXXX-XXXX`,
  - ô nhập activation code tự chèn dấu `-`,
  - nút `KÍCH HOẠT`,
  - trạng thái `Đang xác thực`, `Thành công`, `Mã không hợp lệ`, `Hết hạn / Bị khóa`.
- Touch target lớn, không scroll ở bố cục mặc định 1920×1080.

## VIN binding

Mục tiêu cuối cùng:

```text
VIN
 + Device fingerprint
 + Android Keystore key identity
        ↓
   binding hash
        ↓
 server-signed license
```

### Ràng buộc kỹ thuật

- VIN **không phải bí mật** và không được dùng một mình làm khóa license.
- Không hard-code VIN.
- Không đoán OEM/VHAL property ID.
- Nguồn VIN theo thứ tự (29/09/2026):
  1. Android Car chuẩn `VehiclePropertyIds.INFO_VIN`.
  2. Dự phòng chỉ đọc: system property nền tảng head unit `sys.ecarx.vin` (`REFERENCE`/`UNVERIFIED` tới khi đọc được trên xe).
  Màn kích hoạt ghi nguồn đọc được, ví dụ `… (Android Car)` hoặc `… (thuộc tính hệ thống)`.
- VIN phải qua chuẩn hóa và kiểm tra 17 ký tự; các ký tự I/O/Q bị loại theo quy tắc VIN chuẩn.
- Nếu Car service/property/quyền/VIN không khả dụng, runtime trả `Unavailable`; UI chỉ hiện `Chưa khả dụng`.
- VIN hiện chỉ là telemetry trên màn kích hoạt, **chưa tham gia quyết định license** (`VinBindingPolicy.VIN_REQUIRED_ENABLED = false`).
- Khi kích hoạt license thường, app lưu **hash** SHA-256 của VIN + mã thiết bị (nếu đọc được) trong bản ghi mã hóa Keystore. Theo quyết định chủ repo (30/09/2026), VIN chỉ định danh chiếc xe nên app **gửi VIN** lên máy chủ khi kích hoạt/khôi phục/kiểm tra trạng thái; máy chủ mới lưu VIN mã hóa + mã tra cứu HMAC, admin xem được VIN đầy đủ (`worker/README.md`). Khi bật VIN-required, license thường phải có hash khớp VIN hiện tại, nếu không → `BINDING_MISMATCH`.

## Mã VIP (không cần VIN)

- Máy chủ kích hoạt quyết định loại mã: phản hồi `api/verify` (và `api/status`) có thêm `"license_type": "vip"`; thiếu hoặc giá trị khác = `standard`. App **không** tự nhận VIP từ dạng mã nhập — mã VIP vẫn là 16 ký tự `XXXX-XXXX-XXXX-XXXX`.
- License VIP: không đọc VIN, không lưu hash VIN, luôn qua kiểm tra VIN kể cả khi đã bật VIN-required. Vẫn gắn với mã thiết bị, vẫn có hết hạn và khóa từ xa như license thường.
- Trạng thái hiển thị: `Đã kích hoạt VIP • …`; kích hoạt thành công: `Kích hoạt VIP thành công`.
- **Việc phía máy chủ (Cloudflare Worker, không nằm trong repo):** cho phép tạo mã VIP và trả `license_type` trong 2 endpoint trên. Worker cũ không trả trường này thì mọi mã vẫn là `standard`, app chạy bình thường.
- Chỉ bật **VIN-required binding** sau khi test thật chứng minh head unit đọc VIN ổn định.
- Trước mốc đó vẫn giữ gate bằng DeviceIdentity + Keystore + server license hiện có.

## Mockup và branding

`SECURITY_ACTIVATION_UI_APPROVED.jpg` là reference cho **layout, hierarchy, density và flow**. Một số chữ/chi tiết xe trong ảnh sinh mẫu có thể là placeholder.

Implementation production phải tuân theo UI baseline chung:

- Branding chính: `THT Voice`.
- Không dùng tên/logo/badge hãng xe trong UI người dùng.
- Không hard-code model xe trong branding.
- VIN hiển thị phải là dữ liệu runtime đã đọc thật; nếu chưa có thì hiển thị `Chưa khả dụng`.

## Trạng thái UI tối thiểu

1. `UNLICENSED` — chờ nhập mã.
2. `VERIFYING` — khóa nút, hiển thị tiến trình.
3. `ACTIVE` — xác thực thành công, chuyển vào app.
4. `INVALID_CODE` — báo lỗi, cho nhập lại.
5. `EXPIRED` — hết hạn, không mở app chính.
6. `REMOTE_BLOCKED` — bị khóa từ xa, không mở app chính.
7. `BINDING_MISMATCH` — license không thuộc thiết bị hiện tại.
8. VIN unavailable — telemetry độc lập; không giả mạo trạng thái thành công/thất bại của license.

## Giao diện 30/09/2026

Màn kích hoạt dùng chung bộ giao diện mới (`ThtUi`): bên trái ảnh đêm + icon THT Voice mới + 3 điểm nổi bật; bên phải thẻ "Kích hoạt ứng dụng" (chip trạng thái, VIN, thiết bị, mã thiết bị + Sao chép, ô mã kích hoạt, nút Kích hoạt / Thoát ứng dụng). Luồng và quy tắc khóa không đổi.

## Trạng thái triển khai hiện tại

- [x] `ActivationActivity` là launcher duy nhất.
- [x] `MainActivity` không còn exported/launcher.
- [x] `ActivationScreenView` full-screen đã tách khỏi dialog cũ.
- [x] Chưa license thì launcher không mở navigation 6 menu.
- [x] VoiceAssistantService bị chặn khi gate chưa mở.
- [x] Nút Voice khi chưa license chuyển về ActivationActivity.
- [x] Mã nhập tự format 4-4-4-4.
- [x] Có state model local: UNLICENSED / ACTIVE / EXPIRED / REMOTE_BLOCKED / BINDING_MISMATCH / CLOCK_INVALID.
- [x] Có unit test pure Kotlin cho gate evaluator.
- [x] VIN provider fail-safe dùng `INFO_VIN`, không đoán raw/OEM property.
- [x] VIN rules có unit test; UI chỉ hiển thị VIN đã validate.
- [x] Đồng bộ với app: MainActivity hết license → dừng Voice core, về ActivationActivity (không còn dialog kích hoạt cũ có nút `Đóng`).
- [x] Voice core kiểm tra lại gate ở mỗi trigger (wake word, nút Voice, mic, mở lại xác nhận an toàn).
- [x] Kiểm tra máy chủ nền tối đa 12 giờ/lần (`LicenseSessionPolicy`); offline không khóa license hợp lệ; `REMOTE_BLOCKED` khóa ở trigger kế tiếp.
- [x] Màn chính: bấm trạng thái license → hộp thoại "Bản quyền THT Voice" (trạng thái, mã thiết bị, `Kiểm tra lại`) — chỉ khi đã kích hoạt.
- [x] Màn kích hoạt dùng `ResponsiveLayout` (`ActivationLayoutPlan`): 1920×1080 không đổi; màn thấp thu nhỏ hình xe + cuộn; màn dọc/hẹp xếp chồng, ẩn hình xe, cuộn.
- [x] VIN dự phòng qua system property nền tảng (`FallbackVinProvider`), test JVM.
- [x] Loại license `standard`/`vip` + `VinBindingPolicy` (VIP không cần VIN) + hash VIN khi kích hoạt, test JVM.
- [ ] Worker: tạo mã VIP và trả `license_type`.
- [ ] Xác nhận `INFO_VIN`/quyền đọc VIN trên IHU689G thật.
- [ ] VIN-required binding sau hardware validation.
- [ ] Test thật IHU689G: cold start, offline restart, expired license, blocked license, reinstall/rebind, VIN read nếu khả dụng.

## Máy chủ bản quyền mới (30/09/2026)

Code máy chủ nằm trong `worker/` (hướng dẫn deploy + chuyển khách: `worker/README.md`). App chọn máy chủ lúc build qua `config/license-server.properties`:
- `publicKeys` trống → chế độ máy chủ cũ (mã 16 ký tự, không kiểm tra chữ ký) — mặc định cho tới khi chủ repo deploy.
- `publicKeys` có giá trị → bản quyền phải có chữ ký ECDSA P-256 của máy chủ cho đúng mã thiết bị (`LicenseToken`); bản ghi cũ chưa có chữ ký vẫn chạy tới lần kiểm tra máy chủ đầu tiên rồi được đổi sang bản có chữ ký.

Màn kích hoạt:
- Ô mã mở bàn phím chữ + số (in hoa, tắt gợi ý) để nhập được cả mã 16 ký tự 0-9/A-F của máy chủ hiện tại lẫn mã 20 số của máy chủ mới; mã 20 số có chữ số kiểm tra Damm (`LicenseCodes`) → gõ sai 1 số / đảo 2 số báo ngay, không tính lần sai. (Bản 30/09 sáng mặc định bàn phím số khiến không gõ được chữ A-F — đã bỏ.)
- Nhập 8 số = mã mở khóa khi bị khóa do nhập sai (5 lần → 15 phút, 10 → 1 giờ, 15+ → 24 giờ; mã VIP không vượt khóa).
- Nút **Khôi phục bản quyền**: cài lại app trên cùng thiết bị, hoặc sau khi người bán gia hạn.
- Máy chủ báo `license_moved` / `license_not_found` → xóa bản quyền cục bộ; `license_revoked` / `device_blocked` → khóa từ xa (mở lại được khi admin bỏ khóa).

