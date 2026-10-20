THT COOLRAY – REF-DIAG + REF-VOICE ANALYSIS
=========================================

MỤC ĐÍCH
--------
Gói này tổng hợp dữ liệu đã phân tích từ 2 APK:
1. ref-diag.apk
2. ref-voice.apk

Mục tiêu là làm tài liệu tham khảo cho THTVoice/THT-Dashboard trên Geely Coolray, đặc biệt các phần:
- Coolray / SX11A5 / IHU624G
- VHAL Property ID + Area ID + Value
- ECU / DID / UDS
- HVAC, quạt, nhiệt độ
- Kính, cửa sổ trời, rèm trời
- Cốp, khóa cửa, gương
- Ghế sưởi / thông gió
- Đèn, độ sáng màn hình
- Kiến trúc CoolrayVehicleController

NÊN ĐỌC THEO THỨ TỰ NÀY
-------------------------
1. README_FIRST.txt
   File hướng dẫn này.

2. Báo cáo tổng hợp / summary
   Đọc trước để hiểu kiến trúc chung và các phát hiện quan trọng nhất.

3. Bảng VHAL Coolray
   Dùng để tra nhanh Property ID, Area ID, kiểu dữ liệu và giá trị điều khiển.

4. Bảng SX11A5 ECU / DID / Operation
   Dùng khi cần tra UDS, BCM, AC và các ECU khác.

5. JSON tổng hợp
   Phù hợp để dùng làm nguồn cho code, parser hoặc CoolrayVehicleController.

PHÁT HIỆN QUAN TRỌNG NHẤT
--------------------------
REF-DIAG:
- Có profile SX11A5 dành cho nhánh Coolray/Binyue/SX11-A5.
- Có 12 ECU, khoảng 514 DID và 82 operation.
- Có UDS tunnel qua VHAL Property 0x2170A634.
- Hỗ trợ UDS 0x22 / 0x2E / 0x2F / 0x31.
- Có Extended Session 0x10 03 và Security Access 0x27 khi cần.

REF-VOICE:
- Có nhánh nhận diện COOLRAY = IHU624G.
- Có logic điều khiển trực tiếp qua CarPropertyManager / VHAL.
- Có các Property riêng cho Coolray, ví dụ:
  * Window:            0x234020AB
  * Trunk:             0x21111900
  * Screen Brightness: 0x29020400
  * Interior Light:    0x15400F04
- HVAC quan trọng:
  * Temperature:       0x15600503
  * Fan:               0x15400500
  * Airflow:           0x15400501
  * Seat Heating:      0x1540050B
  * Seat Ventilation:  0x15400513

CÁCH DÙNG CHO THTVOICE COOLRAY
------------------------------
Ưu tiên:
1. Dùng VHAL direct nếu đã biết chắc Property ID + Area ID + Value.
2. Nếu chưa có direct property, dùng REF-DIAG UDS bridge + SX11A5.
3. Với lệnh khóa/cốp/kính/gương/điều hòa nên đọc trạng thái trước và đọc lại sau khi gửi lệnh.
4. Chỉ báo thành công khi xe trả trạng thái phù hợp.

KHÔNG NÊN ĐƯA VÀO VOICE BẢN ĐẦU
--------------------------------
Không bật các lệnh can thiệp sâu như:
- ép số / TCU actuator
- throttle
- fuel pump
- injector
- EPS calibration
- ESC / EPB routines
- SRS routines
- ECU reset / adaptation

LƯU Ý KHI GỬI SANG CHAT KHÁC / NGƯỜI KHÁC
-----------------------------------------
- File ZIP này tự chứa toàn bộ tài liệu tổng hợp nên có thể đọc độc lập.
- Không cần lịch sử chat hiện tại để hiểu nội dung.
- Nếu cần kiểm chứng ngược lại code APK gốc, nên gửi thêm:
  * ref-diag.apk
  * ref-voice.apk
- Nếu dùng để viết code, nên ưu tiên các bảng CSV/JSON thay vì chỉ đọc báo cáo.

MỨC ĐỘ TIN CẬY
---------------
- Các Property/Area/Value được ghi là xác định rõ: lấy trực tiếp từ code APK.
- Các mục đánh dấu cần test: chưa nên coi là mapping cuối cùng cho xe Việt Nam.
- Model Việt Nam có thể khác IHU624G, vì vậy cần log/test thực tế trên đúng màn Coolray trước khi phát hành cho khách.

GỢI Ý PROMPT KHI ĐƯA FILE NÀY SANG CHAT KHÁC
-------------------------------------------
"Đọc toàn bộ file ZIP này, ưu tiên README_FIRST.txt và các bảng VHAL/SX11A5. Đây là tài liệu nền cho dự án THTVoice Coolray. Hãy dùng lại dữ liệu đã tổng hợp, không làm lại từ đầu nếu không cần. Khi đề xuất Property ID / Area ID / UDS, phân biệt rõ mục nào đã xác nhận từ APK và mục nào vẫn cần test trên xe."

