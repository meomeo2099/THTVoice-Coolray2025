package com.voicegeely.app

import android.app.Application
import com.voicegeely.app.voice.AndroidRuntimeContext

/** Installs only process-wide application context needed by read-only Android probes. */
class ThtVoiceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidRuntimeContext.install(this)
    }
}
