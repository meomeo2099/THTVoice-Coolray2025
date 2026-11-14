package com.voicegeely.app.kws

/** Worker-owned primitive PCM ring; adding a chunk allocates no objects. */
class WakePcmBuffer(capacity: Int) {
    private val data = FloatArray(capacity.also { require(it > 0) })
    private var next = 0
    private var size = 0

    fun add(chunk: FloatArray) {
        if (chunk.size >= data.size) {
            System.arraycopy(chunk, chunk.size - data.size, data, 0, data.size)
            size = data.size
            next = 0
            return
        }
        val first = minOf(chunk.size, data.size - next)
        System.arraycopy(chunk, 0, data, next, first)
        System.arraycopy(chunk, first, data, 0, chunk.size - first)
        next = (next + chunk.size) % data.size
        size = minOf(data.size, size + chunk.size)
    }

    fun clear() { next = 0; size = 0 }
    fun snapshot(): FloatArray = FloatArray(size).also { copyInto(it) }

    /** One allocation only when the gate opens, with the current frame following its lead-in. */
    fun drainWith(frame: FloatArray): FloatArray = FloatArray(size + frame.size).also {
        copyInto(it)
        System.arraycopy(frame, 0, it, size, frame.size)
        clear()
    }

    private fun copyInto(output: FloatArray) {
        val start = (next - size + data.size) % data.size
        val first = minOf(size, data.size - start)
        System.arraycopy(data, start, output, 0, first)
        System.arraycopy(data, 0, output, first, size - first)
    }
}
