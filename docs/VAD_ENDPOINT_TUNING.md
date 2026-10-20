# THT Voice — VAD / endpoint tuning

## Mục tiêu

Part 2.7 thêm lớp cấu hình runtime có giới hạn cho VAD/endpoint của ASR offline. Mục tiêu là cho phép tune trên IHU689G thật mà không phải sửa code/rebuild sau mỗi lần thử.

Đây **không** phải bằng chứng rằng các giá trị ngoài mặc định đã được xác nhận trên xe thật. Giá trị cuối cùng chỉ được chốt sau khi thu log cabin thực tế theo `IHU689G_HARDWARE_VALIDATION.md`.

## Baseline giữ nguyên

Mặc định vẫn là:

```text
speech RMS threshold = 0.012
minimum speech       = 180 ms
trailing silence     = 700 ms
maximum listening    = 12 s
```

Vì vậy nếu người dùng không thay đổi cài đặt, hành vi nhận dạng giữ baseline trước Part 2.7.

## Giới hạn an toàn

`AsrEndpointConfig.normalized()` luôn kẹp giá trị trong khoảng:

```text
speech RMS threshold: 0.004 .. 0.030
minimum speech:        120 .. 600 ms
trailing silence:      350 .. 1600 ms
maximum listening:     4 .. 12 s
```

`maximum listening` không được phép vượt quá hard cap 12 giây của voice service.

## Độ nhạy mic

UI dùng ba profile dễ hiểu:

```text
Thấp   -> RMS 0.018
Chuẩn  -> RMS 0.012
Cao    -> RMS 0.008
```

Độ nhạy **cao** nghĩa là ngưỡng RMS thấp hơn, vì vậy dễ bắt giọng nhỏ hơn nhưng cũng dễ bị tiếng quạt/nhạc coi là speech hơn. Không nên tăng độ nhạy chỉ để sửa một lần missed command; cần so sánh missed speech và false speech trong nhiều phiên test.

## Hai backend Sherpa

### Offline transducer / Gipformer 65M

`SherpaBufferedOfflineAsrEngine` đọc config runtime cho từng chunk và dùng:

- RMS để xác định speech;
- minimum speech;
- trailing silence;
- maximum listening.

Khi endpoint local đạt điều kiện, engine decode đúng một lần.

### Online transducer

`SherpaOnlineTransducerAsrEngine` vẫn giữ endpoint detector native của Sherpa. Part 2.7 thêm một endpoint local cùng chuẩn RMS/trailing-silence bên ngoài.

Phiên kết thúc khi **native endpoint hoặc local endpoint** đạt điều kiện. Cách này không thay đổi model API và giúp cùng một bộ cài đặt có tác dụng với cả hai backend.

## Runtime update

`UserVoicePreferences` nạp cấu hình vào `AsrEndpointRuntime` khi được tạo. Khi người dùng thay đổi độ nhạy mic, thời gian im lặng hoặc thời gian nghe tối đa, runtime config được cập nhật ngay.

ASR engine lấy snapshot khi xử lý audio nên không cần hủy/tạo lại model 65M chỉ để thay đổi endpoint setting.

## Quy trình tune trên IHU689G

1. Giữ profile mặc định và chạy collector hardware-validation.
2. Test cabin yên tĩnh, quạt thấp/cao, nhạc nhỏ/vừa.
3. Ghi lại câu bị cắt sớm, chờ quá lâu, không bắt đầu speech hoặc false speech.
4. Chỉ thay **một biến mỗi lần**.
5. Chạy lại cùng tập câu test.
6. Không đánh dấu GREEN nếu chỉ dựa trên emulator/CI.

Các câu regression bắt buộc vẫn gồm `20`, `22`, `hai mươi`, `hai mươi hai` để bảo đảm VAD tuning không che lỗi parser cũ.
