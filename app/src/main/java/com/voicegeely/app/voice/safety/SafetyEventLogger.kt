package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.VoiceCommand
import java.util.ArrayDeque

/**
 * Small in-memory audit trail for safety decisions.
 *
 * No microphone audio or raw personal data is stored. The logger keeps only
 * command type, vehicle motion snapshot, decision and final execution outcome.
 */
data class SafetyEvent(
    val timestampMs: Long,
    val command: String,
    val commandHash: String? = null,
    val requirement: SafetyRequirement,
    val decision: SafetyDecisionType,
    val speedKph: Float? = null,
    val gear: Int? = null,
    val phase: String = "EVALUATE",
    val confirmation: String? = null,
    val result: String? = null,
    val detail: String? = null
)

class SafetyEventLogger(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val capacity: Int = DEFAULT_CAPACITY
) {
    private val events = ArrayDeque<SafetyEvent>()

    @Synchronized
    fun record(
        command: VoiceCommand,
        decision: SafetyDecision,
        commandHash: String? = null,
        phase: String = "EVALUATE",
        confirmation: String? = null,
        result: String? = null,
        detail: String? = decision.reason
    ): SafetyEvent = append(
        SafetyEvent(
            timestampMs = clockMs(),
            command = command.toString(),
            commandHash = commandHash,
            requirement = decision.requirement,
            decision = decision.type,
            speedKph = decision.motion?.speedKph,
            gear = decision.motion?.gear,
            phase = phase,
            confirmation = confirmation,
            result = result,
            detail = detail
        )
    )

    @Synchronized
    fun recordParkedAction(
        actionName: String,
        decision: SafetyDecision,
        phase: String = "PARKED_ONLY",
        result: String? = null
    ): SafetyEvent = append(
        SafetyEvent(
            timestampMs = clockMs(),
            command = "ACTION:$actionName",
            requirement = SafetyRequirement.PARKED_ONLY,
            decision = decision.type,
            speedKph = decision.motion?.speedKph,
            gear = decision.motion?.gear,
            phase = phase,
            result = result,
            detail = decision.reason
        )
    )

    /** Adds the executor outcome while carrying forward the latest motion snapshot. */
    @Synchronized
    fun recordExecution(command: VoiceCommand, execution: CommandExecutionResult): SafetyEvent {
        val commandText = command.toString()
        val prior = events.toList().asReversed().firstOrNull { it.command == commandText }
        val resultName = when (execution) {
            is CommandExecutionResult.Confirmed -> "CONFIRMED"
            is CommandExecutionResult.AcceptedUnverified -> "ACCEPTED_UNVERIFIED"
            is CommandExecutionResult.Rejected -> "REJECTED"
            is CommandExecutionResult.Failed -> "FAILED"
        }
        val detail = when (execution) {
            is CommandExecutionResult.Confirmed -> execution.actualValue
            is CommandExecutionResult.AcceptedUnverified -> execution.detail
            is CommandExecutionResult.Rejected -> execution.reason
            is CommandExecutionResult.Failed -> execution.reason
        }
        return append(
            SafetyEvent(
                timestampMs = clockMs(),
                command = commandText,
                commandHash = prior?.commandHash,
                requirement = prior?.requirement ?: SafetyPolicy.requirement(command),
                decision = SafetyDecisionType.ALLOW,
                speedKph = prior?.speedKph,
                gear = prior?.gear,
                phase = "RESULT",
                confirmation = prior?.confirmation,
                result = resultName,
                detail = detail
            )
        )
    }

    @Synchronized
    fun recordEvent(event: SafetyEvent): SafetyEvent = append(event)

    @Synchronized
    fun snapshot(): List<SafetyEvent> = events.toList()

    @Synchronized
    fun latest(): SafetyEvent? = events.lastOrNull()

    @Synchronized
    fun clear() = events.clear()

    private fun append(event: SafetyEvent): SafetyEvent {
        require(capacity > 0) { "capacity must be > 0" }
        while (events.size >= capacity) events.removeFirst()
        events.addLast(event)
        return event
    }

    companion object {
        const val DEFAULT_CAPACITY = 100
    }
}

/** Shared process-local audit trail used by the production SafetyGate. */
object SafetyAuditRegistry {
    val logger: SafetyEventLogger = SafetyEventLogger()
}
