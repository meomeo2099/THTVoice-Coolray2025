package com.voicegeely.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Turns the car's music down (or pauses it, owner request 01/10/2026) while the assistant is active (owner request 30/09/2026):
 * from the start sound until the reply has been spoken. Uses transient "may duck" audio
 * focus, so the system (or the music app) lowers other audio and restores it on release;
 * the assistant's own sounds and voice keep full volume.
 *
 * Safe to call from any thread; acquire/release are idempotent.
 */
class AssistantAudioDucker(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun focusRequest(gain: Int) = AudioFocusRequest.Builder(gain)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { }
        .build()

    /** Music turned down. */
    private val duckRequest = focusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    /** Music paused; players resume by themselves when this focus is abandoned. */
    private val pauseRequest = focusRequest(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    private val lock = Any()
    private var held: AudioFocusRequest? = null

    /** [pauseMusic] = pause instead of duck (Settings → "Khi nghe lệnh"). */
    fun acquire(pauseMusic: Boolean = false) = synchronized(lock) {
        if (held != null) return@synchronized
        val request = if (pauseMusic) pauseRequest else duckRequest
        val granted = runCatching { audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
            .onFailure { Log.w(TAG, "duck_request_failed type=${it.javaClass.simpleName}") }
            .getOrDefault(false)
        if (granted) held = request
        Log.i(TAG, "duck_acquire mode=${if (pauseMusic) "pause" else "duck"} granted=$granted")
    }

    fun release() = synchronized(lock) {
        val request = held ?: return@synchronized
        runCatching { audioManager.abandonAudioFocusRequest(request) }
        held = null
        Log.i(TAG, "duck_release")
    }

    /** Sends one media "pause" key (used after release when the user asked to pause the music). */
    fun sendPauseKey() {
        runCatching {
            val now = android.os.SystemClock.uptimeMillis()
            val code = android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, code, 0))
            audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, code, 0))
        }
    }

    private companion object {
        const val TAG = "THTAudioDuck"
    }
}
