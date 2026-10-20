# Phase 10 — Command-scoped VHAL trace

Mục tiêu của trace này là nối **một typed vehicle command** với toàn bộ VHAL IO xảy ra trong lúc command đó chạy, mà không thay đổi Property ID, Area ID, retry timing hay tiêu chí `Confirmed`.

## Log format

Bản hardware-test mới ghi VHAL theo dạng:

```text
THTVoiceVHAL: trace=17 scope=HVAC:SetTemperature writeSeq=31 op=SET_FLOAT property=<id> area=<id> value=22.0 result=ACCEPTED
THTVoiceVHAL: trace=17 scope=HVAC:SetTemperature writeSeq=31 op=GET_FLOAT property=<id> area=<id> value=22.0 result=OK
```

Ý nghĩa:

- `trace`: ID của một typed vehicle command trong process hiện tại.
- `scope`: nhóm và loại command, ví dụ `HVAC:SetTemperature`, `WINDOW:OPEN`.
- `writeSeq`: số thứ tự write VHAL; GET sau write giữ sequence gần nhất để hỗ trợ correlation.
- `op/property/area/value/result`: IO thực tế với `CarPropertyManager`.

Không có transcript giọng nói, VIN hay activation code trong command trace.

## Analyzer

Sau khi collector tạo bundle, chạy analyzer tổng quát trước:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\analyze-ihu689g-validation.ps1 `
  -BundlePath .\thtvoice-validation\<TIMESTAMP>
```

Sau đó chạy command trace analyzer:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\analyze-vhal-command-trace.ps1 `
  -BundlePath .\thtvoice-validation\<TIMESTAMP>
```

Script tạo:

- `VHAL_COMMAND_TRACE.csv`: từng IO event có `TraceId + Scope + WriteSeq`.
- `VHAL_COMMAND_SUMMARY.csv`: tổng hợp từng command, số SET/GET thành công/thất bại và PropertyId/AreaId đã chạm tới.

## Cách triage

Một command khỏe mạnh thường có chuỗi:

```text
trace=N scope=...
SET_* result=ACCEPTED
GET_* result=OK
CommandExecutionResult = Confirmed
```

Các trường hợp cần xem kỹ:

- `SET accepted` nhưng không có `GET OK`: write được API nhận nhưng read-back chưa chứng minh trạng thái.
- `SET FAILED`: kiểm tra permission, property availability và area trước khi sửa parser.
- Có `GET OK` nhưng giá trị khác request: có thể actuator đang di chuyển hoặc mapping/value chưa đúng; không kết luận chỉ từ một sample.
- `trace=0` trên SET: write xảy ra ngoài command scope hiện được instrument; cần xác định call path trước khi đánh PASS.

## Quy tắc an toàn

- `result=ACCEPTED` không đồng nghĩa xe đã thực hiện xong.
- Chỉ `CommandExecutionResult.Confirmed` với vehicle read-back mới được phát câu khẳng định thành công.
- Không thêm Property ID/Area ID mới chỉ dựa vào correlation log.
- Với kính, cần quan sát hành vi cơ khí thật vì read-back giữa chuyển động có thể khác target.
- MOCK/CI không được coi là hardware-confirmed.
