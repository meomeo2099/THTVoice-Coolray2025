package com.voicegeely.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.voicegeely.app.security.LicenseManager

class VoiceWakeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var readyObservedAt = 0L
    private val startedAt = System.currentTimeMillis()

    private val finishWhenReady = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (VoiceAssistantService.isServiceReady()) {
                if (readyObservedAt == 0L) {
                    readyObservedAt = now
                    Log.i(TAG, "service_ready_observed elapsedMs=${now - startedAt}")
                }
                if (now - readyObservedAt >= READY_SETTLE_MS) {
                    Log.i(TAG, "finish reason=SERVICE_READY elapsedMs=${now - startedAt}")
                    finishWithoutAnimation()
                    return
                }
            }
            if (now - startedAt >= MAX_BRIDGE_LIFETIME_MS) {
                Log.w(TAG, "finish reason=TIMEOUT serviceReady=${VoiceAssistantService.isServiceReady()}")
                finishWithoutAnimation()
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        Log.i(TAG, "created")

        val licensed = runCatching { LicenseManager(this).isLicensed() }
            .onFailure { Log.w(TAG, "license_check_failed type=${it.javaClass.simpleName}") }
            .getOrDefault(false)
        if (!licensed) {
            Log.i(TAG, "finish reason=LICENSE_INACTIVE")
            finishWithoutAnimation()
            return
        }

        handler.post(finishWhenReady)
    }

    private fun finishWithoutAnimation() {
        if (!isFinishing) finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishWhenReady)
        Log.i(TAG, "destroyed elapsedMs=${System.currentTimeMillis() - startedAt}")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "THTVoiceWakeBridge"
        private const val READY_SETTLE_MS = 1200L
        private const val MAX_BRIDGE_LIFETIME_MS = 10000L
        private const val POLL_INTERVAL_MS = 200L
    }
}
