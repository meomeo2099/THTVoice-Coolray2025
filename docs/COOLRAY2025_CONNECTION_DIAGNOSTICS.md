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
