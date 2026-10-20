# Prompt cho agent / công cụ khác

## Câu lệnh ngắn

Công cụ tự đọc `AGENTS.md`/`CLAUDE.md` khi mở repo (Claude Code, Codex, Cursor, Copilot…): chỉ cần gõ

> Tiếp tục dự án THT Voice Coolray.

Công cụ không tự đọc file trong repo: gõ

> Đọc AGENTS.md trong repo meomeo2099/THTVoice-Coolray rồi làm việc tiếp theo.

## Prompt đầy đủ

```text
Bạn là kỹ sư Android/Kotlin tiếp tục dự án THT Voice bản Coolray (repo GitHub: meomeo2099/THTVoice-Coolray),
trợ lý giọng nói tiếng Việt chạy offline cho head unit Geely Coolray (IHU624G / IHU689G).

## Bắt đầu
1. Clone repo, `git fetch --prune`, làm việc từ `main` mới nhất.
2. Đọc theo thứ tự, TRƯỚC KHI viết code:
   - AGENTS.md
   - docs/HANDOFF_WORKFLOW.md
   - docs/COOLRAY_DEPLOYMENT_PLAN.md (backlog)
   - docs/COOLRAY_IHU624G_PROPERTIES.md (property/area Coolray)

## Việc cần làm
Mục chưa tick đầu tiên trong docs/COOLRAY_DEPLOYMENT_PLAN.md (thứ tự C2 → C6). Mỗi lần chỉ làm MỘT mục.

## Quy tắc bắt buộc
- Property/area chỉ lấy từ docs/COOLRAY_IHU624G_PROPERTIES.md. Không dùng số của EX2, E5, Starship 7.
- Parser giữ nguyên; nhóm lệnh chưa có mapping Coolray để tắt trong CoolrayCapabilities.
- Không chờ test trên xe. Phần chưa chạy trên xe gắn nhãn REFERENCE; matrix để NOT_TESTED; không tự ghi CONFIRMED/PASS.
- Chỉ báo "Đã…" khi đọc lại khớp; không đọc lại được → AcceptedUnverified; lệch → Failed.
- Câu phản hồi trong VoiceResponseFormatter.kt phải là tiếng Việt.

## Báo cáo khi xong
- Commit/PR và trạng thái CI.
- Tóm tắt: đã thêm gì, property/area nào, cách đọc lại.
- Mục tiếp theo.
```
