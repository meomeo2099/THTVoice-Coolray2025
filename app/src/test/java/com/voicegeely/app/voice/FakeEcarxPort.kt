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
    override fun setFloat(functionId: Int, zone: Int, value: Float): Boolean {
        floatWrites += Triple(functionId, zone, value)
        if (acceptWrites && reflectWrites) floats[functionId to zone] = value
        return acceptWrites
    }
}
