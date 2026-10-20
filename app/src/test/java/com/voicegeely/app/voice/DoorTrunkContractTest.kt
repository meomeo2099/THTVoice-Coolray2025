package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorTrunkContractTest {
    @Test fun referenceIdentifiersPreserveObservedBehaviorWithoutInventingApi() {
        val lock = DoorTrunkContractCatalog.spec(DoorTrunkAction.LOCK_ALL_DOORS)
        val unlock = DoorTrunkContractCatalog.spec(DoorTrunkAction.UNLOCK_ALL_DOORS)
        val front = DoorTrunkContractCatalog.spec(DoorTrunkAction.RELEASE_FRONT_TRUNK)
        val rear = DoorTrunkContractCatalog.spec(DoorTrunkAction.UNLOCK_REAR_TRUNK)

        assertEquals("set_door_lock", lock.referenceActionId)
        assertEquals(lock.referenceActionId, unlock.referenceActionId)
        assertEquals("open_front_trunk", front.referenceActionId)
        assertEquals("unlock_rear_trunk", rear.referenceActionId)
        assertEquals("DOOR_LOCK_STATE areas 1/4/16/64", lock.independentReadBackHint)
        assertNull(front.independentReadBackHint)
        assertNull(rear.independentReadBackHint)
        assertTrue(rear.requiresManualCompletion)
        assertFalse(front.requiresManualCompletion)
    }

    @Test fun readableDoorLockTelemetryAloneNeverBecomesAnActuator() {
        val snapshot = DoorTrunkCapabilitySnapshot(
            action = DoorTrunkAction.LOCK_ALL_DOORS,
            independentReadBackIdentity = "DOOR_LOCK_STATE areas 1/4/16/64",
            parkStateReadable = true,
            stationaryStateReadable = true
        )

        assertEquals(
            DoorTrunkContractClassifier.State.REFERENCE_ONLY,
            DoorTrunkContractClassifier.classify(snapshot)
        )
    }

    @Test fun exactBackendWithoutPermissionContractStaysBlocked() {
        val snapshot = DoorTrunkCapabilitySnapshot(
            action = DoorTrunkAction.UNLOCK_ALL_DOORS,
            backendIdentity = "verified.vendor.Backend#unlockAll",
            actuatorCallable = true,
            independentReadBackIdentity = "DOOR_LOCK_STATE areas 1/4/16/64",
            parkStateReadable = true,
            stationaryStateReadable = true
        )

        assertEquals(
            DoorTrunkContractClassifier.State.PRIVILEGE_CONTRACT_UNKNOWN,
            DoorTrunkContractClassifier.classify(snapshot)
        )
    }

    @Test fun deniedPrivilegeNeverAdvancesToHardwareValidation() {
        val snapshot = DoorTrunkCapabilitySnapshot(
            action = DoorTrunkAction.RELEASE_FRONT_TRUNK,
            backendIdentity = "verified.vendor.Backend#releaseFront",
            permissionIdentity = "verified.vendor.permission.BODY_CONTROL",
            permissionGranted = false,
            actuatorCallable = true,
            independentReadBackIdentity = "verified front-latch state",
            parkStateReadable = true,
            stationaryStateReadable = true
        )

        assertEquals(
            DoorTrunkContractClassifier.State.PRIVILEGE_BLOCKED,
            DoorTrunkContractClassifier.classify(snapshot)
        )
    }

    @Test fun callableActuatorWithoutIndependentReadBackStaysUnresolved() {
        val snapshot = DoorTrunkCapabilitySnapshot(
            action = DoorTrunkAction.UNLOCK_REAR_TRUNK,
            backendIdentity = "verified.vendor.Backend#unlockRear",
            permissionIdentity = "verified.vendor.permission.BODY_CONTROL",
            permissionGranted = true,
            actuatorCallable = true,
            parkStateReadable = true,
            stationaryStateReadable = true
        )

        assertEquals(
            DoorTrunkContractClassifier.State.READ_BACK_UNRESOLVED,
            DoorTrunkContractClassifier.classify(snapshot)
        )
    }

    @Test fun missingParkOrStationaryTelemetryFailsClosed() {
        val snapshot = DoorTrunkCapabilitySnapshot(
            action = DoorTrunkAction.RELEASE_FRONT_TRUNK,
            backendIdentity = "verified.vendor.Backend#releaseFront",
            permissionIdentity = "verified.vendor.permission.BODY_CONTROL",
            permissionGranted = true,
            actuatorCallable = true,
            independentReadBackIdentity = "verified front-latch state",
            parkStateReadable = true,
            stationaryStateReadable = false
        )

        assertEquals(
            DoorTrunkContractClassifier.State.SAFETY_STATE_UNRESOLVED,
            DoorTrunkContractClassifier.classify(snapshot)
        )
    }

    @Test fun completeContractIsOnlyReadyForHardwareValidation() {
        val snapshot = DoorTrunkCapabilitySnapshot(
            action = DoorTrunkAction.LOCK_ALL_DOORS,
            backendIdentity = "verified.vendor.Backend#lockAll",
            permissionIdentity = "verified.vendor.permission.BODY_CONTROL",
            permissionGranted = true,
            actuatorCallable = true,
            independentReadBackIdentity = "DOOR_LOCK_STATE areas 1/4/16/64",
            parkStateReadable = true,
            stationaryStateReadable = true
        )

        assertEquals(
            DoorTrunkContractClassifier.State.READY_FOR_HARDWARE_VALIDATION,
            DoorTrunkContractClassifier.classify(snapshot)
        )
    }
}
