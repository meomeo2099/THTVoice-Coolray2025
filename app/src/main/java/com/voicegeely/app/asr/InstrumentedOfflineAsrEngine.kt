package com.voicegeely.app.asr

/**
 * Transparent diagnostics wrapper around any local ASR backend.
 *
 * It never changes recognized text or endpoint policy. Diagnostics are bounded,
 * process-local and contain no raw microphone audio or recognized phrases.
 */
class InstrumentedOfflineAsrEngine(
    private val delegate: OfflineAsrEngine,
    private val backend: String,
    private val modelSource: String? = null,
    private val store: AsrMetricsStore = AsrDiagnosticsRegistry.store,
    private val publisher: AsrDiagnosticsPublisher = NoOpAsrDiagnosticsPublisher,
    private val runtimeProbe: AsrRuntimeProbe = AndroidAsrRuntimeProbe(),
    private val clock: AsrDiagnosticsClock = AsrDiagnosticsClock { System.nanoTime() / 1_000_000L },
    private val endpointConfigProvider: () -> AsrEndpointConfig? = { AsrEndpointRuntime.snapshot() }
) : OfflineAsrEngine {
    private data class ActiveSession(
        val startedAtMs: Long,
        val runtimeStart: AsrRuntimeSnapshot,
        val endpointConfig: AsrEndpointConfig?,
        var audioSamples: Long = 0L,
        var computeMs: Long = 0L,
        var firstPartialMs: Long? = null,
        var endpointSeen: Boolean = false,
        var inputRejected: Boolean = false
    )

    private var active: ActiveSession? = null

    override val isReady: Boolean
        get() = delegate.isReady

    override val sampleRate: Int
        get() = delegate.sampleRate

    override fun initialize(): AsrInitResult = delegate.initialize()

    /** Not a listening session: no diagnostics entry. */
    override fun warmUp() = delegate.warmUp()

    @Synchronized
    override fun startStream(): Boolean {
        recordCancelledIfNeeded()
        val started = timed { delegate.startStream() }.first
        if (started) {
            active = ActiveSession(
                startedAtMs = clock.nowMs(),
                runtimeStart = safeRuntimeSnapshot(),
                endpointConfig = safeEndpointConfig()
            )
        }
        return started
    }

    @Synchronized
    override fun acceptWaveform(samples: FloatArray): Boolean {
        val session = active
        val (accepted, elapsed) = timed { delegate.acceptWaveform(samples) }
        if (session != null) {
            if (samples.isNotEmpty()) session.audioSamples += samples.size.toLong()
            session.computeMs += elapsed
            if (!accepted) session.inputRejected = true
        }
        return accepted
    }

    @Synchronized
    override fun partialText(): String {
        val session = active
        val (text, elapsed) = timed { delegate.partialText() }
        if (session != null) {
            session.computeMs += elapsed
            if (text.isNotBlank() && session.firstPartialMs == null) {
                session.firstPartialMs = (clock.nowMs() - session.startedAtMs).coerceAtLeast(0L)
            }
        }
        return text
    }

    @Synchronized
    override fun earlyText(): String? {
        val session = active
        val (text, elapsed) = timed { delegate.earlyText() }
        if (session != null) session.computeMs += elapsed
        return text
    }

    @Synchronized
    override fun isEndpoint(): Boolean {
        val session = active
        val (endpoint, elapsed) = timed { delegate.isEndpoint() }
        if (session != null) {
            session.computeMs += elapsed
            if (endpoint) session.endpointSeen = true
        }
        return endpoint
    }

    override fun noSpeechTimedOut(): Boolean = delegate.noSpeechTimedOut()

    @Synchronized
    override fun finishStream(): AsrRecognitionResult {
        val session = active
        val before = clock.nowMs()
        val result = delegate.finishStream()
        val finishMs = (clock.nowMs() - before).coerceAtLeast(0L)
        if (session != null) {
            session.computeMs += finishMs
            if (result.text.isNotBlank() && session.firstPartialMs == null) {
                session.firstPartialMs = (clock.nowMs() - session.startedAtMs).coerceAtLeast(0L)
            }
            recordFinished(session, result, finishMs)
            active = null
        }
        return result
    }

    @Synchronized
    override fun reset() {
        recordCancelledIfNeeded()
        delegate.reset()
    }

    @Synchronized
    override fun close() {
        recordCancelledIfNeeded()
        delegate.close()
    }

    private fun recordFinished(
        session: ActiveSession,
        result: AsrRecognitionResult,
        finishMs: Long
    ) {
        val runtimeEnd = safeRuntimeSnapshot()
        val cpuDelta = delta(session.runtimeStart.processCpuMs, runtimeEnd.processCpuMs)
        val outcome = when {
            session.inputRejected -> AsrSessionOutcome.INPUT_REJECTED
            result.text.isNotBlank() -> AsrSessionOutcome.SUCCESS
            else -> AsrSessionOutcome.EMPTY_RESULT
        }
        emit(
            metrics(
                session = session,
                runtimeEnd = runtimeEnd,
                finishMs = finishMs,
                processCpuMs = cpuDelta,
                finalTextChars = result.text.length,
                outcome = outcome
            )
        )
    }

    private fun recordCancelledIfNeeded() {
        val session = active ?: return
        val runtimeEnd = safeRuntimeSnapshot()
        emit(
            metrics(
                session = session,
                runtimeEnd = runtimeEnd,
                finishMs = 0L,
                processCpuMs = delta(session.runtimeStart.processCpuMs, runtimeEnd.processCpuMs),
                finalTextChars = 0,
                outcome = AsrSessionOutcome.CANCELLED
            )
        )
        active = null
    }

    private fun emit(metrics: AsrSessionMetrics) {
        store.append(metrics)
        try {
            publisher.publish(metrics)
        } catch (_: Throwable) {
            // Diagnostics export must never break or alter recognition behavior.
        }
    }

    private fun metrics(
        session: ActiveSession,
        runtimeEnd: AsrRuntimeSnapshot,
        finishMs: Long,
        processCpuMs: Long?,
        finalTextChars: Int,
        outcome: AsrSessionOutcome
    ): AsrSessionMetrics = AsrSessionMetrics(
        backend = backend,
        modelSource = modelSource,
        sampleRate = sampleRate,
        audioSamples = session.audioSamples,
        sessionWallMs = (clock.nowMs() - session.startedAtMs).coerceAtLeast(0L),
        asrComputeMs = session.computeMs,
        finishMs = finishMs,
        firstPartialMs = session.firstPartialMs,
        processCpuMs = processCpuMs,
        pssBeforeKb = session.runtimeStart.pssKb,
        pssAfterKb = runtimeEnd.pssKb,
        endpointSeen = session.endpointSeen,
        finalTextChars = finalTextChars,
        outcome = outcome,
        vadSpeechRmsThreshold = session.endpointConfig?.speechRmsThreshold,
        vadMinSpeechMs = session.endpointConfig?.minSpeechMs,
        vadTrailingSilenceMs = session.endpointConfig?.trailingSilenceMs,
        maxListenMs = session.endpointConfig?.maxListenMs
    )

    private fun safeRuntimeSnapshot(): AsrRuntimeSnapshot = try {
        runtimeProbe.snapshot()
    } catch (_: Throwable) {
        AsrRuntimeSnapshot()
    }

    private fun safeEndpointConfig(): AsrEndpointConfig? = try {
        endpointConfigProvider()?.normalized()
    } catch (_: Throwable) {
        null
    }

    private fun delta(before: Long?, after: Long?): Long? {
        if (before == null || after == null || after < before) return null
        return after - before
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Long> {
        val before = clock.nowMs()
        val result = block()
        val elapsed = (clock.nowMs() - before).coerceAtLeast(0L)
        return result to elapsed
    }
}
