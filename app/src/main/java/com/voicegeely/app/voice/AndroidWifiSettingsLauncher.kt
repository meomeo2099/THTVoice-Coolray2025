package com.voicegeely.app.voice

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Opens Android's Wi-Fi settings surface using a public intent only.
 * This helper never toggles the Wi-Fi radio and never reports a radio-state change.
 */
class AndroidWifiSettingsLauncher(context: Context) {
    private val appContext = context.applicationContext

    fun open(): AndroidBackendResult {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = try {
            intent.resolveActivity(appContext.packageManager)
        } catch (t: Throwable) {
            Log.w(TAG, "op=WIFI_SETTINGS resolve=FAILED type=${t.javaClass.simpleName}")
            null
        } ?: return AndroidBackendResult(
            accepted = false,
            confirmed = false,
            detail = "Thiết bị không có màn cài đặt Wi-Fi có thể mở từ ứng dụng."
        )

        return try {
            appContext.startActivity(intent)
            Log.i(TAG, "op=WIFI_SETTINGS component=${resolved.flattenToShortString()} result=STARTED")
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = "Đã mở cài đặt Wi-Fi",
                detail = "Chỉ mở Settings; không thay đổi trạng thái Wi-Fi."
            )
        } catch (t: SecurityException) {
            Log.w(TAG, "op=WIFI_SETTINGS result=PERMISSION_DENIED")
            AndroidBackendResult(false, false, detail = "Android không cho phép mở cài đặt Wi-Fi từ ứng dụng này.")
        } catch (t: Throwable) {
            Log.w(TAG, "op=WIFI_SETTINGS result=FAILED type=${t.javaClass.simpleName}")
            AndroidBackendResult(false, false, detail = "Không mở được cài đặt Wi-Fi.")
        }
    }

    private companion object {
        const val TAG = "THTVoiceWifi"
    }
}
