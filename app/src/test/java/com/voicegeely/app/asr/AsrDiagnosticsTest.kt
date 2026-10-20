package com.voicegeely.app.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrDiagnosticsTest {
    @Test
    fun metricsExposeAudioDurationAndComputeRtfWithoutRawText() {
        val metrics = AsrSessionMetrics(
            backend = "offline-transducer",
            modelSource = "test-model",
            sampleRate = 16_000,
            audioSamples = 16_000,
            sessionWallMs = 1_200,
            asrComputeMs = 400,
            finishMs = 200,
            firstPartialMs = 900,
            processCpuMs = 300,
            pssBeforeKb = 100_000,
            pssAfterKb = 102_000,
            endpointSeen = true,
            finalTextChars = 12,
            outcome = AsrSessionOutcome.SUCCESS,
            vadSpeechRmsThreshold = 0.008f,
            vadMinSpeechMs = 180,
            vadTrailingSilenceMs = 900,
            maxListenMs = 8_000
        )

        assertEquals(1_000L, metrics.audioDurationMs)
        assertEquals(0.4, metrics.computeRealtimeFactor!!, 0.0001)
        assertEquals(2_000L, metrics.pssDeltaKb)
        val formatted = AsrDiagnosticsFormatter.format(metrics)
        assertTrue(formatted.contains("computeRTF=0.400"))
        assertTrue(formatted.contains("vadRms=0.008"))
        assertTrue(formatted.contains("minSpeech=180ms"))
        assertTrue(formatted.contains("silence=900ms"))
        assertTrue(formatted.contains("maxListen=8000ms"))
        assertTrue(formatted.contains("chars=12"))
    }

    @Test
    fun storeIsBoundedAndSnapshotSummarizesSamples() {
        val store = AsrMetricsStore(capacity = 2)
        store.append(sample(100, AsrSessionOutcome.EMPTY_RESULT))
        store.append(sample(200, AsrSessionOutcome.SUCCESS))
        store.append(sample(300, AsrSessionOutcome.SUCCESS))

        val snapshot = AsrDiagnostics.snapshot(limit = 10, store = store)
        assertEquals(2, snapshot.totalBuffered)
        assertEquals(2, snapshot.successful)
        assertEquals(250L, snapshot.averageWallMs)
        assertEquals(2, snapshot.lines.size)
    }

    @Test
    fun instrumentedEngineRecordsAndPublishesSuccessfulSession() {
        val store = AsrMetricsStore(capacity = 5)
        val published = mutableListOf<AsrSessionMetrics>()
        val endpointConfig = AsrEndpointConfig(
            speechRmsThreshold = 0.008f,
            trailingSilenceMs = 900,
            minSpeechMs = 180,
            maxListenMs = 8_000
        )
        val engine = InstrumentedOfflineAsrEngine(
            delegate = FakeEngine(
                partial = "hai mươi hai độ",
                finalText = "hai mươi hai độ",
                endpoint = true
            ),
            backend = "offline-transducer",
            modelSource = "unit-test",
            store = store,
            publisher = AsrDiagnosticsPublisher { published += it },
            runtimeProbe = SequenceProbe(
                AsrRuntimeSnapshot(processCpuMs = 100, pssKb = 10_000),
                AsrRuntimeSnapshot(processCpuMs = 140, pssKb = 10_500)
            ),
            clock = StepClock(),
            endpointConfigProvider = { endpointConfig }
        )

        assertTrue(engine.startStream())
        assertTrue(engine.acceptWaveform(FloatArray(1_600)))
        assertEquals("hai mươi hai độ", engine.partialText())
        assertTrue(engine.isEndpoint())
        assertEquals("hai mươi hai độ", engine.finishStream().text)

        val metrics = store.snapshot().single()
        assertEquals(AsrSessionOutcome.SUCCESS, metrics.outcome)
        assertEquals(1_600L, metrics.audioSamples)
        assertTrue(metrics.endpointSeen)
        assertNotNull(metrics.firstPartialMs)
        assertEquals(40L, metrics.processCpuMs)
        assertEquals(500L, metrics.pssDeltaKb)
        assertEquals("hai mươi hai độ".length, metrics.finalTextChars)
        assertEquals(0.008f, metrics.vadSpeechRmsThreshold!!, 0.00001f)
        assertEquals(180L, metrics.vadMinSpeechMs)
        assertEquals(900L, metrics.vadTrailingSilenceMs)
        assertEquals(8_000L, metrics.maxListenMs)
        assertEquals(listOf(metrics), published)
    }

    @Test
    fun endpointConfigProviderFailureNeverBreaksRecognition() {
        val store = AsrMetricsStore(capacity = 5)
        val engine = InstrumentedOfflineAsrEngine(
            delegate = FakeEngine(finalText = "mở điều hòa"),
            backend = "online-transducer",
            store = store,
            runtimeProbe = SequenceProbe(AsrRuntimeSnapshot(), AsrRuntimeSnapshot()),
            clock = StepClock(),
            endpointConfigProvider = { throw IllegalStateException("prefs unavailable") }
        )

        assertTrue(engine.startStream())
        assertTrue(engine.acceptWaveform(FloatArray(800)))
        assertEquals("mở điều hòa", engine.finishStream().text)
        val metrics = store.snapshot().single()
        assertEquals(null, metrics.vadSpeechRmsThreshold)
        assertEquals(null, metrics.vadTrailingSilenceMs)
    }

    @Test
    fun publisherFailureNeverBreaksRecognition() {
        val store = AsrMetricsStore(capacity = 5)
        val engine = InstrumentedOfflineAsrEngine(
            delegate = FakeEngine(finalText = "mở điều hòa"),
            backend = "online-transducer",
            store = store,
            publisher = AsrDiagnosticsPublisher { throw IllegalStateException("log unavailable") },
            runtimeProbe = SequenceProbe(AsrRuntimeSnapshot(), AsrRuntimeSnapshot()),
            clock = StepClock()
        )

        assertTrue(engine.startStream())
        assertTrue(engine.acceptWaveform(FloatArray(800)))
        assertEquals("mở điều hòa", engine.finishStream().text)
        assertEquals(1, store.snapshot().size)
    }

    @Test
    fun resetRecordsCancellationInsteadOfLosingSample() {
        val store = AsrMetricsStore(capacity = 5)
        val engine = InstrumentedOfflineAsrEngine(
            delegate = FakeEngine(),
            backend = "online-transducer",
            store = store,
            runtimeProbe = SequenceProbe(AsrRuntimeSnapshot(), AsrRuntimeSnapshot()),
            clock = StepClock()
        )

        assertTrue(engine.startStream())
        engine.acceptWaveform(FloatArray(800))
        engine.reset()

        val metrics = store.snapshot().single()
        assertEquals(AsrSessionOutcome.CANCELLED, metrics.outcome)
        assertEquals(800L, metrics.audioSamples)
    }

    private fun sample(wallMs: Long, outcome: AsrSessionOutcome) = AsrSessionMetrics(
        backend = "offline-transducer",
        modelSource = null,
        sampleRate = 16_000,
        audioSamples = 16_000,
        sessionWallMs = wallMs,
        asrComputeMs = 100,
        finishMs = 50,
        firstPartialMs = null,
        processCpuMs = 20,
        pssBeforeKb = 10_000,
        pssAfterKb = 10_100,
        endpointSeen = false,
        finalTextChars = if (outcome == AsrSessionOutcome.SUCCESS) 5 else 0,
        outcome = outcome
    )

    private class StepClock : AsrDiagnosticsClock {
        private var value = 0L
        override fun nowMs(): Long = value.also { value += 5L }
    }

    private class SequenceProbe(vararg snapshots: AsrRuntimeSnapshot) : AsrRuntimeProbe {
        private val values = snapshots.toList()
        private var index = 0
        override fun snapshot(): AsrRuntimeSnapshot {
            if (values.isEmpty()) return AsrRuntimeSnapshot()
            val result = values[index.coerceAtMost(values.lastIndex)]
            index += 1
            return result
        }
    }

    private class FakeEngine(
        private val partial: String = "",
        private val finalText: String = "",
        private val endpoint: Boolean = false,
        private val accept: Boolean = true
    ) : OfflineAsrEngine {
        override val isReady: Boolean = true
        override val sampleRate: Int = 16_000

        override fun initialize() = AsrInitResult(true)
        override fun startStream() = true
        override fun acceptWaveform(samples: FloatArray) = accept
        override fun partialText() = partial
        override fun isEndpoint() = endpoint
        override fun finishStream() = AsrRecognitionResult(finalText, true)
        override fun reset() = Unit
        override fun close() = Unit
    }
}
