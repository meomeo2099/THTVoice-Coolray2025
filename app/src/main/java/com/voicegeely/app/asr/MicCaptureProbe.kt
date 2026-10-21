package com.voicegeely.app.asr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Microphone level diagnostics for one recognition session (Coolray 2025 bring-up, 21/10/2026).
 *
 * Always: one log line `mic_level ...` with RMS / peak (dBFS), clipping ratio and DC offset, so a
 * too-quiet, clipped or silent channel shows up in `adb logcat -s VoiceAssistantService`.
 *
 * Opt-in only: when the marker file `<external files>/asr-debug/ENABLE` exists, the session audio is
 * also written as 16 kHz mono WAV to `<external files>/asr-debug/` (last [KEEP] files kept). The
 * marker is created by hand with adb; without it no audio is stored (project rule: no raw mic audio).
 */
class MicCaptureProbe private constructor(
    private val sampleRate: Int,
    private val wav: File?
) {
    private var count = 0L
    private var sumSquares = 0.0
    private var sum = 0.0
    private var peak = 0
    private var clipped = 0L
    private var out: RandomAccessFile? = null

    init {
        wav?.let { file ->
            runCatching {
                out = RandomAccessFile(file, "rw").apply {
                    setLength(0)
                    write(ByteArray(44)) // header written on finish
                }
            }.onFailure { Log.w(TAG, "asr_debug_wav_open_failed ${it.message}") }
        }
    }

    fun add(pcm: ShortArray, n: Int) {
        if (n <= 0) return
        for (i in 0 until n) {
            val v = pcm[i].toInt()
            val a = abs(v)
            if (a > peak) peak = a
            if (a >= CLIP_LEVEL) clipped++
            sum += v
            sumSquares += v.toDouble() * v
        }
        count += n
        out?.let { file ->
            runCatching {
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    val v = pcm[i].toInt()
                    bytes[i * 2] = (v and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                file.write(bytes)
            }.onFailure { closeQuietly() }
        }
    }

    /** Logs the session summary and finalises the WAV (if recording). */
    fun finish(source: Int?, text: String) {
        val rms = if (count > 0) sqrt(sumSquares / count) else 0.0
        val dc = if (count > 0) sum / count else 0.0
        Log.i(
            TAG,
            "mic_level source=$source ms=${count * 1000 / sampleRate} rms_dbfs=${dbfs(rms)} " +
                "peak_dbfs=${dbfs(peak.toDouble())} clip_pct=${"%.2f".format(if (count > 0) clipped * 100.0 / count else 0.0)} " +
                "dc=${"%.0f".format(dc)} chars=${text.length}" + (wav?.let { " wav=${it.name}" } ?: "")
        )
        out?.let { file ->
            runCatching {
                val dataBytes = (count * 2).toInt()
                file.seek(0)
                file.write(header(dataBytes))
            }
        }
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { out?.close() }
        out = null
    }

    private fun header(dataBytes: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + dataBytes); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(sampleRate); b.putInt(sampleRate * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataBytes)
        return b.array()
    }

    companion object {
        private const val TAG = "VoiceAssistantService"
        private const val CLIP_LEVEL = 32000
        private const val KEEP = 10
        const val DIR = "asr-debug"
        const val MARKER = "ENABLE"

        fun dbfs(value: Double): String =
            if (value <= 0.0) "-inf" else "%.1f".format(20 * log10(max(value, 1.0) / 32768.0))

        fun start(context: Context, sampleRate: Int): MicCaptureProbe {
            val wav = runCatching {
                val dir = File(context.getExternalFilesDir(null) ?: return@runCatching null, DIR)
                if (!File(dir, MARKER).exists()) return@runCatching null
                dir.listFiles { f -> f.name.endsWith(".wav") }?.sortedBy { it.lastModified() }
                    ?.dropLast(KEEP - 1)?.forEach { it.delete() }
                File(dir, "asr-${System.currentTimeMillis()}.wav")
            }.getOrNull()
            return MicCaptureProbe(sampleRate, wav)
        }
    }
}
