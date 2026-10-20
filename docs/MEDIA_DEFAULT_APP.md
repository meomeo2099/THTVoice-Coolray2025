# Ứng dụng Media mặc định

THT Voice dùng **một** setting `default_media_app` cho cả lệnh nhạc và video. Parser vẫn giữ `MUSIC` và `VIDEO` riêng để hiểu đúng ý người dùng; chỉ phần chọn ứng dụng được gộp.

## Lựa chọn
- Tự động
- YouTube
- Vanced
- ReVanced
- Morphe
- SmartTube
- YouTube Music
- Zing MP3
- Spotify

## Quy tắc
1. Nếu câu nói chỉ rõ ứng dụng, ứng dụng được nói có ưu tiên cao nhất.
2. Nếu không chỉ rõ ứng dụng, dùng `default_media_app`.
3. Nếu app mặc định không cài, thử fallback đúng loại nội dung.
4. Mỗi command chỉ mở **một** package cuối cùng; không mở đồng thời nhiều app.
5. `MUSIC` và `VIDEO` không bị gộp ở parser, chỉ gộp setting ứng dụng.

## Package candidates
- YouTube: `com.google.android.youtube`
- Vanced: `com.vanced.android.youtube`
- ReVanced: `app.revanced.android.youtube`, `app.rvx.android.youtube`, `com.revanced.android.youtube`
- Morphe: `app.morphe.android.youtube`
- SmartTube: `org.smarttube.stable`, `org.smarttube.beta`, `com.liskovsoft.smarttubetv`, `com.liskovsoft.smarttubetv.beta`, `com.teamsmart.videomanager.tv`
- YouTube Music: `com.google.android.apps.youtube.music`, `app.revanced.android.apps.youtube.music`, `anddea.youtube.music`
- Zing MP3: `com.zing.mp3`
- Spotify: `com.spotify.music`

Package của app mod có thể khác theo bản APK. Resolver giữ nhiều candidate để tăng khả năng tương thích; khi có APK thực tế trên xe nên xác nhận bằng package manager/ADB rồi ưu tiên package đúng của bản đang phân phối.
