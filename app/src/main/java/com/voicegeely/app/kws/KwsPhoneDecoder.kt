package com.voicegeely.app.kws

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * Free greedy decoding with the keyword spotter's own transducer (R03 PhoneDecoder): the tokens the
 * spotter actually hears for a speaker, which [WakeEnrollment] turns into keyword lines. Loaded only
 * while training; [close] frees it.
 */
class KwsPhoneDecoder(context: Context) : java.io.Closeable {
    private val recognizer: OnlineRecognizer = OnlineRecognizer(
        assetManager = context.applicationContext.assets,
        config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SherpaKwsModelSpec.SAMPLE_RATE, featureDim = SherpaKwsModelSpec.FEATURE_DIM, dither = 0.0f),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = SherpaKwsModelSpec.ENCODER, decoder = SherpaKwsModelSpec.DECODER, joiner = SherpaKwsModelSpec.JOINER),
                tokens = SherpaKwsModelSpec.TOKENS, numThreads = 1, debug = false, provider = SherpaKwsModelSpec.PROVIDER),
            enableEndpoint = false,
            decodingMethod = "greedy_search"
        )
    )

    @Synchronized
    fun decode(pcm: ShortArray, cancelled: () -> Boolean = { false }): List<String> {
        if (pcm.isEmpty()) return emptyList()
        val s = recognizer.createStream()
        return try {
            s.acceptWaveform(FloatArray(pcm.size) { pcm[it] / 32768f }, SherpaKwsModelSpec.SAMPLE_RATE)
            s.acceptWaveform(FloatArray(SherpaKwsModelSpec.SAMPLE_RATE / 2), SherpaKwsModelSpec.SAMPLE_RATE)
            s.inputFinished()
            var steps = 0
            while (recognizer.isReady(s)) {
                if (cancelled() || ++steps > 512) return emptyList()
                recognizer.decode(s)
            }
            recognizer.getResult(s).tokens.map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            runCatching { s.release() }
        }
    }

    override fun close() { runCatching { recognizer.release() } }
}
