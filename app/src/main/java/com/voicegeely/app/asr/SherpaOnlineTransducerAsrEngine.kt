package com.voicegeely.app.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * True OnlineRecognizer implementation for streaming transducer ONNX exports.
 *
 * Sherpa's native endpoint remains enabled. A bounded local RMS/trailing-silence endpoint is
 * layered on top so the same tuning controls can be used by both online and buffered-offline
 * backends without depending on model-specific endpoint defaults.
 */
class SherpaOnlineTransducerAsrEngine(
    private val modelDir: File,
    private val numThreads: Int = 2,
    private val configProvider: () -> AsrEndpointConfig = { AsrEndpointRuntime.snapshot() }
) : OfflineAsrEngine {
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var speechSeen = false
    private var speechSamples = 0L
    private var trailingSilenceSamples = 0L
    private var totalSamples = 0L

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
        ) ?: return AsrInitResult(
            false,
            "Bộ nhận giọng nói chưa đầy đủ, hãy cài lại ứng dụng."
        )

        return try {
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(modelDir, files.encoder).absolutePath,
                    decoder = File(modelDir, files.decoder).absolutePath,
                    joiner = File(modelDir, files.joiner).absolutePath
                ),
                tokens = File(modelDir, files.tokens).absolutePath,
                numThreads = numThreads.coerceIn(1, 4),
                debug = false,
                provider = Sherpa65mModelSpec.PROVIDER,
                modelType = "zipformer"
            )
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = Sherpa65mModelSpec.SAMPLE_RATE,
                    featureDim = Sherpa65mModelSpec.FEATURE_DIM,
                    dither = 0.0f
                ),
                modelConfig = modelConfig,
                enableEndpoint = true,
                decodingMethod = "greedy_search"
            )
            recognizer = OnlineRecognizer(assetManager = null, config = config)
            AsrInitResult(true, "Nhận giọng nói offline sẵn sàng")
        } catch (t: Throwable) {
            recognizer = null
            AsrInitResult(false, t.message ?: "Không khởi động được bộ nhận giọng nói.")
        }
    }

    @Synchronized
    override fun startStream(): Boolean {
        val r = recognizer ?: return false
        stream?.release()
        stream = try {
            r.createStream()
        } catch (_: Throwable) {
            null
        }
        speechSeen = false
        speechSamples = 0L
        trailingSilenceSamples = 0L
        totalSamples = 0L
        return stream != null
    }

    @Synchronized
    override fun acceptWaveform(samples: FloatArray): Boolean {
        val r = recognizer ?: return false
        val s = stream ?: return false
        if (samples.isEmpty()) return true
        return try {
            s.acceptWaveform(samples, sampleRate)
            while (r.isReady(s)) r.decode(s)
            totalSamples += samples.size
            val config = configProvider().normalized()
            val rms = SherpaBufferedOfflineAsrEngine.rms(samples)
            if (rms >= config.speechRmsThreshold) {
                speechSeen = true
                speechSamples += samples.size
                trailingSilenceSamples = 0L
            } else if (speechSeen) {
                trailingSilenceSamples += samples.size
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    override fun partialText(): String {
        val r = recognizer ?: return ""
        val s = stream ?: return ""
        return try {
            r.getResult(s).text.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    @Synchronized
    override fun isEndpoint(): Boolean {
        val r = recognizer ?: return false
        val s = stream ?: return false
        val localEndpoint = localEndpointReached()
        return try {
            localEndpoint || r.isEndpoint(s)
        } catch (_: Throwable) {
            localEndpoint
        }
    }

    @Synchronized
    override fun finishStream(): AsrRecognitionResult {
        val r = recognizer ?: return AsrRecognitionResult("", true, "Bộ nhận giọng nói chưa sẵn sàng.")
        val s = stream ?: return AsrRecognitionResult("", true, "Chưa có stream nhận dạng.")
        return try {
            s.inputFinished()
            while (r.isReady(s)) r.decode(s)
            val text = r.getResult(s).text.trim()
            s.release()
            stream = null
            clearEndpointState()
            AsrRecognitionResult(text = text, final = true)
        } catch (t: Throwable) {
            try { s.release() } catch (_: Throwable) {}
            stream = null
            clearEndpointState()
            AsrRecognitionResult("", true, t.message ?: "Lỗi kết thúc nhận dạng.")
        }
    }

    @Synchronized
    override fun reset() {
        val s = stream
        if (s != null) {
            try { recognizer?.reset(s) } catch (_: Throwable) {}
            try { s.release() } catch (_: Throwable) {}
            stream = null
        }
        clearEndpointState()
    }

    @Synchronized
    override fun close() {
        reset()
        try { recognizer?.release() } catch (_: Throwable) {}
        recognizer = null
    }

    private fun localEndpointReached(): Boolean {
        val config = configProvider().normalized()
        val maxListenSamples = config.maxListenMs * sampleRate / 1000L
        if (totalSamples >= maxListenSamples) return true
        if (!speechSeen) return false
        val minSpeechSamples = config.minSpeechMs * sampleRate / 1000L
        val silenceSamples = config.trailingSilenceMs * sampleRate / 1000L
        return speechSamples >= minSpeechSamples && trailingSilenceSamples >= silenceSamples
    }

    private fun clearEndpointState() {
        speechSeen = false
        speechSamples = 0L
        trailingSilenceSamples = 0L
        totalSamples = 0L
    }
}
