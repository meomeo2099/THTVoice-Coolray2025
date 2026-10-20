package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.ParsedVoiceCommand
import java.security.MessageDigest

/** One in-memory safety confirmation. It is intentionally short-lived. */
data class PendingSafetyCommand(
    val parsed: ParsedVoiceCommand,
    val commandHash: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val motionAtRequest: VehicleMotionState
) {
    fun isExpired(nowMs: Long): Boolean = nowMs >= expiresAtMs
}

class PendingCommandStore(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = DEFAULT_TTL_MS
) {
    @Volatile private var pending: PendingSafetyCommand? = null

    @Synchronized
    fun put(parsed: ParsedVoiceCommand, motion: VehicleMotionState): PendingSafetyCommand {
        val now = clockMs()
        return PendingSafetyCommand(
            parsed = parsed,
            commandHash = hash(parsed.command.toString()),
            createdAtMs = now,
            expiresAtMs = now + ttlMs,
            motionAtRequest = motion
        ).also { pending = it }
    }

    @Synchronized
    fun peek(): PendingSafetyCommand? {
        val current = pending ?: return null
        if (current.isExpired(clockMs())) {
            pending = null
            return null
        }
        return current
    }

    @Synchronized
    fun take(): PendingSafetyCommand? {
        val current = peek() ?: return null
        pending = null
        return current
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    companion object {
        const val DEFAULT_TTL_MS = 8_000L

        private fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
