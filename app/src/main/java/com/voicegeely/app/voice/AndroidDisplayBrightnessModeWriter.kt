package com.voicegeely.app.voice

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.voicegeely.app.voice.safety.SafetyDecisionType
import com.voicegeely.app.voice.safety.VehicleSafetyGate

/**
 * Guarded Android Settings.System writer for automatic/manual brightness mode.
 *
 * This is intentionally separate from vehicle display-theme VHAL settings.
 * Success is reported only after an independent Settings.System read-back.
 */
internal class AndroidDisplayBrightnessModeWriter(
    context: Context,
    private val parkedGate: VehicleSafetyGate
) {
    private val appContext = context.applicationContext

    fun set(mode: DisplayBrightnessReading.Mode): AndroidBackendResult {
        val label = DisplayBrightnessReading.modeDescription(mode)
        val initial = parkedGate.evaluateParkedOnly("Đổi chế độ độ sáng hệ thống")
        if (initial.type != SafetyDecisionType.ALLOW) {
            return AndroidBackendResult(
                accepted = false,
                confirmed = false,
                detail = initial.reason ?: "Chỉ đổi chế độ độ sáng khi xe đang đỗ ở số P."
            )
        }

        if (!Settings.System.canWrite(appContext)) {
            return AndroidBackendResult(
                accepted = false,
                confirmed = false,
                detail = "Ứng dụng chưa có quyền sửa thiết lập hệ thống."
            )
        }

        val resolver = appContext.contentResolver
        return try {
            val requested = DisplayBrightnessReading.settingValue(mode)
            val before = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            if (DisplayBrightnessReading.modeMatches(mode, before)) {
                return AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    verificationSource = VerificationSource.ANDROID_STATE,
                    actualValue = label
                )
            }

            // Re-check immediately before mutation; losing vehicle telemetry fails closed.
            val recheck = parkedGate.evaluateParkedOnly("Đổi chế độ độ sáng hệ thống")
            if (recheck.type != SafetyDecisionType.ALLOW) {
                return AndroidBackendResult(
                    accepted = false,
                    confirmed = false,
                    detail = recheck.reason ?: "Xe không còn ở trạng thái đỗ an toàn."
                )
            }

            if (!Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, requested)) {
                return AndroidBackendResult(
                    accepted = false,
                    confirmed = false,
                    detail = "Android từ chối ghi chế độ độ sáng hệ thống."
                )
            }

            val actual = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            val confirmed = DisplayBrightnessReading.modeMatches(mode, actual)
            Log.i(TAG, "op=DISPLAY_BRIGHTNESS_MODE requested=$requested actual=$actual result=${if (confirmed) "CONFIRMED" else "UNVERIFIED"}")
            AndroidBackendResult(
                accepted = true,
                confirmed = confirmed,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = if (confirmed) label else null,
                detail = if (confirmed) null else "Chế độ độ sáng đọc lại chưa khớp yêu cầu."
            )
        } catch (_: Settings.SettingNotFoundException) {
            AndroidBackendResult(false, false, detail = "Không đọc được chế độ độ sáng hệ thống.")
        } catch (t: SecurityException) {
            Log.w(TAG, "op=DISPLAY_BRIGHTNESS_MODE result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép đổi chế độ độ sáng hệ thống.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=DISPLAY_BRIGHTNESS_MODE result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không đổi được chế độ độ sáng hệ thống.")
        }
    }

    private companion object {
        const val TAG = "THTVoiceDisplayMode"
    }
}
