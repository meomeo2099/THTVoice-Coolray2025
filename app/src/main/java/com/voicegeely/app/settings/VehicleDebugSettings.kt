package com.voicegeely.app.settings

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VehicleDebugSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var mockVhalEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOCK_VHAL, false)
        set(value) = prefs.edit().putBoolean(KEY_MOCK_VHAL, value).apply()
    fun appendLog(message: String) {
        val stamped = "${timestamp()}  $message"
        synchronized(LOG_LOCK) {
            val old = prefs.getString(KEY_LOG, "").orEmpty()
            val lines = (old.lineSequence().filter { it.isNotBlank() }.toList() + stamped).takeLast(MAX_LOG_LINES)
            prefs.edit().putString(KEY_LOG, lines.joinToString("\n")).apply()
        }
    }
    fun readLog(): String = prefs.getString(KEY_LOG, "").orEmpty()
    fun clearLog() { prefs.edit().remove(KEY_LOG).apply() }
    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    companion object {
        private const val PREFS = "tht_voice_vehicle_debug"
        private const val KEY_MOCK_VHAL = "mock_vhal_enabled"
        private const val KEY_LOG = "mock_vhal_log"
        private const val MAX_LOG_LINES = 80
        private val LOG_LOCK = Any()
    }
}
