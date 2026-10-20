# THT Voice — Floating voice overlay

## Mục tiêu

Overlay là UI nhỏ hiển thị vòng đời một phiên voice mà không cần mở `MainActivity`.

```text
ECARX / Hey THT
      |
      v
  Đang nghe...
      |
      +-- partial STT
      |
      v
 Đang xử lý...
      |
      v
 result / error
      |
      v
   auto hide
```

Overlay không tham gia quyết định command và không có quyền bypass SafetyGate. Nếu không có quyền `SYSTEM_ALERT_WINDOW`, toàn bộ voice pipeline vẫn tiếp tục hoạt động bình thường.

## Runtime

`VoiceOverlayController` được sở hữu trực tiếp bởi `VoiceAssistantService`, do đó:

- hoạt động khi `MainActivity` đã đóng;
- dùng cùng lifecycle với foreground voice service;
- không cần một service UI thứ hai;
- đóng ngay khi voice service bị destroy.

Mọi thao tác `WindowManager` được chuyển về main looper, trong khi ASR/KWS vẫn chạy ở worker thread.

## Trạng thái

`VoiceOverlayStateMachine` có 5 trạng thái:

- `HIDDEN`
- `LISTENING`
- `PROCESSING`
- `RESULT`
- `ERROR`

Partial text không bị mất khi chuyển sang final/processing. Kết quả và lỗi tự ẩn sau một khoảng ngắn.

## Chống race khi auto-hide

Mỗi lần render tăng một generation token. Callback auto-hide cũ chỉ được phép đóng overlay nếu token vẫn còn là phiên hiện tại. Vì vậy kết quả của command trước không được phép đóng nhầm overlay vừa mở cho command sau hoặc cho bước Safety confirmation.

## Kéo vị trí

Overlay nhận touch trên chính card để kéo bằng `WindowManager.updateViewLayout`. Nó dùng `FLAG_NOT_FOCUSABLE`, vì vậy không chiếm keyboard/focus của ứng dụng đang chạy bên dưới.

## Hardware validation còn lại

Trước khi coi overlay là hoàn tất trên IHU689G thật cần kiểm tra:

- kích thước chữ/card ở 1920x1080, 160 dpi;
- vị trí mặc định có che UI quan trọng hay không;
- kéo card bằng màn hình xe;
- overlay khi YouTube/Maps/Vietmap đang foreground;
- partial text dài;
- chuyển `LISTENING -> PROCESSING -> RESULT` với TTS bật/tắt;
- Safety confirmation hai lượt không bị callback auto-hide cũ đóng nhầm.

Các thông số kích thước/vị trí chỉ tune sau khi có ảnh/video hoặc test trực tiếp trên head unit.
