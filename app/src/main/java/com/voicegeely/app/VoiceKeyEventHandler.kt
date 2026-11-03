package com.voicegeely.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/** Shared by the visible activity and the user-enabled background key service. */
class VoiceKeyEventHandler {
    private val policy = VoiceKeyEventPolicy()

    fun handle(context: Context, event: KeyEvent, source: String): Boolean {
        if (!VoiceKeyEventPolicy.isVoiceKey(event.keyCode)) return false
        val detail = "keyCode=${event.keyCode} scanCode=${event.scanCode} action=${event.action} " +
            "repeat=${event.repeatCount} canceled=${event.isCanceled} device=${event.deviceId}"
        VoiceKeySignalLog.add(SystemClock.elapsedRealtime(), source, detail)
        Log.i("THTVoiceECARX", "voice_key_signal path=$source $detail")
        return policy.handle(event.keyCode, event.action, event.repeatCount, event.isCanceled, event.deviceId) {
            VoiceKeyLauncher.handle(context, VoiceKeyActions.ECARX, source)
        }
    }

    fun clear() = policy.clear()
}
