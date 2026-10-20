package com.voicegeely.app.asr

/**
 * When to put the echo canceller on our microphone. AEC also softens quiet speech on some
 * devices (phone test 1.0.5), so it only runs while something is actually playing:
 * music/media or the assistant's own reply. The "Hey Geely" listener never gets it.
 */
object EchoCancelPolicy {
    fun shouldAttach(forWakeWord: Boolean, enabled: Boolean, musicActive: Boolean, replySpeaking: Boolean): Boolean =
        !forWakeWord && enabled && (musicActive || replySpeaking)
}
