package com.voicegeely.app.tts

/** Cancellation is handled; a failed segment must allow the caller's TTS fallback. */
internal object Mp3PlaybackSequence {
    fun play(ids: List<String>, cancelled: () -> Boolean, playClip: (String) -> Boolean): Boolean {
        for (id in ids) {
            if (cancelled() || Thread.currentThread().isInterrupted) return true
            val ok = runCatching { playClip(id) }.getOrDefault(false)
            if (cancelled() || Thread.currentThread().isInterrupted) return true
            if (!ok) return false
        }
        return true
    }
}
