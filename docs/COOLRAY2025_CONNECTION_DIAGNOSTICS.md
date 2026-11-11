# Coolray 2025 — lỗi kết nối trong bản 1.0.3

Ảnh trên xe do chủ repo cung cấp cho thấy lệnh mở Bản đồ và YouTube thành công,
còn tăng nhiệt độ và hạ kính thất bại. Dòng kết nối xe hiển thị `Có lỗi I/O`.
Chủ repo xác nhận phiên bản 1.0.3. Chưa có log exception trực tiếp từ xe.

## Đối chiếu Android 9

Nguồn là `cau_hinh/system_framework/arm64/boot-android.car.vdex` của bộ dump,
SHA-256 `1107babc4b5fd9d6cec2a114adcc6da089d95e0de73f4aa52546d241a1fea81f`.
Đọc bảng method/prototype CompactDex cho `android.car.hardware.property.CarPropertyManager`
cho thấy có `getPropertyList()`, `getIntProperty(int,int)`, `getFloatProperty(int,int)`,
`getBooleanProperty(int,int)` và các setter tương ứng. Không có `getCarPropertyConfig(int)`.
Adapter lấy giới hạn property qua `getPropertyList()` để tương thích API có trên xe.

Việc này giải quyết lời gọi API thiếu khi truy vấn giới hạn. Chưa đủ chứng cứ để
kết luận đó là nguyên nhân mọi lệnh nhiệt độ và kính thất bại. Các getter mà hai nhóm
lệnh sử dụng vẫn có trong thư viện trên xe.

## Chẩn đoán trên đầu xe

Bản 1.0.4 giữ tối đa 16 lỗi VHAL/ECARX trong bộ nhớ phiên đang chạy, gồm thao tác,
ID, area và loại exception cùng thông báo giới hạn độ dài. Không ghi giá trị
property, VIN, câu lệnh giọng nói hoặc license. Lỗi reflection được mở lớp
`InvocationTargetException` để hiện nguyên nhân bên trong.

Thử lại một lệnh đang lỗi rồi chạm dòng **Kết nối xe** ở màn Trợ lý hoặc vào
**Cài đặt → Kết nối xe → Chẩn đoán**. Sao chép báo cáo hoặc chụp phần lỗi để phân
biệt thiếu quyền, Binder chưa sẵn sàng, API không tương thích và lỗi property.
Trang này đọc trạng thái sẵn có và quyền Android, không tự gửi lệnh điều khiển xe.

Giao diện chỉ tô trạng thái kết nối màu xanh khi enum là `CONNECTED`.
`SecurityException` hiện thiếu quyền, lỗi liên kết API hiện không tương thích,
các I/O khác tiếp tục báo lỗi. Một lần đọc thành công tiếp theo không xoá lịch sử lỗi.

## Trạng thái xác nhận

Đối chiếu API dựa trên thư viện gốc. Kiểm thử JVM và build xác nhận phần mềm.
Thực thi điều hòa/kính và read-back trên xe vẫn cần báo cáo runtime từ đầu xe.

## Capture đầu xe ngày 11/11/2026 và bản sửa 1.0.5

Chủ repo cung cấp `20261111-102130-98324d.zip`, ứng dụng cài trên xe là 1.0.4,
UID 1000. Quyền CONTROL_CAR_CLIMATE, CONTROL_CAR_WINDOWS, CAR_VENDOR_EXTENSION,
CAR_SPEED và CAR_POWERTRAIN đã được cấp. Log đọc tốc độ 0.0 và số P (4) thành công.

`CarPropertyConfig` trong `99-car-service.txt` xác nhận nhiệt độ có area **49/68**,
nguồn A/C, quạt và hướng gió có area **117**. Phần HAL dump của cùng file in các
area lần lượt là `31`, `44`, `75` theo hệ 16. Bản trước đã đọc nhầm thành thập phân.
Bản 1.0.5 dùng hằng `0x31`, `0x44`, `0x75`, tương ứng 49, 68, 117.

Log `BCMModule.onCustomizeFunctionValueChanged` xác nhận các property
INFO_ID_WINDOW_FL/FR/RL/RR trả giá trị vị trí trong khoảng 0–100 khi thao tác OEM.
Người dùng xác nhận chỉ hạ hết rồi nâng lại, vì vậy tên bước 25/50/75 trong file
collector không chứng minh đã đặt chính xác các vị trí đó. Các giá trị trung gian
trong callback là vị trí khi kính đang di chuyển. Chưa xác nhận lệnh Voice ghi kính.

Log 10:31:34 ghi `session_stuck_recovered state=PROCESSING ageMs=35022`.
Log không có stack trace nên chưa xác định được hàm native đang chờ. Nhánh phản
hồi không nghe rõ/chặn lệnh trước đây gọi TTS đồng bộ ngoài timeout của command.
Bản sửa chạy phản hồi trên một worker, giới hạn 10 giây, không xếp hàng thêm khi
worker bị treo. ASR được trả về sau khi phần đợi phản hồi hết hạn. Có log phase ở
watchdog và log kết quả typed command để phân biệt các lần thử sau.

Body API được khởi tạo khi dùng và thử lại sau tối thiểu 5 giây nếu lần đầu thất
bại. Trước đây body API bị giữ ở null cho tới khi VHAL kết nối lại, kể cả khi VHAL
đang đọc tốt. Đây là sửa phục hồi từ phân tích code, chưa phải xác nhận rằng lần
khởi tạo ECARX trên xe đã thất bại trong phiên capture này.

Kiểm thử hồi quy gồm các area thập phân từ capture, retry body API và TTS giả lập
bỏ qua interrupt. Việc ghi và đọc lại trạng thái sau khi cài 1.0.5 vẫn cần thử trên xe.
