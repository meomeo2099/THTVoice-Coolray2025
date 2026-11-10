package com.voicegeely.app.voice

/**
 * Owner report 03/10/2026: after "hạ kính xuống hết" the voice key and the in-app mic button did
 * nothing any more, even after closing the app. A command (car call or reply) never returned, so
 * the session stayed "processing" and every new press was taken as a barge-in on it.
 *
 * - A command gets [COMMAND_TIMEOUT_MS]; after that the driver hears an error and the session ends.
 * - [SessionDeadline] abandons a session after [STUCK_AFTER_MS] without another key press.
 * - A native worker that ignores interruption is quarantined until it returns; it cannot publish
 *   a late result or share the recognizer with the next session.
 */
object SessionWatchdogPolicy {
    const val COMMAND_TIMEOUT_MS = 15_000L

    /** Longest normal session: 12 s listening + command timeout + a spoken reply. */
    const val STUCK_AFTER_MS = 35_000L

    fun commandTimedOut(elapsedMs: Long): Boolean = elapsedMs >= COMMAND_TIMEOUT_MS

    fun sessionStuck(running: Boolean, startedAtMs: Long, nowMs: Long): Boolean =
        running && startedAtMs > 0 && nowMs - startedAtMs > STUCK_AFTER_MS
}
