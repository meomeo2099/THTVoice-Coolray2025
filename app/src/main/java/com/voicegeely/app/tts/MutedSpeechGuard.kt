package com.voicegeely.app.tts

/**
 * Keeps assistant speech audible while the media stream is muted (owner decision 29/09/2026:
 * "tắt tiếng" silences music, not the assistant).
 *
 * Assistant speech shares the media stream, so when that stream is muted the guard first takes
 * transient exclusive audio focus (players pause on transient loss), unmutes only for the
 * utterance, then restores the mute and releases focus — even if playback throws. When focus is
 * refused (for example during a phone call) the stream stays muted and the utterance is silent,
 * so music is never exposed. Hardware behaviour: MED-04 `NOT_TESTED`.
 */
class MutedSpeechGuard(private val audio: Audio) {
    interface Audio {
        fun isMediaMuted(): Boolean
        fun requestSpeechFocus(): Boolean
        fun abandonSpeechFocus()
        fun setMediaMuted(muted: Boolean)
    }

    enum class Mode { NOT_MUTED, UNMUTED_FOR_SPEECH, FOCUS_REFUSED_STAYS_MUTED }

    @Volatile var lastMode: Mode = Mode.NOT_MUTED
        private set

    fun <T> speak(block: () -> T): T {
        val muted = runCatching { audio.isMediaMuted() }.getOrDefault(false)
        if (!muted) {
            lastMode = Mode.NOT_MUTED
            return block()
        }
        val focused = runCatching { audio.requestSpeechFocus() }.getOrDefault(false)
        if (!focused) {
            lastMode = Mode.FOCUS_REFUSED_STAYS_MUTED
            return block()
        }
        lastMode = Mode.UNMUTED_FOR_SPEECH
        try {
            runCatching { audio.setMediaMuted(false) }
            return block()
        } finally {
            runCatching { audio.setMediaMuted(true) }
            runCatching { audio.abandonSpeechFocus() }
        }
    }
}
