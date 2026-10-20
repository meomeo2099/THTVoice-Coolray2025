package com.voicegeely.app.voice

/** Pure policy for deciding whether Android audio activity proves a media command. */
object MediaPlaybackStateVerifier {
    fun expectedActive(action: MediaAction, beforeActive: Boolean?): Boolean? = when (action) {
        MediaAction.PLAY -> true
        MediaAction.PAUSE -> false
        MediaAction.TOGGLE -> beforeActive?.not()
        MediaAction.NEXT,
        MediaAction.PREVIOUS,
        MediaAction.VOLUME_UP,
        MediaAction.VOLUME_DOWN,
        MediaAction.MUTE,
        MediaAction.UNMUTE -> null
    }

    fun isConfirmed(action: MediaAction, beforeActive: Boolean?, afterActive: Boolean): Boolean {
        val expected = expectedActive(action, beforeActive) ?: return false
        return afterActive == expected
    }
}
