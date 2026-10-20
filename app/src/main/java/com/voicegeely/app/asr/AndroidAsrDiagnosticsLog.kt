package com.voicegeely.app.asr

import android.util.Log

/**
 * Production diagnostics export for on-car ADB testing.
 * Formatter contains performance counters only; it never includes transcript or raw audio.
 */
object AndroidAsrDiagnosticsLog : AsrDiagnosticsPublisher {
    const val TAG = "THTVoiceASR"

    override fun publish(metrics: AsrSessionMetrics) {
        runCatching {
            Log.i(TAG, AsrDiagnosticsFormatter.format(metrics))
        }
    }
}
