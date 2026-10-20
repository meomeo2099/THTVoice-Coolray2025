# Phase 10 — Hardware evidence rules

Mục tiêu của tài liệu này là ngăn việc đánh dấu một chức năng xe là PASS chỉ vì Android/VHAL không ném lỗi.

## Nguyên tắc

- `SET ... result=ACCEPTED` chỉ có nghĩa lời gọi ghi VHAL được chấp nhận tại thời điểm đó.
- `GET ... result=OK` chỉ có nghĩa đọc được một giá trị từ property/area tương ứng.
- Có SET + GET vẫn chưa đủ để chứng minh actuator cơ khí đã hoàn tất thao tác.
- Chỉ được ghi `Confirmed` khi runtime `CommandExecutionResult` xác nhận, read-back phù hợp và hành vi xe thật quan sát được cũng khớp.
- MOCK, parser test, CI hoặc emulator không được dùng làm bằng chứng hardware-confirmed.

## EvidenceStatus

### SET_FAILED
Ít nhất một thao tác SET trong trace thất bại. Không được coi command là thành công.

### READBACK_IO_FAILED
Có SET được chấp nhận nhưng read-back GET thất bại. Trạng thái xe chưa xác minh được.

### ACCEPTED_NO_READBACK
Có SET được chấp nhận nhưng không có GET thành công. Đây tương đương `AcceptedUnverified`, không phải PASS.

### READBACK_PRESENT_NEEDS_RUNTIME_RESULT
Có SET được chấp nhận và có GET thành công. Vẫn cần đối chiếu `CommandExecutionResult` và hành vi xe thật trước khi gọi `Confirmed`.

### INSUFFICIENT_EVIDENCE
Trace thiếu dữ liệu cần thiết để phân loại.

## File phân tích

Chạy:

```powershell
.\scripts\analyze-vhal-command-trace.ps1 -BundlePath <bundle-or-zip>
```

Script tạo:

- `VHAL_COMMAND_TRACE.csv`
- `VHAL_COMMAND_SUMMARY.csv`
- `VHAL_EVIDENCE_SUMMARY.csv`

`HardwareConfirmed` trong file evidence mặc định luôn là `False`. Script không tự nâng thành `True` chỉ từ log SET/GET.

## Review test matrix từ evidence

Sau khi có `VHAL_EVIDENCE_SUMMARY.csv`, có thể tạo một bản matrix để review:

```powershell
.\scripts\build-test-matrix-evidence-review.ps1 `
  -BundlePath <bundle-or-zip> `
  -MatrixPath .\docs\phase10\IHU689G_TEST_MATRIX.csv
```

Script tạo `IHU689G_TEST_MATRIX_REVIEW.csv` trong bundle. Nó không sửa matrix chuẩn trong repo và không tự ghi PASS.

Các gợi ý có thể xuất hiện:

- `REVIEW_FAIL`: có bằng chứng lỗi đủ mạnh để cần xem đúng test case trước khi ghi FAIL chính thức.
- `PENDING`: evidence thiếu read-back hoặc chưa đủ.
- `PENDING_HARDWARE_CONFIRMATION`: đã có SET + GET nhưng vẫn phải quan sát xe thật/CommandExecutionResult.
- `MANUAL_REVIEW`: nhóm chưa có mapper tự động an toàn.

## Quy tắc ghi test matrix

- `PASS`: chỉ sau khi xe thật thực hiện đúng + read-back/CommandExecutionResult phù hợp.
- `FAIL`: thao tác sai, bị từ chối, read-back sai ổn định hoặc hành vi xe không đúng.
- `BLOCKED`: chưa có quyền/property/backend cần thiết để test.
- `PENDING`: chưa chạy test xe thật hoặc bằng chứng chưa đủ.

Không sửa Property ID/Area ID dựa trên suy đoán khi evidence chưa đủ.
