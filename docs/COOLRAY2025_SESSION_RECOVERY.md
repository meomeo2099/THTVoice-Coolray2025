# Kết thúc phiên bị kẹt và kiểm soát kết quả nhận dạng

Trạng thái: đã triển khai phần mềm, **UNVERIFIED trên xe thật**.

## Vấn đề

- Watchdog trước đây chỉ kiểm tra tuổi phiên khi bấm lại nút Voice.
  Nếu ASR, đọc trạng thái xe hoặc TTS không trả về, popup có thể đứng ở
  “Đang xử lý” và không tự ẩn.
- Bộ ASR buffered-offline vẫn giải mã đoạn im lặng khi hết thời gian nghe.
  Nếu giải mã lại thất bại, bản cũ giữ kết quả của đoạn trước khi người nói tạm dừng.

## Thay đổi

- Mỗi phiên có timer riêng 35 giây, dùng Handler của service; không cần người dùng bấm lại nút.
  Giới hạn lệnh đơn 15 giây được giữ nguyên.
- Khi hết hạn, hủy phiên, dừng mic/TTS, trả lại âm lượng và hiển thị lỗi.
  Popup lỗi tự ẩn theo lựa chọn hiện tại (mặc định 1 giây).
- Luồng native chưa chịu dừng được giữ riêng đến khi thoát. App không chạy phiên mới trên
  cùng engine/front end đang bị giữ khóa; nếu còn bận sẽ báo đang khôi phục.
  Trường hợp native không bao giờ trả về có thể vẫn cần khởi động lại ứng dụng.
- Kết quả từ phiên đã hết hạn không được đọc hoặc cập nhật trạng thái sau đó.
- Nếu worker kết thúc mà popup vẫn ở “Đang xử lý”, chuyển sang lỗi và tự ẩn.
- Không giải mã đoạn chưa đủ thời lượng tiếng nói tối thiểu; im lặng hoặc tiếng động ngắn
  không được xem là một câu lệnh. Khi giải mã lỗi, không dùng lại câu của lần giải mã trước.
- Kết quả cuối rỗng không được thay bằng câu partial cũ.
- Thời gian nghe dùng đồng hồ monotonic, không bị kéo dài khi giờ hệ thống thay đổi.

Các thay đổi này chưa chứng minh độ chính xác nhận dạng trên mic xe đã cải thiện.
Cần đối chiếu câu người dùng nói với câu ASR nhận được; không tự thêm alias hoặc sửa số
nhiệt độ/kính khi chưa có ví dụ cụ thể.

## Kiểm thử

- SessionDeadlineTest: tự hết hạn không cần nút, hủy timer, callback cũ sau phiên mới,
  callback trùng và callback đã được đưa vào hàng đợi trước khi hủy.
- OfflineEndpointTrackerTest: im lặng, nền nhỏ hơn ngưỡng, tiếng động ngắn, tiếng nói đủ
  thời lượng, câu tiếp sau dấu nối và giới hạn bộ đệm.
- VoiceOverlayStateMachineTest: phiên kết thúc không giữ spinner; không ghi đè kết quả/lỗi đã có.

Trên xe, thử một câu ngắn, một câu không hỗ trợ, một phiên không nói gì và một phiên sau lỗi.
Nếu nhận sai từ, ghi lại chính xác câu đã nói và chữ app hiển thị. Không cần bật ghi WAV
để thử các ca trên.
