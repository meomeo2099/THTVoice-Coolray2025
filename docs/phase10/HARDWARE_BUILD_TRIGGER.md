# Phase 10 — Controlled hardware build trigger

Workflow `IHU689G hardware test APK` có hai cách chạy:

1. `workflow_dispatch` thủ công trên GitHub Actions.
2. Push có kiểm soát để automation/connector có thể kích hoạt build mà không cần bấm UI.

## Push trigger được phép

Workflow chỉ chạy khi **đồng thời** thỏa:

```text
branch = hardware-test/**
path changed = .hardware-test-trigger
```

Ví dụ branch:

```text
hardware-test/run-20260927-305083c7
```

Marker nên ghi nguồn build:

```text
source_main_sha=<main sha>
requested_at=<date/time>
purpose=phase10-hardware-validation
```

## Những gì KHÔNG kích hoạt hardware artifact

- push bình thường lên `main`;
- Pull Request;
- branch feature/phase10 thông thường;
- thay đổi file khác trên `hardware-test/**` mà không chạm `.hardware-test-trigger`.

Do đó CI `Android build` vẫn chỉ validate/test/assemble và không upload APK.

## Artifact

Hardware workflow tạo một package `THTVoice-IHU689G-hardware-test`. Tải artifact từ Actions nếu upload thành công; khi Actions artifact storage không khả dụng, workflow cập nhật private rolling prerelease [`hardware-test-latest`](https://github.com/meomeo2099/THTVoice/releases/tag/hardware-test-latest) với ZIP package và `BUILD_INFO.txt`.

Actions artifact retention: 3 ngày. Rolling prerelease được cập nhật bởi lần build kế tiếp.

Artifact phải qua:

```text
Sherpa runtime
→ ASR Vietnamese 65M INT8
→ KWS profiles: Hey THT / Hello Geely / Hey Geely
→ unit tests
→ assembleDebug
→ BUILD_INFO/SHA256/ASSET_SHA256
→ verify-hardware-package.ps1
→ upload-artifact (nếu storage sẵn sàng)
→ publish private rolling prerelease fallback
```

## Quy tắc branch

- Branch hardware-test chỉ dùng làm build trigger, không dùng làm development branch.
- Luôn tạo từ `main` xanh hiện tại.
- Không merge `.hardware-test-trigger` vào `main`.
- Một build mới cần branch/marker mới hoặc cập nhật marker có chủ đích.
- Sau khi lấy evidence cần thiết có thể giữ branch làm dấu vết hoặc xóa sau; provenance thật nằm trong `BUILD_INFO.txt` và validation bundle.

Mục tiêu là cho phép automation tạo hardware-test artifact có chủ đích mà không quay lại tình trạng mọi push/PR đều tiêu tốn artifact quota.
