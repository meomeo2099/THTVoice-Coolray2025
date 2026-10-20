package com.voicegeely.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-local event bus used by VoiceAssistantService and MainActivity.
 *
 * This replaces deprecated LocalBroadcastManager without exposing voice runtime
 * events through Android system broadcasts. Delivery is posted to the main
 * thread, matching the UI-thread behavior expected by MainActivity.
 */
object VoiceServiceEventBus {
    private data class Registration(
        val receiver: BroadcastReceiver,
        val filter: IntentFilter
    )

    private val registrations = CopyOnWriteArrayList<Registration>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        unregisterReceiver(receiver)
        registrations += Registration(receiver, filter)
    }

    fun unregisterReceiver(receiver: BroadcastReceiver) {
        registrations.removeAll { it.receiver === receiver }
    }

    fun sendBroadcast(context: Context, intent: Intent): Boolean {
        val action = intent.action ?: return false
        val targets = registrations.filter { it.filter.hasAction(action) }
        if (targets.isEmpty()) return false

        val appContext = context.applicationContext
        targets.forEach { registration ->
            val event = Intent(intent)
            mainHandler.post {
                if (registrations.contains(registration)) {
                    registration.receiver.onReceive(appContext, event)
                }
            }
        }
        return true
    }
}
