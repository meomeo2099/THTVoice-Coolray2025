package com.voicegeely.app.asr

import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Echo cancellation on our microphone (IHU629G probe 26/09/2026: AEC exists and enables on the
 * head unit, noise suppression does not). Removes the car's own music/replies from the mic so
 * commands are heard while music plays. Best effort: no AEC → the mic works as before.
 */
object MicEchoCanceler {
    private const val TAG = "THTVoiceMic"
    private val active = ConcurrentHashMap<Int, AcousticEchoCanceler>()

    fun attach(recorder: AudioRecord) {
        val session = recorder.audioSessionId
        if (!runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)) {
            Log.i(TAG, "aec unavailable session=$session")
            return
        }
        runCatching {
            val aec = AcousticEchoCanceler.create(session) ?: return
            aec.enabled = true
            active.put(session, aec)?.let { runCatching { it.release() } }
            Log.i(TAG, "aec enabled=${aec.enabled} session=$session")
        }.onFailure { Log.w(TAG, "aec_failed type=${it.javaClass.simpleName}") }
    }

    fun release(recorder: AudioRecord) {
        val session = runCatching { recorder.audioSessionId }.getOrNull() ?: return
        active.remove(session)?.let { runCatching { it.release() } }
    }
}
