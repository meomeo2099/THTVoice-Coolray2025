package com.voicegeely.app.voice

/**
 * User-facing speech policy for a command execution.
 * FULL: acknowledge the command before execution, then announce the result.
 * SHORT: announce only the result.
 * SILENT: execute without TTS feedback.
 */
enum class VoiceResponseMode {
    FULL,
    SHORT,
    SILENT
}

/** Describes how a successful command was verified. */
enum class VerificationSource {
    VEHICLE_READ_BACK,
    VEHICLE_CALLBACK,
    ANDROID_STATE,
    MEDIA_SESSION,
    NONE
}

/**
 * Execution result returned by handlers/backends.
 * Only [Confirmed] is allowed to produce a user-facing success sentence.
 */
sealed class CommandExecutionResult {
    data class Confirmed(
        val verificationSource: VerificationSource,
        val actualValue: String? = null
    ) : CommandExecutionResult()

    data class AcceptedUnverified(
        val detail: String? = null
    ) : CommandExecutionResult()

    data class Rejected(
        val reason: String
    ) : CommandExecutionResult()

    data class Failed(
        val reason: String,
        val throwableClass: String? = null
    ) : CommandExecutionResult()
}

/** Implemented by CommandRouter/handlers when they are wired to real backends. */
interface VoiceCommandExecutor {
    suspend fun execute(command: VoiceCommand): CommandExecutionResult
}

/** Adapter for the project's real TTS engine. */
fun interface VoiceSpeechSink {
    fun speak(text: String)
}
