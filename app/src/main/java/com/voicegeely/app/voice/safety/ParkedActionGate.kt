package com.voicegeely.app.voice.safety

/**
 * Reusable PARKED_ONLY boundary for actions that are not VoiceCommand yet,
 * such as OTA installation, APK installation and developer maintenance.
 */
class ParkedActionGate(
    private val vehicleSafetyGate: VehicleSafetyGate
) {
    fun evaluate(actionName: String): SafetyDecision =
        vehicleSafetyGate.evaluateParkedOnly(actionName)

    fun evaluate(action: ParkedAction): SafetyDecision = evaluate(action.displayName)

    inline fun <T> runIfParked(
        actionName: String,
        onBlocked: (SafetyDecision) -> T,
        onAllowed: () -> T
    ): T {
        val decision = evaluate(actionName)
        return if (decision.type == SafetyDecisionType.ALLOW) onAllowed() else onBlocked(decision)
    }

    inline fun <T> runIfParked(
        action: ParkedAction,
        onBlocked: (SafetyDecision) -> T,
        onAllowed: () -> T
    ): T = runIfParked(action.displayName, onBlocked, onAllowed)
}
