package com.voicegeely.app.asr

import android.os.Debug
import android.os.Process

/** Cheap process-level probe used only at ASR session boundaries. */
class AndroidAsrRuntimeProbe : AsrRuntimeProbe {
    override fun snapshot(): AsrRuntimeSnapshot = AsrRuntimeSnapshot(
        processCpuMs = runCatching { Process.getElapsedCpuTime() }.getOrNull(),
        pssKb = runCatching { Debug.getPss().toLong() }.getOrNull()
    )
}
