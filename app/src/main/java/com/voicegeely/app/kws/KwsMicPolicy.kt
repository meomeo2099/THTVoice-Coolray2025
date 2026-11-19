package com.voicegeely.app.kws

/**
 * Wake-word microphone rules (owner report 03/10/2026: "Hey Geely" stopped answering).
 * - Give the mic up during a phone call (also CarPlay / Android Auto calls) or when Android
 *   silences our recording because a higher-priority app (phone assistant) is listening.
 * - When the mic cannot be opened or dies, try again later with a growing, bounded delay
 *   instead of staying deaf until the next voice-key press.
 */
object KwsMicPolicy {
    // android.media.AudioManager.MODE_* values (kept here so the policy is unit-testable).
    const val MODE_NORMAL = 0
    const val MODE_RINGTONE = 1
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3
    const val MODE_CALL_SCREENING = 4

    const val CALL_RECHECK_MS = 3_000L
    private val RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L, 30_000L)
    const val MAX_RETRY_DELAY_MS = 60_000L

    /** Listening this long without an error counts as healthy again (retry delay starts over). */
    const val HEALTHY_LISTEN_MS = 30_000L

    fun callActive(audioMode: Int): Boolean =
        audioMode == MODE_IN_CALL || audioMode == MODE_IN_COMMUNICATION ||
            audioMode == MODE_CALL_SCREENING || audioMode == MODE_RINGTONE

    // android.media.AudioAttributes.USAGE_* values for call audio (unit-testable).
    const val USAGE_VOICE_COMMUNICATION = 2
    const val USAGE_VOICE_COMMUNICATION_SIGNALLING = 3
    const val USAGE_NOTIFICATION_RINGTONE = 6

    /** Playback that means a phone call is ringing or in progress (Bluetooth phone, CarPlay). */
    fun callUsage(usage: Int): Boolean =
        usage == USAGE_VOICE_COMMUNICATION || usage == USAGE_VOICE_COMMUNICATION_SIGNALLING ||
            usage == USAGE_NOTIFICATION_RINGTONE

    fun shouldYield(audioMode: Int, selfSilenced: Boolean): Boolean = callActive(audioMode) || selfSilenced

    fun retryDelayMs(failures: Int): Long =
        if (failures <= 0) RETRY_DELAYS_MS[0] else RETRY_DELAYS_MS.getOrElse(failures) { MAX_RETRY_DELAY_MS }

    /** A running wake-word loop with no audio for this long after the car wakes is restarted. */
    const val STALE_LOOP_MS = 3_000L

    fun loopStale(now: Long, lastFrameAt: Long): Boolean = lastFrameAt > 0 && now - lastFrameAt > STALE_LOOP_MS
}
