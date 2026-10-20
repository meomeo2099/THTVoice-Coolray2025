package com.voicegeely.app.voice

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Brings the launcher home screen to the front. A normal app cannot force-stop
 * other apps, so this is reported as "về màn hình chính", never as "đã đóng".
 */
class AndroidHomeLauncher(context: Context) {
    private val appContext = context.applicationContext

    fun goHome(): AndroidBackendResult = try {
        appContext.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Log.i(TAG, "op=GO_HOME result=STARTED")
        AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, actualValue = "home")
    } catch (t: Throwable) {
        Log.w(TAG, "op=GO_HOME result=FAILED type=${t.javaClass.simpleName}")
        AndroidBackendResult(false, false, detail = "Không về được màn hình chính.")
    }

    private companion object {
        const val TAG = "THTVoiceHome"
    }
}
