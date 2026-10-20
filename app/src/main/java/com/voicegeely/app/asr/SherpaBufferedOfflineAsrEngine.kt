package com.voicegeely.app.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * Sherpa OfflineRecognizer adapter for 65M Vietnamese transducer models such as Gipformer.
 *
 * Audio chunks are accepted continuously, while decoding happens once the utterance reaches
 * a local endpoint. VAD values are read from [AsrEndpointRuntime], allowing bounded tuning
 * without rebuilding the app while preserving the original baseline by default.
 */
class SherpaBufferedOfflineAsrEngine(
    private val modelDir: File,
    private val numThreads: Int = 2,
    private val configProvider: () -> AsrEndpointConfig = { AsrEndpointRuntime.snapshot() }
) : OfflineAsrEngine {
    private var recognizer: OfflineRecognizer? = null
    private var active = false
    private val tracker = OfflineEndpointTracker(Sherpa65mModelSpec.SAMPLE_RATE, configProvider)
    private var endpointText = ""

    override val sampleRate: Int = Sherpa65mModelSpec.SAMPLE_RATE
    override val isReady: Boolean
        get() = recognizer != null

    @Synchronized
    override fun initialize(): AsrInitResult {
        if (recognizer != null) return AsrInitResult(true)
        if (!modelDir.isDirectory) {
            return AsrInitResult(false, "Không tìm thấy bộ nhận giọng nói.")
        }
        val files = Sherpa65mModelSpec.discover(
            modelDir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        ) ?: return AsrInitResult(false, "Bộ nhận giọng nói chưa đầy đủ, hãy cài lại ứng dụng.")

        return try {
            val modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(modelDir, files.encoder).absolutePath,
                    decoder = File(modelDir, files.decoder).absolutePath,
                    joiner = File(modelDir, files.joiner).absolutePath
                ),
                tokens = File(modelDir, files.tokens).absolutePath,
                numThreads = numThreads.coerceIn(1, 4),
                debug = false,
                provider = Sherpa65mModelSpec.PROVIDER,
                modelType = "transducer"
            )
            recognizer = OfflineRecognizer(
                assetManager = null,
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = Sherpa65mModelSpec.SAMPLE_RATE,
                        featureDim = Sherpa65mModelSpec.FEATURE_DIM,
                        dither = 0.0f
                    ),
                    modelConfig = modelConfig,
                    decodingMethod = "greedy_search"
                )
            )
            AsrInitResult(true, "Nhận giọng nói offline sẵn sàng")
        } catch (t: Throwable) {
            recognizer = null
            AsrInitResult(false, t.message ?: "Không khởi động được bộ nhận giọng nói.")
        }
    }

    @Synchronized
    override fun startStream(): Boolean {
        if (recognizer == null) return false
        tracker.reset()
        endpointText = ""
        active = true
        return true
    }

    @Synchronized
    override fun acceptWaveform(samples: FloatArray): Boolean {
        if (!active) return false
        tracker.accept(samples)
        return true
    }

    /**
     * Offline models have no cheap partial decode. At a local VAD endpoint the whole utterance is
     * decoded; if speech comes back afterwards (pause after "rồi"), the next endpoint decodes again.
     */
    @Synchronized
    override fun partialText(): String {
        if (tracker.decodedValid) return endpointText
        if (!tracker.endpointReached()) return ""
        decodeAll()
        return endpointText
    }

    @Synchronized
    override fun isEndpoint(): Boolean = tracker.decodedValid || tracker.endpointReached()

    @Synchronized
    override fun noSpeechTimedOut(): Boolean = tracker.noSpeechTimedOut()

    @Synchronized
    override fun finishStream(): AsrRecognitionResult {
        if (!tracker.decodedValid && tracker.totalSamples > 0) decodeAll()
        val text = endpointText
        if (tracker.resumedCount > 0) android.util.Log.i("THTAsr", "offline_endpoint_resumed count=${tracker.resumedCount}")
        active = false
        tracker.reset()
        return AsrRecognitionResult(text = text, final = true)
    }

    @Synchronized
    override fun reset() {
        active = false
        tracker.reset()
        endpointText = ""
    }

    @Synchronized
    override fun close() {
        reset()
        try { recognizer?.release() } catch (_: Throwable) {}
        recognizer = null
    }

    /** Fresh stream over the whole buffered utterance, so a re-decode after a pause sees it all. */
    private fun decodeAll() {
        val r = recognizer ?: return
        var s: OfflineStream? = null
        endpointText = try {
            s = r.createStream()
            s.acceptWaveform(tracker.audio(), sampleRate)
            r.decode(s)
            r.getResult(s).text.trim()
        } catch (_: Throwable) {
            endpointText
        } finally {
            try { s?.release() } catch (_: Throwable) {}
        }
        tracker.markDecoded()
    }

    companion object {
        const val DEFAULT_TRAILING_SILENCE_MS = AsrEndpointConfig.DEFAULT_TRAILING_SILENCE_MS
        const val DEFAULT_MIN_SPEECH_MS = AsrEndpointConfig.DEFAULT_MIN_SPEECH_MS
        const val DEFAULT_SPEECH_RMS = AsrEndpointConfig.DEFAULT_SPEECH_RMS

        internal fun rms(samples: FloatArray): Float = OfflineEndpointTracker.rms(samples)
    }
}
