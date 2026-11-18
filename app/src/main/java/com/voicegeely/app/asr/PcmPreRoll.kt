package com.voicegeely.app.asr

/** Short in-memory handoff only: never persisted or uploaded. */
class PcmPreRoll(private val capacity: Int = 32_000) {
    init { require(capacity > 0) }
    private val data = FloatArray(capacity)
    private var next = 0
    private var size = 0
    private var updatedAt = Long.MIN_VALUE

    @Synchronized fun add(samples: FloatArray, nowMs: Long) {
        for (sample in samples) {
            data[next] = sample
            next = (next + 1) % capacity
            size = minOf(size + 1, capacity)
        }
        updatedAt = nowMs
    }

    @Synchronized fun take(nowMs: Long, maxSamples: Int = capacity): FloatArray {
        val fresh = updatedAt != Long.MIN_VALUE && nowMs >= updatedAt && nowMs - updatedAt <= 1_000
        val count = minOf(size, maxSamples.coerceAtLeast(0))
        val out = if (fresh) FloatArray(count) { data[(next - count + it + capacity) % capacity] } else FloatArray(0)
        clear()
        return out
    }

    /** Like [take] but keeps the buffer (the warm handoff still needs it afterwards). */
    @Synchronized fun peek(nowMs: Long, maxSamples: Int = capacity): FloatArray {
        val fresh = updatedAt != Long.MIN_VALUE && nowMs >= updatedAt && nowMs - updatedAt <= 1_000
        val count = minOf(size, maxSamples.coerceAtLeast(0))
        return if (fresh) FloatArray(count) { data[(next - count + it + capacity) % capacity] } else FloatArray(0)
    }

    @Synchronized fun clear() { next = 0; size = 0; updatedAt = Long.MIN_VALUE; data.fill(0f) }
}
