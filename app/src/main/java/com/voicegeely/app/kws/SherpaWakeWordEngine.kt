package com.voicegeely.app.kws

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * Always-local Sherpa keyword spotter for the small dedicated KWS model.
 *
 * The KWS model is intentionally separate from the 65M Vietnamese ASR model so
 * idle listening stays lightweight. Exactly one wake phrase profile is loaded
 * per engine instance.
 */
class SherpaWakeWordEngine(
    context: Context,
    val wakePhrase: WakePhrase = WakePhrase.HEY_THT,
    private val numThreads: Int = 1
) : OfflineWakeWordEngine {
    private val appContext = context.applicationContext
    private var spotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    override val sampleRate: Int = SherpaKwsModelSpec.SAMPLE_RATE
    override val isReady: Boolean
        get() = spotter != null

    @Synchronized
    override fun initialize(): WakeWordInitResult {
        if (spotter != null) {
            return WakeWordInitResult(true, "Wake word ${wakePhrase.displayLabel} đã sẵn sàng")
        }

        val missing = SherpaKwsModelSpec.requiredAssets(wakePhrase).filterNot(::assetExists)
        if (missing.isNotEmpty()) {
            return WakeWordInitResult(
                false,
                "Chưa có model wake word ${wakePhrase.displayLabel}: ${missing.joinToString { it.substringAfterLast('/') }}"
            )
        }

        return try {
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = SherpaKwsModelSpec.ENCODER,
                    decoder = SherpaKwsModelSpec.DECODER,
                    joiner = SherpaKwsModelSpec.JOINER
                ),
                tokens = SherpaKwsModelSpec.TOKENS,
                numThreads = numThreads.coerceIn(1, 2),
                debug = false,
                provider = SherpaKwsModelSpec.PROVIDER
            )
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(
                    sampleRate = SherpaKwsModelSpec.SAMPLE_RATE,
                    featureDim = SherpaKwsModelSpec.FEATURE_DIM,
                    dither = 0.0f
                ),
                modelConfig = modelConfig,
                maxActivePaths = 4,
                keywordsFile = SherpaKwsModelSpec.keywords(wakePhrase),
                keywordsScore = 1.5f,
                keywordsThreshold = 0.25f,
                numTrailingBlanks = 1
            )
            spotter = KeywordSpotter(assetManager = appContext.assets, config = config)
            WakeWordInitResult(true, "Wake word ${wakePhrase.displayLabel} offline sẵn sàng")
        } catch (t: Throwable) {
            spotter = null
            WakeWordInitResult(false, t.message ?: "Không khởi động được từ đánh thức")
        }
    }

    @Synchronized
    override fun startStream(): Boolean {
        val kws = spotter ?: return false
        releaseStream()
        stream = try {
            kws.createStream()
        } catch (_: Throwable) {
            null
        }
        return stream != null
    }

    @Synchronized
    override fun acceptWaveform(samples: FloatArray): WakeWordDetection? {
        val kws = spotter ?: return null
        val active = stream ?: return null
        if (samples.isEmpty()) return null

        return try {
            active.acceptWaveform(samples, sampleRate)
            while (kws.isReady(active)) {
                kws.decode(active)
                val result = kws.getResult(active)
                if (result.keyword.isNotBlank()) {
                    val keyword = result.keyword.trim()
                    kws.reset(active)
                    return WakeWordDetection(keyword)
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    override fun reset() {
        val active = stream
        if (active != null) {
            try { spotter?.reset(active) } catch (_: Throwable) {}
        }
        releaseStream()
    }

    @Synchronized
    override fun close() {
        reset()
        try { spotter?.release() } catch (_: Throwable) {}
        spotter = null
    }

    private fun assetExists(path: String): Boolean = try {
        appContext.assets.open(path).use { input -> input.read() >= -1 }
        true
    } catch (_: Throwable) {
        false
    }

    private fun releaseStream() {
        val active = stream ?: return
        try { active.release() } catch (_: Throwable) {}
        stream = null
    }
}
