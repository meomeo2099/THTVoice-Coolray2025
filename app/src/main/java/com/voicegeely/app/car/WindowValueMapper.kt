package com.voicegeely.app.car

import kotlin.math.roundToInt

/** Pure percentage/raw conversion for window VHAL ranges. */
object WindowValueMapper {
    fun percentToRaw(percent: Int, range: VehicleIntRange): Int {
        require(range.isValid) { "Invalid VHAL range ${range.minValue}..${range.maxValue}" }
        val p = percent.coerceIn(0, 100)
        val span = range.maxValue - range.minValue
        return (range.minValue + (p / 100.0) * span)
            .roundToInt()
            .coerceIn(range.minValue, range.maxValue)
    }

    fun rawToPercent(raw: Int, range: VehicleIntRange): Int {
        require(range.isValid) { "Invalid VHAL range ${range.minValue}..${range.maxValue}" }
        val clamped = raw.coerceIn(range.minValue, range.maxValue)
        val span = range.maxValue - range.minValue
        return (((clamped - range.minValue).toDouble() / span) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }
}
