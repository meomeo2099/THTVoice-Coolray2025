# Phase 10 — Media / Navigation validation

Mục tiêu: phân biệt rõ **intent đã được gửi**, **Android state đã xác nhận**, và **xe/người dùng thực sự thấy đúng app/media**. Không coi dispatch thành công là hardware-confirmed.

## Runtime trace

Tag: `THTVoiceAndroid`.

Các marker chính:

```text
op=OPEN_APP package=<package> result=STARTED|NO_LAUNCH_INTENT|FAILED
op=OPEN_URI package=<package> result=STARTED|NOT_RESOLVED|FAILED
op=MEDIA action=<action> phase=DISPATCH ...
op=MEDIA action=<action> verify=PLAYBACK_STATE result=CONFIRMED
op=MEDIA action=NEXT|PREVIOUS verify=TRACK_CHANGE package=<package> result=CONFIRMED
op=MEDIA action=<action> verify=<type> result=ACCEPTED_UNVERIFIED
op=MEDIA action=<action> result=FAILED
```

Trace không ghi URI tìm kiếm/điểm đến và không ghi tên bài hát.

## Quy tắc xác nhận

- `OPEN_APP` / `OPEN_URI` `STARTED`: explicit intent đã start. Vẫn phải nhìn đúng app đích trên màn hình xe.
- Play/Pause/Toggle: chỉ Confirmed khi `AudioManager.isMusicActive` đạt trạng thái mong đợi.
- Volume: Confirmed bằng stream-volume read-back.
- Next/Previous: chỉ Confirmed khi MediaSession metadata chứng minh track identity thay đổi trên **cùng package + cùng session**.
- Nếu media key đã gửi nhưng read-back không đủ: `AcceptedUnverified`, không nói “đã đổi bài”.
- Không thêm generic browser fallback để che lỗi package target.

## Collector

`collect-ihu689g-validation.ps1` tạo:

- `media-navigation-events.txt`
- `media-session-before.txt`
- `media-session-after.txt`

## Analyzer riêng

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\analyze-media-navigation.ps1 `
  -BundlePath .\thtvoice-validation\<TIMESTAMP>
```

Có thể truyền trực tiếp file ZIP collector.

Output:

- `MEDIA_NAVIGATION_SUMMARY.txt`

Báo cáo đếm app/URI start, media dispatch, Confirmed, AcceptedUnverified, Failed, Next/Previous track-change confirmation và package đã start.

## Test xe thật

Ưu tiên:

1. `mở YouTube` — kiểm tra đúng package theo thứ tự ưu tiên, không mở browser chung.
2. Play / Pause / Toggle — đối chiếu âm thanh thật + read-back.
3. Tăng / giảm âm lượng — đối chiếu volume thực tế.
4. Next / Previous — kiểm tra track thật đổi và MediaSession xác nhận.
5. Google Maps / Vietmap Live / Waze — explicit app phải mở đúng khi đã cài.
6. Nếu app không cài hoặc intent không resolve, phải báo thất bại/không xác minh, không tự chuyển sang browser.

Chỉ cập nhật `IHU689G_TEST_MATRIX.csv` thành PASS sau khi hành vi trên xe thật khớp log/read-back.
