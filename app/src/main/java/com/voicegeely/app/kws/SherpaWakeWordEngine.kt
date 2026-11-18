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
    val sensitivity: WakeSensitivity = WakeSensitivity.LOW,
    private val numThreads: Int = 1
) : OfflineWakeWordEngine {
    private val appContext = context.applicationContext
    private var spotter: KeywordSpotter? = null
    /** Same model with a lower threshold, used only during the "second chance" window. */
    private var lenientSpotter: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var streamOwner: KeywordSpotter? = null
    private var modelConfigCache: OnlineModelConfig? = null

    /** Lines learned by "Dạy từ gọi" for this profile, added to the built-in file in every stream. */
    @Volatile var learnedLines: List<KeywordLines.Line> = emptyList()

    val learnedLabel: String get() = learnedLabel(wakePhrase)

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
                maxActivePaths = sensitivity.maxActivePaths,
                keywordsFile = SherpaKwsModelSpec.keywords(wakePhrase),
                keywordsScore = sensitivity.keywordsScore,
                keywordsThreshold = sensitivity.keywordsThreshold,
                numTrailingBlanks = 1
            )
            spotter = KeywordSpotter(assetManager = appContext.assets, config = config)
            modelConfigCache = modelConfig
            WakeWordInitResult(true, "Wake word ${wakePhrase.displayLabel} offline sẵn sàng")
        } catch (t: Throwable) {
            spotter = null
            WakeWordInitResult(false, t.message ?: "Không khởi động được từ đánh thức")
        }
    }

    @Synchronized
    override fun startStream(): Boolean = startStream(lenient = false)

    /** [lenient] = "second chance": lower threshold, more boost, for a few seconds after a near miss. */
    @Synchronized
    fun startStream(lenient: Boolean): Boolean {
        val kws = (if (lenient) lenient() else null) ?: spotter ?: return false
        releaseStream()
        val extra = learnedLines.let { if (lenient) KeywordLines.lenient(it, LENIENT_THRESHOLD, LENIENT_EXTRA_BOOST, sensitivity.keywordsScore) else it }
        stream = try {
            if (extra.isEmpty()) kws.createStream() else kws.createStream(KeywordLines.streamKeywords(extra))
        } catch (_: Throwable) {
            // A learned line the model cannot encode must never cost the wake word itself.
            runCatching { kws.createStream() }.getOrNull()
        }
        streamOwner = kws
        return stream != null
    }

    val lenientActive: Boolean @Synchronized get() = streamOwner != null && streamOwner === lenientSpotter

    private fun lenient(): KeywordSpotter? {
        lenientSpotter?.let { return it }
        val modelConfig = modelConfigCache ?: return null
        return runCatching {
            KeywordSpotter(assetManager = appContext.assets, config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SherpaKwsModelSpec.SAMPLE_RATE, featureDim = SherpaKwsModelSpec.FEATURE_DIM, dither = 0.0f),
                modelConfig = modelConfig,
                maxActivePaths = maxOf(sensitivity.maxActivePaths, 8),
                keywordsFile = SherpaKwsModelSpec.keywords(wakePhrase),
                keywordsScore = sensitivity.keywordsScore + LENIENT_EXTRA_BOOST,
                keywordsThreshold = minOf(sensitivity.keywordsThreshold, LENIENT_THRESHOLD),
                numTrailingBlanks = 1
            ))
        }.getOrNull().also { lenientSpotter = it }
    }

    /**
     * Enrollment cross-check: does the spotter, with [extra] keyword lines added, hear one of those
     * lines (label [label]) in [pcm]? The built-in lines ride along and are skipped.
     */
    @Synchronized
    fun hears(pcm: ShortArray, extra: List<KeywordLines.Line>, label: String, cancelled: () -> Boolean = { false }): Boolean {
        val kws = spotter ?: return false
        if (pcm.isEmpty() || extra.isEmpty()) return false
        val s = runCatching { kws.createStream(KeywordLines.streamKeywords(extra)) }.getOrNull() ?: return false
        return try {
            s.acceptWaveform(FloatArray(pcm.size) { pcm[it] / 32768f }, sampleRate)
            s.acceptWaveform(FloatArray(sampleRate / 2), sampleRate)
            s.inputFinished()
            var steps = 0
            while (kws.isReady(s)) {
                if (cancelled() || ++steps > 512) return false
                kws.decode(s)
                val k = kws.getResult(s).keyword.trim()
                if (k.isEmpty()) continue
                if (k == label) return true
                kws.reset(s)
            }
            false
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { s.release() }
        }
    }

    @Synchronized
    override fun acceptWaveform(samples: FloatArray): WakeWordDetection? {
        val kws = spotter ?: return null
        val active = stream ?: return null
        if (samples.isEmpty()) return null

        return try {
            val owner = streamOwner ?: kws
            active.acceptWaveform(samples, sampleRate)
            while (owner.isReady(active)) {
                owner.decode(active)
                val result = owner.getResult(active)
                if (result.keyword.isNotBlank()) {
                    val keyword = result.keyword.trim()
                    owner.reset(active)
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
            try { (streamOwner ?: spotter)?.reset(active) } catch (_: Throwable) {}
        }
        releaseStream()
    }

    @Synchronized
    override fun close() {
        reset()
        try { spotter?.release() } catch (_: Throwable) {}
        try { lenientSpotter?.release() } catch (_: Throwable) {}
        spotter = null
        lenientSpotter = null
        modelConfigCache = null
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
        streamOwner = null
    }

    companion object {
        const val LENIENT_THRESHOLD = 0.10f
        const val LENIENT_EXTRA_BOOST = 0.5f
        fun learnedLabel(phrase: WakePhrase): String = phrase.keywordLabel + "_USER"
    }
}
