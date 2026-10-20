# THT Voice — Phase 10 Security / VIN / Overlay hardening

Mục tiêu của mốc này là loại bỏ mọi UI kích hoạt kiểu dialog còn sót và giữ một gate duy nhất: `ActivationActivity`.

## Security gate

- `ActivationActivity` là launcher duy nhất.
- `MainActivity` chỉ dựng UI khi license đang `ACTIVE`.
- Nếu license hết hạn, bị khóa từ xa, clock invalid hoặc binding mismatch trong lúc app đang mở: stop Voice core, ẩn overlay, chuyển về `ActivationActivity`, rồi finish `MainActivity`.
- Không còn `showActivationDialog()` trong `MainActivity`.
- Các entry point Voice vẫn phải tự kiểm tra license; không tin rằng caller đã kiểm tra.

## VIN

- Chỉ đọc `VehiclePropertyIds.INFO_VIN` qua Android Automotive API chuẩn.
- Chỉ chấp nhận VIN hợp lệ 17 ký tự theo `VinRules`.
- VIN unavailable phải ghi reason để test xe thật, không suy đoán raw property ID khác.
- VIN vẫn là telemetry trong Phase 10; chưa dùng làm điều kiện bắt buộc cho license cho đến khi có hardware evidence.

## Overlay

- Overlay tự fail-closed nếu license không còn ACTIVE.
- Không render popup chỉ vì service/command gọi nhầm sau khi license invalid.
- Log overlay chỉ ghi trạng thái kỹ thuật, không ghi nội dung transcript nhạy cảm.

## Hardware evidence cần thu

1. Cold start chưa license: chỉ Activation UI, không Voice service.
2. License ACTIVE: Main UI + Voice service hoạt động.
3. Làm license invalid trong lúc Main đang foreground: quay về Activation UI, Voice service dừng, overlay biến mất.
4. ECARX khi chưa license: mở Activation UI, không nghe mic.
5. VIN: ghi `AVAILABLE` hoặc reason `UNAVAILABLE`; chưa bật VIN-required binding.
6. Overlay: không còn hiển thị sau khi gate đóng.

Chỉ sau test IHU689G thật mới được đánh dấu hardware-confirmed.
