package com.voicegeely.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Receives supported steering keys while another app is visible, with explicit user opt-in. */
class VoiceKeyAccessibilityService : AccessibilityService() {
    private val keyHandler = VoiceKeyEventHandler()

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        VoiceKeySignalLog.add(SystemClock.elapsedRealtime(), "key_service", "CONNECTED")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = keyHandler.handle(this, event, "accessibility_key")

    // No window content or UI events are read; only the supported hardware key codes are used.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = keyHandler.clear()

    override fun onDestroy() {
        connected = false
        keyHandler.clear()
        super.onDestroy()
    }

    companion object {
        @Volatile var connected = false
            private set

        fun isEnabled(context: Context): Boolean = runCatching {
            val component = ComponentName(context, VoiceKeyAccessibilityService::class.java)
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component }
        }.getOrDefault(false)

        fun status(context: Context): String = when {
            connected -> "đã kết nối"
            isEnabled(context) -> "đã bật, đang chờ hệ thống kết nối"
            else -> "chưa bật — Cài đặt → Nhận nút voice khi chạy nền → Mở cài đặt → bật THT Voice – Nút voice"
        }
    }
}
