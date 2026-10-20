# Hướng dẫn cài THT Voice cho Geely Coolray

Dành cho người dùng cuối. Áp dụng cho màn hình xe Coolray có mã **IHU624G** hoặc **IHU689G**.

> **Cảnh báo:** cài đặt cần bật ADB và (với cách có root) ghi vào phân vùng hệ thống của màn hình xe.
> Làm sai có thể khiến màn hình không khởi động được. Chỉ làm khi bạn hiểu rủi ro và có cách khôi phục.
> Không áp dụng cho màn hình đã cài bản Việt hoá/thương mại có trợ lý giọng nói riêng.

## 1. Chuẩn bị

- Máy tính Windows, macOS hoặc Linux đã cài **ADB** (gói *platform-tools* của Android).
- Cáp USB-A ↔ USB-A có truyền dữ liệu.
- File APK mới nhất: trang Releases của repo `meomeo2099/THTVoice-Coolray`, tag `hardware-test-latest`,
  file `THTVoice-IHU689G-hardware-test.apk`.
- Thư mục `scripts/` của repo (có `install-coolray.sh` và `privapp-permissions-com.tht.voice.coolray2025.xml`).
  Trên Windows chạy script bằng Git Bash hoặc WSL; máy cần có lệnh `unzip`.

## 2. Bật ADB trên màn hình xe

1. Vào menu kỹ sư của màn hình xe và bật ADB (cách vào menu tuỳ phiên bản phần mềm).
2. Cắm cáp từ máy tính vào cổng USB dữ liệu của xe.
3. Trên máy tính chạy `adb devices`. Phải thấy đúng **một** thiết bị ở trạng thái `device`.
4. Kiểm tra mã màn hình: `adb shell getprop ro.product.model` — kết quả nên chứa `IHU624G` hoặc `IHU689G`.

## 3. Chọn cách cài

Chạy `adb root` rồi `adb remount`:

- Nếu thấy `remount succeeded` → màn hình **có root**, dùng **cách A**.
- Nếu báo lỗi (ví dụ `Not running as root`) → **không có root**, dùng **cách B**.

### Cách A — có root (cài thành ứng dụng hệ thống)

```bash
bash scripts/install-coolray.sh root THTVoice-IHU689G-hardware-test.apk
```

Script sẽ: kiểm tra mã màn hình, đặt APK vào `/system/priv-app/THTVoiceCoolray`, tự tách thư viện
native từ APK, chép file cho phép quyền hệ thống, tắt trợ lý gốc của xe rồi khởi động lại màn hình.
Coolray thường cần **vá framework** mới cài được kiểu này; nếu `adb remount` thất bại, dùng cách B.

### Cách B — không root

```bash
bash scripts/install-coolray.sh nonroot THTVoice-IHU689G-hardware-test.apk
```

Cài như ứng dụng thường và tắt trợ lý gốc. Một số lệnh điều khiển xe có thể bị màn hình từ chối
nếu ứng dụng không có quyền hệ thống.

## 4. Lần đầu sử dụng

1. Mở **THT Voice** trên màn hình xe, nhập mã kích hoạt khi được hỏi.
2. Vào **Cài đặt → Gói giọng đọc → Tải về** để có giọng trả lời (khoảng 330 MB; cần mạng một lần).
3. Gọi trợ lý bằng **nút Voice trên vô lăng** hoặc nói **"Hey THT"** (đổi sang "Hello Geely" / "Hey Geely" trong Cài đặt).

## 5. Lệnh thường dùng

| Nhóm | Ví dụ |
|---|---|
| Điều hoà | "bật điều hoà", "nhiệt độ 22", "quạt mức 3", "gió vào mặt", "sấy kính trước", "sấy kính sau", "lấy gió trong", "bật làm lạnh nhanh" |
| Kính cửa | "mở kính lái", "đóng tất cả kính", "hé kính", "mở kính sau bên phụ 50 phần trăm" |
| Cửa sổ trời | "mở cửa sổ trời", "hé cửa sổ trời", "đóng rèm che nắng" |
| Đèn | "bật đèn trong xe", "bật đèn cốt", "bật đèn định vị", "bật đèn sương mù" |
| Ghế, vô lăng | "bật sưởi ghế", "sưởi ghế phụ mức 3", "bật làm mát ghế lái", "bật sưởi vô lăng" |
| Hỏi xe | "xăng còn bao nhiêu", "xe còn đi được bao nhiêu", "áp suất lốp", "khi nào bảo dưỡng", "nhiệt độ nước làm mát", "kiểm tra ắc quy", "odo" |
| Ứng dụng | "mở YouTube", "mở bản đồ", "dẫn đường đến …", "bài tiếp theo" |

Trợ lý chỉ nói **"Đã …"** khi xe xác nhận lệnh đã xong. Một số lệnh (đèn ngoài, cửa sổ trời, kính)
sẽ hỏi lại khi xe đang chạy. Lệnh xe không có trên Coolray sẽ được trả lời "chưa hỗ trợ trên xe này".

## 6. Gỡ cài đặt

```bash
bash scripts/install-coolray.sh uninstall
```

Gỡ ứng dụng, xoá thư mục hệ thống (nếu có root) và bật lại trợ lý gốc của xe.

## 7. Khi gặp lỗi

- Trợ lý nói "Không nhận ra màn hình xe, đã chặn lệnh điều khiển xe": màn hình không báo mã
  IHU624G/IHU689G. Gửi kết quả `adb shell getprop | grep ro.product` cho người hỗ trợ.
- Lệnh xe không chạy: lấy log bằng `adb logcat -d > log.txt` ngay sau khi nói lệnh và gửi kèm.
