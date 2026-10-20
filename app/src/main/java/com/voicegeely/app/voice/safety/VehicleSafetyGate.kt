package com.voicegeely.app.voice.safety

import com.voicegeely.app.car.CoolrayProperties
import com.voicegeely.app.car.VehiclePropertyAccess
import com.voicegeely.app.voice.VoiceCommand

/**
 * Reads live motion telemetry (CoolrayProperties speed/gear) and applies SafetyPolicy before execution.
 * Sensitive commands fail closed if SPEED or GEAR cannot be read.
 */
class VehicleSafetyGate(
    private val vehicle: VehiclePropertyAccess?,
    private val eventLogger: SafetyEventLogger = SafetyAuditRegistry.logger
) {
    fun evaluate(command: VoiceCommand): SafetyDecision {
        val requirement = SafetyPolicy.requirement(command)
        if (requirement == SafetyRequirement.SAFE) {
            return SafetyDecision(SafetyDecisionType.ALLOW, requirement)
                .also { eventLogger.record(command, it) }
        }

        val motion = readMotionState()
        val decision = if (!motion.telemetryAvailable) {
            SafetyDecision(
                SafetyDecisionType.BLOCK,
                requirement,
                motion,
                "Không đọc được tốc độ hoặc cần số để kiểm tra an toàn."
            )
        } else {
            when (requirement) {
                SafetyRequirement.SAFE -> SafetyDecision(SafetyDecisionType.ALLOW, requirement, motion)
                SafetyRequirement.CONFIRM_WHEN_MOVING -> {
                    if (motion.isParked) SafetyDecision(SafetyDecisionType.ALLOW, requirement, motion)
                    else SafetyDecision(SafetyDecisionType.REQUIRE_CONFIRMATION, requirement, motion)
                }
                SafetyRequirement.PARKED_ONLY -> {
                    if (motion.isParked) SafetyDecision(SafetyDecisionType.ALLOW, requirement, motion)
                    else SafetyDecision(
                        SafetyDecisionType.BLOCK,
                        requirement,
                        motion,
                        "Chức năng này chỉ sử dụng khi xe đang đỗ và ở số P."
                    )
                }
            }
        }
        eventLogger.record(command, decision)
        return decision
    }

    /** Re-check immediately before executing a previously confirmed command. */
    fun recheckConfirmed(command: VoiceCommand): SafetyDecision {
        val requirement = SafetyPolicy.requirement(command)
        if (requirement == SafetyRequirement.SAFE) {
            return SafetyDecision(SafetyDecisionType.ALLOW, requirement)
                .also { eventLogger.record(command, it, phase = "RECHECK") }
        }

        val motion = readMotionState()
        val decision = if (!motion.telemetryAvailable) {
            SafetyDecision(
                SafetyDecisionType.BLOCK,
                requirement,
                motion,
                "Mất dữ liệu tốc độ hoặc cần số trước khi thực hiện lệnh."
            )
        } else {
            when (requirement) {
                SafetyRequirement.SAFE -> SafetyDecision(SafetyDecisionType.ALLOW, requirement, motion)
                SafetyRequirement.CONFIRM_WHEN_MOVING -> SafetyDecision(SafetyDecisionType.ALLOW, requirement, motion)
                SafetyRequirement.PARKED_ONLY -> {
                    if (motion.isParked) SafetyDecision(SafetyDecisionType.ALLOW, requirement, motion)
                    else SafetyDecision(
                        SafetyDecisionType.BLOCK,
                        requirement,
                        motion,
                        "Xe không còn ở trạng thái đỗ an toàn."
                    )
                }
            }
        }
        eventLogger.record(command, decision, phase = "RECHECK")
        return decision
    }

    /** Reusable gate for OTA/install/dev actions that are not VoiceCommand yet. */
    fun evaluateParkedOnly(actionName: String): SafetyDecision {
        val motion = readMotionState()
        val decision = if (!motion.telemetryAvailable) {
            SafetyDecision(
                SafetyDecisionType.BLOCK,
                SafetyRequirement.PARKED_ONLY,
                motion,
                "Không đọc được trạng thái xe để thực hiện $actionName."
            )
        } else if (motion.isParked) {
            SafetyDecision(SafetyDecisionType.ALLOW, SafetyRequirement.PARKED_ONLY, motion)
        } else {
            SafetyDecision(
                SafetyDecisionType.BLOCK,
                SafetyRequirement.PARKED_ONLY,
                motion,
                "$actionName chỉ được thực hiện khi xe ở số P và tốc độ dưới 1 km/h."
            )
        }
        eventLogger.recordParkedAction(actionName, decision)
        return decision
    }

    fun readMotionState(): VehicleMotionState {
        val access = vehicle ?: return VehicleMotionState(null, null)
        val speed = try {
            access.getFloatProperty(CoolrayProperties.VEHICLE_SPEED, CoolrayProperties.VEHICLE_SPEED_AREA)
                .takeIf { it.isFinite() && it >= 0f }
        } catch (_: Throwable) {
            null
        }
        val gear = try {
            access.getIntPropertyOrNull(CoolrayProperties.GEAR, CoolrayProperties.GEAR_AREA)
        } catch (_: Throwable) {
            null
        }
        return VehicleMotionState(speed, gear)
    }
}
