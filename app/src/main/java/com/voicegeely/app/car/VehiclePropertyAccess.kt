package com.voicegeely.app.car

data class VehicleIntRange(
    val minValue: Int,
    val maxValue: Int,
    val source: String = "VHAL"
) {
    val isValid: Boolean get() = maxValue > minValue
}

enum class VehicleWriteStatus {
    SUCCESS,
    MOCK_SUCCESS,
    NO_MANAGER,
    PROPERTY_UNAVAILABLE,
    SECURITY_DENIED,
    WRITE_FAILED,
    READBACK_FAILED,
    READBACK_MISMATCH
}

data class VehicleWriteResult(
    val accepted: Boolean,
    val confirmed: Boolean,
    val status: VehicleWriteStatus,
    val propertyId: Int,
    val areaId: Int,
    val requestedValue: Int,
    val actualValue: Int? = null,
    val message: String = ""
)

/** Abstraction for real Coolray VHAL and development/mock implementations. */
interface VehiclePropertyAccess {
    val isMock: Boolean
        get() = false

    fun getIntProperty(propertyId: Int, areaId: Int): Int
    fun getBoolProperty(propertyId: Int, areaId: Int): Boolean
    fun getFloatProperty(propertyId: Int, areaId: Int): Float

    fun getIntPropertyOrNull(propertyId: Int, areaId: Int): Int? {
        val value = getIntProperty(propertyId, areaId)
        return value.takeUnless { it == -1 }
    }

    /** Nullable bool read so OFF can be distinguished from a backend read failure. */
    fun getBoolPropertyOrNull(propertyId: Int, areaId: Int): Boolean? =
        getBoolProperty(propertyId, areaId)

    fun getIntRange(propertyId: Int, areaId: Int): VehicleIntRange? = null

    fun setIntProperty(propertyId: Int, areaId: Int, value: Int): Boolean
    fun setBoolProperty(propertyId: Int, areaId: Int, value: Boolean): Boolean
    fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean

    fun setIntPropertyVerified(
        propertyId: Int,
        areaId: Int,
        value: Int,
        readBackDelayMs: Long = 120L,
        tolerance: Int = 1
    ): VehicleWriteResult {
        val accepted = setIntProperty(propertyId, areaId, value)
        if (!accepted) {
            return VehicleWriteResult(false, false, VehicleWriteStatus.WRITE_FAILED, propertyId, areaId, value, message = "VHAL rejected write")
        }
        if (isMock) {
            return VehicleWriteResult(true, true, VehicleWriteStatus.MOCK_SUCCESS, propertyId, areaId, value, value, "MOCK write stored")
        }
        if (readBackDelayMs > 0) {
            try { Thread.sleep(readBackDelayMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        val actual = getIntPropertyOrNull(propertyId, areaId)
            ?: return VehicleWriteResult(true, false, VehicleWriteStatus.READBACK_FAILED, propertyId, areaId, value, message = "Write accepted but read-back failed")
        val confirmed = kotlin.math.abs(actual - value) <= tolerance
        return VehicleWriteResult(
            true,
            confirmed,
            if (confirmed) VehicleWriteStatus.SUCCESS else VehicleWriteStatus.READBACK_MISMATCH,
            propertyId,
            areaId,
            value,
            actual,
            if (confirmed) "Read-back confirmed" else "Read-back differs from requested value"
        )
    }

    fun disconnect()
}
