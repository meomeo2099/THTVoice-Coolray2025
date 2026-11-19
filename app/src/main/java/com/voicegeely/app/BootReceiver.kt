package com.voicegeely.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
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

    /**
     * Coolray 2025 (car check 19/11/2026): the head unit is so busy at start-up that BOOT_COMPLETED
     * reached THT Voice ~1 min 40 s after power-on, and the voice key did nothing until the app was
     * opened. LOCKED_BOOT_COMPLETED arrives first (THT StatusBar starts on it). The receiver then waits
     * for the user's storage to unlock (no PIN on the car: seconds) before reading the license.
     */
    const val ACTION_LOCKED_BOOT = Intent.ACTION_LOCKED_BOOT_COMPLETED
    /** Longest wait for user storage after LOCKED_BOOT_COMPLETED; BOOT_COMPLETED still follows. */
    const val UNLOCK_WAIT_MS = 30_000L

    fun startsService(action: String?): Boolean = action != null && (action in ACTIONS || action == ACTION_LOCKED_BOOT)

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
        val users = context.getSystemService(UserManager::class.java)
        if (users != null && !users.isUserUnlocked) {
            // Early boot: wait for user storage on a worker thread, then start (license is in it).
            val pending = goAsync()
            val app = context.applicationContext
            Thread {
                try {
                    val until = SystemClock.elapsedRealtime() + BootTriggers.UNLOCK_WAIT_MS
                    while (!users.isUserUnlocked && SystemClock.elapsedRealtime() < until) Thread.sleep(250)
                    if (users.isUserUnlocked) start(app, action) else {
                        Log.i(TAG, "action=$action storage still locked; BOOT_COMPLETED will start it")
                        lastStartMs = 0L
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    pending.finish()
                }
            }.apply { name = "THTVoice-BootWait"; isDaemon = true }.start()
            return
        }
        start(context, action)
    }

    private fun start(context: Context, action: String?) {
        // Storage is unlocked here; the Application may have deferred the history database (no-op if open).
        com.voicegeely.app.history.VoiceHistory.install(context.applicationContext)
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
