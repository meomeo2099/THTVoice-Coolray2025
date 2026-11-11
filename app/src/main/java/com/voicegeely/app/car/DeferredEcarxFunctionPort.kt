package com.voicegeely.app.car

/** Retries a body API that was unavailable during head-unit startup, independently of VHAL. */
class DeferredEcarxFunctionPort(
    private val connect: () -> EcarxFunctionPort?,
    private val clock: () -> Long,
    private val retryDelayMs: Long = 5_000L
) : EcarxFunctionPort {
    private var connected: EcarxFunctionPort? = null
    private var attemptedAt: Long? = null

    @Synchronized
    private fun port(): EcarxFunctionPort? {
        connected?.let { return it }
        val now = clock()
        val previous = attemptedAt
        if (previous != null && now - previous < retryDelayMs) return null
        attemptedAt = now
        connected = connect()
        return connected
    }

    override fun isSupported(functionId: Int, zone: Int): Boolean? = port()?.isSupported(functionId, zone)
    override fun getInt(functionId: Int, zone: Int): Int? = port()?.getInt(functionId, zone)
    override fun setInt(functionId: Int, zone: Int, value: Int): Boolean = port()?.setInt(functionId, zone, value) ?: false
    override fun getFloat(functionId: Int, zone: Int): Float? = port()?.getFloat(functionId, zone)
    override fun setFloat(functionId: Int, zone: Int, value: Float): Boolean = port()?.setFloat(functionId, zone, value) ?: false
    override fun sendBytesToMcu(propertyId: Int, bytes: ByteArray): Boolean = port()?.sendBytesToMcu(propertyId, bytes) ?: false
    override fun getIntByProp(propertyId: Int, area: Int): Int? = port()?.getIntByProp(propertyId, area)
    override fun getBytesByProp(propertyId: Int): ByteArray? = port()?.getBytesByProp(propertyId)
    override fun getSensorLatestValue(sensorId: Int): Float? = port()?.getSensorLatestValue(sensorId)
}
