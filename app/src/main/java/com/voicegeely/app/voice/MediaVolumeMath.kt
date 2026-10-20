package com.voicegeely.app.voice

/**
 * Owner request 15/10/2026: volume by number. Level = the head unit's media volume index
 * (STREAM_MUSIC, 0..max as the system reports it). REFERENCE/UNVERIFIED on IHU629G: whether this
 * index matches the number the car shows is confirmed on the car.
 */
object MediaVolumeMath {
    /** Target level, or null when [value] is outside the stream's range (never clamped silently). */
    fun target(value: Int, unit: VolumeUnit, min: Int, max: Int): Int? {
        if (max <= 0 || min > max) return null
        return when (unit) {
            VolumeUnit.LEVEL -> value.takeIf { it in min..max }
            VolumeUnit.PERCENT -> value.takeIf { it in 0..100 }
                ?.let { Math.round(it * max / 100.0).toInt().coerceIn(min, max) }
        }
    }

    /** [steps] from [current], kept inside min..max. */
    fun adjust(current: Int, steps: Int, min: Int, max: Int): Int? =
        if (max <= 0 || min > max) null else (current + steps).coerceIn(min, max)
}
