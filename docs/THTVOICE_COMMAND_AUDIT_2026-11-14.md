# Đối chiếu câu lệnh THTVoice cho Coolray 2025 — 14/11/2026

Nguồn: [THTVoice0c48c6e](https://github.com/meomeo2099/THTVoice/tree/0c48c6e17a7bb37bca636472593f2ee915392bce). Bản Coolray trước sửa:7aecfccfdacc51ba916a9221d1d21b281d1f1468 (1.0.16).

## Kết quả
CSV nguồn và CSV hiện có đều có3230dòng; toàn bộ câu, intent và tham số đều đã có. Không sao chép thêm một bộ CSV trùng. So sánh thêm parser và test phát âm/nhận sai để tìm thiếu sót thực tế.

| Kết quả parser và capability | Trước | Sau |
|---|---:|---:|
| Có command thuộc nhóm đang hỗ trợ hoặc lệnh Android/local |2473|2553|
| Có command nhưng nhóm chức năng đang tắt |257|257|
| Chưa có command trong parser không ngữ cảnh |500|420|

Tăng80mẫu được parser nhận vào nhóm hỗ trợ. Đây là thống kê parser/capability, không phải tỷ lệ ASR trên mic xe hay xác nhận tất cả hành vi đúng trên xe. Các câu chỉ đầy đủ nghĩa sau câu trước, câu cần hỏi lại hoặc nhãn dữ liệu cũ không được coi là lỗi parser chỉ vì chúng không trả command độc lập.

## Đã bổ sung
Bộ91mẫu câu kiểm thử ở [THTVOICE_COMMAND_SUPPLEMENT.tsv](reference/voice/THTVOICE_COMMAND_SUPPLEMENT.tsv). Một số biến thể đã được nhận trước đó nhưng giữ trong bộ test để kiểm tra đúng ý nghĩa.

- Điều hòa: cho mát xe đi; làm mát xe ngay; nóng quá bạn ơi; lạnh cóng; cho ấm xe lên. Quy về bật/tắt điều hòa có sẵn, không thêm sưởi.
- Nhiệt độ: lạnh quá tăng độ lên; nâng nhiệt lên tí; nóng quá giảm độ; cho mát thêm; hạ độ xuống. Mỗi lần không nói số vẫn0,5°C.
- Quạt: mức gió cực đại; gió nhỏ nhất; gió nhẹ nhất; bậc giáo ba/bậc gió ba/bật giáo ba. Mức hợp lệ1..7.
- Lấy gió: trong xe có mùi; xe bí quá; ngột ngạt quá; hút mùi ra; bí bách quá → gió ngoài.
- Sấy và làm lạnh nhanh: sấy giúp tôi kính lái; điều hòa làm mát nhanh → handler sấy/MaxAC hiện có.
- Hé cửa kính một chút/hạ hé cửa sổ → hé10% theo quy tắc hé kính đã chốt. Đóng kính còn lại/đóng luôn kiếng còn lại → hỏi kính nào; không tự chọn hoặc đóng toàn bộ.
- ODO: tổng số km đã đi; số km đã đi; tổng km đã đi. Hỏi mức quạt/nhiệt độ điều hòa bằng cách nói tự nhiên.
- Ứng dụng/media: bản đồ Waze/Google, dẫn đường, mở cho tôi một bài nhạc, xem video, mở xì mát túp, VTVGo/FPTPlay/VieON/TV360, Gofa, quản lý file, Kiki, lướt web. Giữ bộ chọn ứng dụng và tìm app đã cài; không chép package xe khác hay giả báo thành công khi app chưa cài.
- Vietmap Live: viên map li/việc map lie/viết mép li/map live…; chuẩn hóa riêng ô tên app, chọn ứng dụng đích danh trước chữ map/bản đồ chung. Tên địa điểm và tên bài hát giữ nguyên. Cách đọc Vietmap Live: việt mép lai.

## Còn thiếu hoặc cố ý chưa bật
- Kịch bản làm mát thoải mái, hẹn giờ điều hòa/lịch đèn và gỡ kẹt màn hình: cần handler/kịch bản hoặc hợp đồng THTSplit riêng; không ánh xạ sang một thao tác có nghĩa khác.
- Bật/tắt Wi-Fi: parser hiện cố ý phân biệt mở cài đặt Wi-Fi với bật/tắt radio. Chưa coi OpenWifiSettings là đã bật mạng.
- Các chức năng pin/regen/sạc/cốp trước, khóa cửa và tính năng không có mapping Coolray: không bật bằng cách thêm alias.
- Câu cửa bên lái/bên phụ không được tự hiểu là kính theo quyết định trước. Câu kính còn lại cần hỏi vị trí. Các câu nói tiếp ngắn được xét bởi FollowUpContext, không phải lệnh độc lập.
- Không chép các nhận nhầm mơ hồ anh nhiệt độ/cảm giác độ/tắt âm hơn thành lệnh tác động xe. Các tên camera/cài đặt xe chưa có đích ứng dụng rõ ràng không được gán package tùy ý.

## Kiểm chứng
RED206tests/3failures tái hiện thiếu alias, ô tên Vietmap và câu hỏi kính còn lại. GREEN206tests sau sửa; gồm toàn bộ kiểm tra corpus3230dòng, cửa kính, điều hòa, AUTO và lifecycle hiện có. Test mới kiểm tra91mẫu cùng các trường hợp tên địa điểm/app, prefix mở ứng dụng, ứng dụng đích danh và giới hạn capability. Rà soát độc lập không có phát hiện cần sửa.

Sherpa1.13.8, mô hình65M, mic, backend xe, bước0,5°C, quạt1..7 và Worker được giữ nguyên. Chưa có thử xe thật cho các biến thể mới.

## Signed candidate and backup
- Candidate: 1.0.17 / versionCode 18; package `com.tht.voice.coolray2025`.
- Source commit: `853e3c1143a6fc63e4f2e9399ae1e42481d70b5d`.
- CI: https://github.com/meomeo2099/THTVoice-Coolray2025/actions/runs/43545998093 (success; offline assets, SQLite, unit tests, release assembly and pinned THT_SIGN verification passed).
- APK artifact: https://github.com/meomeo2099/THTVoice-Coolray2025/actions/runs/43545998093/artifacts/14094307336 (`THTVoice-rc-1.0.17-18`, expires 2026-11-15 02:28:56 UTC).
- APK SHA256: `1b6fa68219a52d5aa99712645d3fec3dd57ad950491fd16ab076af7dffd48369`.
- GitHub Release/OTA publication skipped. Temporary push trigger restored to the original manual-only workflow in the follow-up backup commit.
- Vehicle verification remains pending for new utterances.
