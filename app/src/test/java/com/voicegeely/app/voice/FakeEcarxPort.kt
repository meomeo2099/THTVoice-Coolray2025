package com.voicegeely.app.voice

import com.voicegeely.app.car.EcarxFunctionPort

/** Test double for the ECARX AdaptAPI: stores writes, optionally reflects them into reads. */
class FakeEcarxPort(
    private val reflectWrites: Boolean = true,
    private val acceptWrites: Boolean = true
) : EcarxFunctionPort {
    val ints = mutableMapOf<Pair<Int, Int>, Int>()
    val floats = mutableMapOf<Pair<Int, Int>, Float>()
    val intWrites = mutableListOf<Triple<Int, Int, Int>>()
    val floatWrites = mutableListOf<Triple<Int, Int, Float>>()
    val unsupported = mutableSetOf<Int>()
    /** Functions whose float reads always return null (no read-back). */
    val unreadable = mutableSetOf<Int>()
    /** Function whose float reads are served from this sequence (then the stored value). */
    val floatSequences = mutableMapOf<Pair<Int, Int>, MutableList<Float>>()

    override fun isSupported(functionId: Int, zone: Int): Boolean = functionId !in unsupported
    override fun getInt(functionId: Int, zone: Int): Int? = ints[functionId to zone]
    override fun setInt(functionId: Int, zone: Int, value: Int): Boolean {
        intWrites += Triple(functionId, zone, value)
        if (acceptWrites && reflectWrites) ints[functionId to zone] = value
        return acceptWrites
    }
    override fun getFloat(functionId: Int, zone: Int): Float? {
        if (functionId in unreadable) return null
        floatSequences[functionId to zone]?.let { if (it.isNotEmpty()) return it.removeAt(0) }
        return floats[functionId to zone]
    }
    /** MCU path (null = path unavailable, like a head unit without CarImpl). */
    var mcuAccepts: Boolean? = null
    val mcuWrites = mutableListOf<Pair<Int, ByteArray>>()
    val props = mutableMapOf<Int, Int>()
    val byteProps = mutableMapOf<Int, ByteArray>()
    val sensors = mutableMapOf<Int, Float>()
    val sensorReads = mutableListOf<Int>()
    override fun getSensorLatestValue(sensorId: Int): Float? { sensorReads += sensorId; return sensors[sensorId] }
    override fun sendBytesToMcu(propertyId: Int, bytes: ByteArray): Boolean {
        val ok = mcuAccepts ?: return false
        mcuWrites += propertyId to bytes
        if (ok && reflectWrites && propertyId == com.voicegeely.app.car.Coolray2025Functions.INFO_ID_WINDOW_GROUP) {
            bytes.forEachIndexed { i, b -> if (b.toInt() != -1) com.voicegeely.app.car.Coolray2025Functions.windowStateProp(i)?.let { props[it] = b.toInt() } }
        }
        return ok
    }
    override fun getIntByProp(propertyId: Int, area: Int): Int? = props[propertyId]
    override fun getBytesByProp(propertyId: Int): ByteArray? = byteProps[propertyId]
    override fun setFloat(functionId: Int, zone: Int, value: Float): Boolean {
        floatWrites += Triple(functionId, zone, value)
        if (acceptWrites && reflectWrites) floats[functionId to zone] = value
        return acceptWrites
    }
}
