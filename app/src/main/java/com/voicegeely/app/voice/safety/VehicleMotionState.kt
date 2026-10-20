package com.voicegeely.app.voice.safety

/** Snapshot used by the safety layer. Null fields mean telemetry was unavailable. */
data class VehicleMotionState(
    val speedKph: Float?,
    val gear: Int?
) {
    val telemetryAvailable: Boolean
        get() = speedKph != null && gear != null

    val isParked: Boolean
        get() = telemetryAvailable && gear == GEAR_PARK && speedKph!! < PARKED_SPEED_KPH

    val isMovingOrNotParked: Boolean
        get() = telemetryAvailable && !isParked

    companion object {
        const val GEAR_PARK = 4
        const val PARKED_SPEED_KPH = 1.0f
    }
}

enum class SafetyDecisionType {
    ALLOW,
    REQUIRE_CONFIRMATION,
    BLOCK
}

data class SafetyDecision(
    val type: SafetyDecisionType,
    val requirement: SafetyRequirement,
    val motion: VehicleMotionState? = null,
    val reason: String? = null
)
