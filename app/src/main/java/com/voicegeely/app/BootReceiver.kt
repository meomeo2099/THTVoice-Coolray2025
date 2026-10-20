package com.voicegeely.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.voicegeely.app.security.LicenseManager

class BootReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "BootReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        // MY_PACKAGE_REPLACED (06/10/2026): after an update nothing restarted the service, so
        // "Hey Geely" stayed silent until the app was opened or the voice key pressed. The head
        // unit also sleeps instead of rebooting, so BOOT_COMPLETED alone is not enough.
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!LicenseManager(context).isLicensed()) {
            Log.i(TAG, "Boot completed nhưng app chưa có license hợp lệ")
            return
        }
        Log.i(TAG, "action=$action, starting licensed voice service")
        val serviceIntent = Intent(context, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
        else context.startService(serviceIntent)
    }
}
