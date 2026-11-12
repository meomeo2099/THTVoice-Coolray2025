package com.voicegeely.app.voice

import com.voicegeely.app.voice.safety.SafetyAuditRegistry

interface CommandHistoryObserver {
    fun begin(parsed: ParsedVoiceCommand): String
    fun complete(id: String, parsed: ParsedVoiceCommand, result: CommandExecutionResult)
}

/**
 * Owns the complete spoken lifecycle of a parsed vehicle command:
 * acknowledge -> execute -> verified result.
 *
 * Handlers must never speak success on their own. They return a structured
 * [CommandExecutionResult] and this coordinator decides what is safe to say.
 */
class VoiceCommandCoordinator(
    private val executor: VoiceCommandExecutor,
    private val speech: VoiceSpeechSink,
    /** [S06] Follow-up context ("tăng nữa", "về như cũ"); null disables it. */
    val followUp: FollowUpContext? = null,
    /** Reads the car value before a fan/temperature command so "về như cũ" can restore it. */
    private val snapshotReader: (FollowUpContext.Family) -> FollowUpContext.Snapshot? = { null },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val history: CommandHistoryObserver? = null
) {
    suspend fun handle(
        parsed: ParsedVoiceCommand,
        responseMode: VoiceResponseMode = VoiceResponseMode.FULL
    ): CommandExecutionResult {
        val historyId = runCatching { history?.begin(parsed) }.getOrNull()
        if (responseMode == VoiceResponseMode.FULL) {
            speech.speak(VoiceResponseFormatter.acknowledgement(parsed.command))
        }

        val before = followUp?.let {
            FollowUpContext.restorable(parsed.command)?.let { family -> runCatching { snapshotReader(family) }.getOrNull() }
        }

        val execution = try {
            executor.execute(parsed.command)
        } catch (t: Throwable) {
            CommandExecutionResult.Failed(
                reason = t.message ?: "Lỗi không xác định.",
                throwableClass = t::class.java.name
            )
        }

        // Archive the typed result before speech: a TTS failure cannot erase a vehicle outcome.
        if (historyId != null) runCatching { history?.complete(historyId, parsed, execution) }

        SafetyAuditRegistry.logger.recordExecution(parsed.command, execution)
        followUp?.record(parsed.command, before, execution, clock())

        if (responseMode != VoiceResponseMode.SILENT) {
            speech.speak(VoiceResponseFormatter.result(parsed.command, execution))
        }

        return execution
    }
}
