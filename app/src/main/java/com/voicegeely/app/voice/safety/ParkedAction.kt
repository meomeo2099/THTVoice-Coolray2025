package com.voicegeely.app.voice.safety

/** Canonical names for non-voice actions that must only run while safely parked. */
enum class ParkedAction(val displayName: String) {
    OTA_INSTALL("Cài đặt bản cập nhật"),
    APK_INSTALL("Cài APK"),
    DEV_TOOLS("Công cụ phát triển"),
    SYSTEM_MAINTENANCE("Bảo trì hệ thống"),
    RESET_APP_DATA("Đặt lại dữ liệu ứng dụng"),
    TECHNICAL_CONFIGURATION("Cấu hình kỹ thuật")
}
