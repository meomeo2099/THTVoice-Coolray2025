package com.voicegeely.app.voice

/**
 * [S09] Lets the Voice core swap in a router built on a fresh car connection without rebuilding the
 * coordinator (pending confirmations, history, follow-up context stay). Each command uses the
 * router current at its start.
 */
class SwappableCommandExecutor(initial: VoiceCommandExecutor) : VoiceCommandExecutor {
    @Volatile var delegate: VoiceCommandExecutor = initial

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult = delegate.execute(command)
}
