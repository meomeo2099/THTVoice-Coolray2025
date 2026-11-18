package com.voicegeely.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.voicegeely.app.security.LicenseManager

/**
 * Which broadcasts restart the voice service when it is not running (wake word must work without
 * opening the app). Pure Kotlin so the list is unit tested.
 *
 * The head unit sleeps instead of rebooting, so BOOT_COMPLETED alone is not enough:
 * - ACTION_BOOT_HU is the head unit's own "screen back on after sleep" broadcast (EX2; REFERENCE /
 *   UNVERIFIED on IHU629G until a log shows it arrive),
 * - USER_UNLOCKED arrives once the user profile is ready after a start.
 * A running service already recovers on SCREEN_ON / USER_PRESENT (VoiceAssistantService); these
 * cover the case where the system stopped the service while the car slept.
 */
object BootTriggers {
    const val ACTION_BOOT_HU = "android.intent.action.ACTION_BOOT_HU"
    const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    /** Several of these arrive together at start-up: one service start is enough. */
    const val DEBOUNCE_MS = 1_500L

    /** Coolray 2025 (ECARX IHU730P): the head unit's own boot broadcast, seen in its package dump. */
    const val ACTION_ECARX_BOOT = "ecarx.intent.action.bt.boot.completed"

    val ACTIONS = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        ACTION_QUICKBOOT,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_USER_UNLOCKED,
        ACTION_BOOT_HU,
        ACTION_ECARX_BOOT,
    )

    fun startsService(action: String?): Boolean = action != null && action in ACTIONS

    /** True when this trigger should start the service (not within [DEBOUNCE_MS] of the last one). */
    fun accept(nowMs: Long, lastMs: Long): Boolean = lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= DEBOUNCE_MS
}

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        @Volatile private var lastStartMs = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        // MY_PACKAGE_REPLACED (06/10/2026): after an update nothing restarted the service, so
        // "Hey Geely" stayed silent until the app was opened or the voice key pressed.
        val action = intent.action
        if (!BootTriggers.startsService(action)) return
        val now = SystemClock.elapsedRealtime()
        if (!BootTriggers.accept(now, lastStartMs)) {
            Log.i(TAG, "action=$action debounced")
            return
        }
        lastStartMs = now
        if (!LicenseManager(context).isLicensed()) {
            Log.i(TAG, "action=$action nhưng app chưa có license hợp lệ")
            return
        }
        Log.i(TAG, "action=$action, starting licensed voice service")
        val serviceIntent = Intent(context, VoiceAssistantService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        }.onFailure { Log.w(TAG, "start_failed action=$action type=${it.javaClass.simpleName}") }
    }
}
