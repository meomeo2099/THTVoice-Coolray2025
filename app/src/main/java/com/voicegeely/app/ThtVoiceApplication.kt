package com.voicegeely.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import com.voicegeely.app.voice.AndroidRuntimeContext

/** Installs only process-wide application context needed by read-only Android probes. */
class ThtVoiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidRuntimeContext.install(this)
        // Coolray 2025: the process may start on LOCKED_BOOT_COMPLETED (BootReceiver is directBootAware)
        // while user storage is still locked. The history database lives there: open it after unlock.
        val users = getSystemService(UserManager::class.java)
        if (users == null || users.isUserUnlocked) {
            com.voicegeely.app.history.VoiceHistory.install(this)
            return
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching { unregisterReceiver(this) }
                com.voicegeely.app.history.VoiceHistory.install(this@ThtVoiceApplication)
            }
        }, IntentFilter(Intent.ACTION_USER_UNLOCKED))
    }
}
