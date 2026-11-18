package com.voicegeely.app.asr

import java.io.File

/**
 * Compatibility facade retained for VoiceAssistantService.
 *
 * The original implementation always used OnlineRecognizer. It now delegates to
 * the backend declared by `asr-backend.txt`, so an offline Gipformer export can
 * never be passed to the online Sherpa API by mistake.
 */
class SherpaStreamingAsrEngine(
    modelDir: File,
    // Owner report 22/10/2026: 1.0.9 with 2..4 threads per core count was slower on the car; back to 2.
    numThreads: Int = 2
) : OfflineAsrEngine {

    private val delegate: OfflineAsrEngine = SherpaAsrEngineFactory.create(modelDir, numThreads)

    override val isReady: Boolean
        get() = delegate.isReady
    override val sampleRate: Int
        get() = delegate.sampleRate

    override fun initialize(): AsrInitResult = delegate.initialize()
    override fun warmUp() = delegate.warmUp()
    override fun startStream(): Boolean = delegate.startStream()
    override fun acceptWaveform(samples: FloatArray): Boolean = delegate.acceptWaveform(samples)
    override fun partialText(): String = delegate.partialText()
    override fun earlyText(): String? = delegate.earlyText()
    override fun isEndpoint(): Boolean = delegate.isEndpoint()
    override fun noSpeechTimedOut(): Boolean = delegate.noSpeechTimedOut()
    override fun finishStream(): AsrRecognitionResult = delegate.finishStream()
    override fun reset() = delegate.reset()
    override fun close() = delegate.close()
}
