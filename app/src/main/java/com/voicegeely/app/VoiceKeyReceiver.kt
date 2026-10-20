package com.voicegeely.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.voicegeely.app.security.LicenseManager
import com.voicegeely.app.settings.UserVoicePreferences

/**
 * Nhận nút giọng nói trên xe kể cả khi giao diện chính đang đóng hoặc app khác đang ở màn hình.
 * Khai báo trong manifest và đồng thời được Voice core đăng ký lúc chạy: Android 8+ không giao
 * broadcast ngầm định cho receiver manifest trừ khi bên gửi chỉ định gói, còn receiver đăng ký lúc
 * chạy luôn nhận được. [VoiceKeyDebounce] chặn việc hai đường cùng mở phiên.
 */
class VoiceKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        recordSignal(intent)
        if (VoiceKeyActions.isVoiceKey(intent?.action) && yieldToProjection(context)) {
            // Settings: phone assistant answers the key while Android Auto / CarPlay is connected.
            Log.i(TAG, "ecarx_key_yield_to_projection")
            return
        }
        val handled = VoiceKeyLauncher.handle(context, intent?.action, source = if (isInitialStickyBroadcast) "sticky" else "broadcast")
        // The key is ours (owner report 03/10/2026: the steering-wheel key also opened the phone
        // projection app, which shows "connect your phone with a USB cable"). For an ordered
        // broadcast, stop it here so lower-priority receivers do not react to the same press.
        // IHU629G probe (26/09/2026): the keyserver sends it as a plain implicit broadcast, so this
        // usually does nothing; the real fix is THT Voice as default assistant (Settings button).
        if (handled && isOrderedBroadcast) {
            abortBroadcast()
            Log.i(TAG, "ecarx_key_consumed")
        }
    }

    /** Action, ordered flag and every extra (key=value) of the voice-key broadcast, for Kiểm tra nút voice. */
    private fun recordSignal(intent: Intent?) {
        runCatching {
            val action = intent?.action ?: return
            if (!VoiceKeyActions.isVoiceKey(action)) return
            val extras = intent.extras?.let { b -> b.keySet().sorted().joinToString(" ") { k -> "$k=${b.get(k)}" } }.orEmpty()
            val variant = if (action == VoiceKeyActions.ECARX) "" else " (actiOn)"
            VoiceKeySignalLog.add(
                SystemClock.elapsedRealtime(), "broadcast$variant",
                listOf(if (isOrderedBroadcast) "ordered" else "thường", extras.ifBlank { "không có extra" }).joinToString("  ")
            )
            Log.i(TAG, "voice_key_signal action=$action ordered=$isOrderedBroadcast extras=$extras")
        }
    }

    private fun yieldToProjection(context: Context): Boolean = runCatching {
        VoiceKeyProjectionPolicy.yieldToProjection(
            alwaysThtVoice = UserVoicePreferences(context).voiceKeyAlwaysTht,
            connected = ProjectionDetector.connectedUsb(context)
        )
    }.getOrDefault(false)

    companion object {
        private const val TAG = "THTVoiceECARX"
    }
}

/**
 * One place that turns a voice-key press (ECARX broadcast, or the system "assistant" intent) into
 * a THT Voice listening session. Returns true when the press was handled by THT Voice.
 */
object VoiceKeyLauncher {
    private const val TAG = "THTVoiceECARX"

    fun handle(context: Context, incomingAction: String?, source: String): Boolean {
        val route = VoiceKeyRoutingPolicy.decide(
            incomingAction = incomingAction,
            expectedAction = VoiceAssistantService.ACTION_ECARX_KEY
        ) {
            runCatching { LicenseManager(context).isLicensed() }
                .onFailure { Log.w(TAG, "license_check_failed type=${it.javaClass.simpleName}") }
                .getOrDefault(false)
        }

        if (route != VoiceKeyRouteDecision.IGNORE && !VoiceKeyDebounce.shared.accept(SystemClock.elapsedRealtime())) {
            Log.i(TAG, "ecarx_key_debounced path=$source")
            return true
        }

        when (route) {
            VoiceKeyRouteDecision.IGNORE -> {
                Log.d(TAG, "ignored_action=$incomingAction")
                return false
            }

            VoiceKeyRouteDecision.ACTIVATION -> {
                Log.i(TAG, "ecarx_key_received path=$source")
                Log.i(TAG, "ecarx_route=ACTIVATION reason=LICENSE_INACTIVE")
                runCatching {
                    context.startActivity(Intent(context, ActivationActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    })
                }.onFailure {
                    Log.e(TAG, "activation_start_failed type=${it.javaClass.simpleName}")
                }
                return true
            }

            VoiceKeyRouteDecision.VOICE_SERVICE -> {
                Log.i(TAG, "ecarx_key_received path=$source")
            }
        }

        // Voice core already running: stay in the current app, just show the popup and listen.
        if (VoiceAssistantService.triggerFromVoiceKey()) {
            Log.i(TAG, "ecarx_route=IN_PROCESS")
            return true
        }

        // Cold start only: the invisible bridge activity (own task, never raises THT Voice) lets
        // Android grant the new foreground service microphone access.
        runCatching {
            context.startActivity(Intent(context, VoiceWakeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            })
            Log.i(TAG, "ecarx_wake_activity_started")
        }.onFailure {
            Log.w(TAG, "ecarx_wake_activity_failed type=${it.javaClass.simpleName}")
        }

        val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
            this.action = VoiceAssistantService.ACTION_TRIGGER_RECOGNITION
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "ecarx_route=VOICE_SERVICE action=TRIGGER_RECOGNITION")
        }.onFailure {
            Log.e(TAG, "ecarx_voice_service_start_failed type=${it.javaClass.simpleName}")
        }
        return true
    }
}
