package com.voicegeely.app.asr

import android.content.res.AssetManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Audio front end between the Coolray 2025 microphone and the ASR (21/10/2026).
 *
 * The head unit's microphone delivers a quiet signal; recognition improved in owner tests when the
 * audio is (1) amplified and (2) denoised before ASR. Both steps here:
 * - [applyGain]: fixed gain with a soft limiter above [KNEE] so loud speech does not hard-clip;
 * - GTCRN speech enhancement (sherpa-onnx `OnlineSpeechDenoiser`, official `gtcrn_simple.onnx`
 *   from the sherpa-onnx speech-enhancement release, assets/models/denoise/). Streaming: each chunk
 *   returns the denoised samples available so far; [finish] flushes the tail.
 *
 * If the denoiser cannot be created (missing asset, native error) the front end falls back to gain
 * only and logs once. One instance per recognition thread; not thread-safe.
 */
class MicFrontEnd private constructor(
    private val gain: Float,
    private var denoiser: com.k2fsa.sherpa.onnx.OnlineSpeechDenoiser?,
    private val sampleRate: Int
) {
    val denoiseActive: Boolean get() = denoiser != null

    fun reset() {
        runCatching { denoiser?.reset() }.onFailure { disableDenoiser(it) }
    }

    /** Gain + denoise one chunk. May return fewer (or more) samples than given while streaming. */
    fun process(samples: FloatArray): FloatArray {
        val amplified = applyGain(samples, gain)
        val d = denoiser ?: return amplified
        return try {
            d.run(amplified, sampleRate).samples
        } catch (t: Throwable) {
            disableDenoiser(t)
            amplified
        }
    }

    /** Remaining denoised samples at the end of a session (empty without denoiser). */
    fun finish(): FloatArray {
        val d = denoiser ?: return FloatArray(0)
        return try { d.flush().samples } catch (t: Throwable) { disableDenoiser(t); FloatArray(0) }
    }

    private fun disableDenoiser(t: Throwable) {
        Log.w(TAG, "denoise_disabled ${t.javaClass.simpleName} ${t.message}")
        runCatching { denoiser?.release() }
        denoiser = null
    }

    fun release() {
        runCatching { denoiser?.release() }
        denoiser = null
    }

    companion object {
        private const val TAG = "MicFrontEnd"
        const val DENOISE_MODEL_ASSET = "models/denoise/gtcrn_simple.onnx"
        const val DEFAULT_GAIN = 2.0f
        const val KNEE = 0.8f

        /** Gain with a soft limiter: linear up to [KNEE], then tanh-compressed toward 1.0. */
        fun applyGain(samples: FloatArray, gain: Float): FloatArray {
            if (gain == 1f) return samples
            return FloatArray(samples.size) { i -> softLimit(samples[i] * gain) }
        }

        fun softLimit(x: Float): Float {
            val a = abs(x)
            if (a <= KNEE) return x
            val headroom = 1f - KNEE
            val y = KNEE + headroom * tanh(((a - KNEE) / headroom).toDouble()).toFloat()
            return if (x < 0) -y else y
        }

        fun create(assets: AssetManager?, sampleRate: Int, gain: Float = DEFAULT_GAIN, denoise: Boolean = true): MicFrontEnd {
            val d = if (denoise && assets != null) {
                runCatching {
                    val exists = runCatching { assets.open(DENOISE_MODEL_ASSET).close(); true }.getOrDefault(false)
                    if (!exists) error("asset $DENOISE_MODEL_ASSET missing")
                    val model = com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig().apply {
                        gtcrn = com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig(DENOISE_MODEL_ASSET)
                        numThreads = 1
                    }
                    com.k2fsa.sherpa.onnx.OnlineSpeechDenoiser(assets, com.k2fsa.sherpa.onnx.OnlineSpeechDenoiserConfig(model))
                }.onFailure { Log.w(TAG, "denoise_unavailable ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
            } else null
            Log.i(TAG, "mic_front_end gain=$gain denoise=${d != null}")
            return MicFrontEnd(gain, d, sampleRate)
        }
    }
}
