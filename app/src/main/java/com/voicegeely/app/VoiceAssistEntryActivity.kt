package com.voicegeely.app

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Lets THT Voice be chosen as the head unit's assistant app (owner report 03/10/2026: the
 * steering-wheel voice key opened the phone projection app, which then asked for a USB cable).
 * The system "assistant / voice command" intent lands here; it starts a THT Voice listening
 * session exactly like the ECARX voice key (same debounce, so both paths never open two
 * sessions) and closes at once without showing anything.
 */
class VoiceAssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        Log.i("THTVoiceECARX", "assist_entry action=${intent?.action}")
        // Treated as the voice key: same routing, licence check and debounce.
        VoiceKeyLauncher.handle(this, VoiceAssistantService.ACTION_ECARX_KEY, source = "assist_intent")
        finish()
        overridePendingTransition(0, 0)
    }
}
