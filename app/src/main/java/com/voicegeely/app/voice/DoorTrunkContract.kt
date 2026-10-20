package com.voicegeely.app.voice

/**
 * Evidence-only contract for door/lock/trunk actions (parser side; Coolray routes only the tailgate).
 *
 * This file deliberately exposes no execute/write API. Reference action identifiers describe
 * observed behavior only; they are not Android intents, VHAL properties, or OEM function IDs.
 */
enum class DoorTrunkAction {
    LOCK_ALL_DOORS,
    UNLOCK_ALL_DOORS,
    RELEASE_FRONT_TRUNK,
    UNLOCK_REAR_TRUNK
}

data class DoorTrunkActionSpec(
    val referenceActionId: String,
    val expectedEffect: String,
    val independentReadBackHint: String?,
    val requiresPark: Boolean = true,
    val requiresStationary: Boolean = true,
    val requiresManualCompletion: Boolean = false
)

object DoorTrunkContractCatalog {
    fun spec(action: DoorTrunkAction): DoorTrunkActionSpec = when (action) {
        DoorTrunkAction.LOCK_ALL_DOORS -> DoorTrunkActionSpec(
            referenceActionId = "set_door_lock",
            expectedEffect = "lock all four passenger doors",
            independentReadBackHint = "DOOR_LOCK_STATE areas 1/4/16/64"
        )
        DoorTrunkAction.UNLOCK_ALL_DOORS -> DoorTrunkActionSpec(
            referenceActionId = "set_door_lock",
            expectedEffect = "unlock all four passenger doors",
            independentReadBackHint = "DOOR_LOCK_STATE areas 1/4/16/64"
        )
        DoorTrunkAction.RELEASE_FRONT_TRUNK -> DoorTrunkActionSpec(
            referenceActionId = "open_front_trunk",
            expectedEffect = "release the front-trunk latch",
            independentReadBackHint = null
        )
        DoorTrunkAction.UNLOCK_REAR_TRUNK -> DoorTrunkActionSpec(
            referenceActionId = "unlock_rear_trunk",
            expectedEffect = "unlock the mechanical rear-trunk release",
            independentReadBackHint = null,
            requiresManualCompletion = true
        )
    }
}

data class DoorTrunkCapabilitySnapshot(
    val action: DoorTrunkAction,
    val backendIdentity: String? = null,
    val permissionIdentity: String? = null,
    val permissionGranted: Boolean = false,
    val actuatorCallable: Boolean = false,
    val independentReadBackIdentity: String? = null,
    val parkStateReadable: Boolean = false,
    val stationaryStateReadable: Boolean = false
)

/**
 * A READY result means only that an exact backend contract is complete enough for a dedicated
 * hardware-validation branch. It is never equivalent to an executed or confirmed vehicle action.
 */
object DoorTrunkContractClassifier {
    enum class State {
        REFERENCE_ONLY,
        PRIVILEGE_CONTRACT_UNKNOWN,
        PRIVILEGE_BLOCKED,
        ACTUATOR_UNAVAILABLE,
        READ_BACK_UNRESOLVED,
        SAFETY_STATE_UNRESOLVED,
        READY_FOR_HARDWARE_VALIDATION
    }

    fun classify(snapshot: DoorTrunkCapabilitySnapshot): State {
        if (snapshot.backendIdentity.isNullOrBlank()) return State.REFERENCE_ONLY
        if (snapshot.permissionIdentity.isNullOrBlank()) return State.PRIVILEGE_CONTRACT_UNKNOWN
        if (!snapshot.permissionGranted) return State.PRIVILEGE_BLOCKED
        if (!snapshot.actuatorCallable) return State.ACTUATOR_UNAVAILABLE
        if (snapshot.independentReadBackIdentity.isNullOrBlank()) {
            return State.READ_BACK_UNRESOLVED
        }

        val spec = DoorTrunkContractCatalog.spec(snapshot.action)
        if ((spec.requiresPark && !snapshot.parkStateReadable) ||
            (spec.requiresStationary && !snapshot.stationaryStateReadable)
        ) {
            return State.SAFETY_STATE_UNRESOLVED
        }

        return State.READY_FOR_HARDWARE_VALIDATION
    }
}
