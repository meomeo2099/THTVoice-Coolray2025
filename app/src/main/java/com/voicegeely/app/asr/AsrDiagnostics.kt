package com.voicegeely.app.asr

import java.util.ArrayDeque
import java.util.Locale

/** Outcome of one local ASR utterance. Raw recognized text is intentionally not stored. */
enum class AsrSessionOutcome {
    SUCCESS,
    EMPTY_RESULT,
    INPUT_REJECTED,
    CANCELLED
}

data class AsrRuntimeSnapshot(
    val processCpuMs: Long? = null,
    val pssKb: Long? = null
)

fun interface AsrRuntimeProbe {
    fun snapshot(): AsrRuntimeSnapshot
}

fun interface AsrDiagnosticsClock {
    fun nowMs(): Long
}

/** Optional sink for exporting one metrics sample without coupling instrumentation to Android logging. */
fun interface AsrDiagnosticsPublisher {
    fun publish(metrics: AsrSessionMetrics)
}

object NoOpAsrDiagnosticsPublisher : AsrDiagnosticsPublisher {
    override fun publish(metrics: AsrSessionMetrics) = Unit
}

/**
 * One bounded, privacy-preserving ASR performance sample.
 *
 * [asrComputeMs] is wall time spent inside ASR engine API calls. It is not total
 * utterance latency. [computeRealtimeFactor] therefore means ASR API compute time
 * divided by microphone audio duration and must not be presented as a full-system RTF.
 *
 * VAD/endpoint fields contain only numeric tuning metadata captured at session start;
 * no microphone samples or recognized phrases are persisted here.
 */
data class AsrSessionMetrics(
    val backend: String,
    val modelSource: String?,
    val sampleRate: Int,
    val audioSamples: Long,
    val sessionWallMs: Long,
    val asrComputeMs: Long,
    val finishMs: Long,
    val firstPartialMs: Long?,
    val processCpuMs: Long?,
    val pssBeforeKb: Long?,
    val pssAfterKb: Long?,
    val endpointSeen: Boolean,
    val finalTextChars: Int,
    val outcome: AsrSessionOutcome,
    val vadSpeechRmsThreshold: Float? = null,
    val vadMinSpeechMs: Long? = null,
    val vadTrailingSilenceMs: Long? = null,
    val maxListenMs: Long? = null
) {
    val audioDurationMs: Long
        get() = if (sampleRate <= 0 || audioSamples <= 0L) 0L
        else (audioSamples * 1000L) / sampleRate.toLong()

    val computeRealtimeFactor: Double?
        get() = audioDurationMs.takeIf { it > 0L }?.let { asrComputeMs.toDouble() / it.toDouble() }

    val processCpuToWallRatio: Double?
        get() = if (processCpuMs == null || sessionWallMs <= 0L) null
        else processCpuMs.toDouble() / sessionWallMs.toDouble()

    val pssDeltaKb: Long?
        get() = if (pssBeforeKb == null || pssAfterKb == null) null else pssAfterKb - pssBeforeKb
}

class AsrMetricsStore(
    private val capacity: Int = DEFAULT_CAPACITY
) {
    private val entries = ArrayDeque<AsrSessionMetrics>()

    @Synchronized
    fun append(metrics: AsrSessionMetrics) {
        if (capacity <= 0) return
        while (entries.size >= capacity) entries.removeFirst()
        entries.addLast(metrics)
    }

    @Synchronized
    fun snapshot(): List<AsrSessionMetrics> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    companion object {
        const val DEFAULT_CAPACITY = 50
    }
}

object AsrDiagnosticsRegistry {
    val store = AsrMetricsStore()
}

object AsrDiagnosticsFormatter {
    fun format(metrics: AsrSessionMetrics): String = buildString {
        append("ASR backend=").append(metrics.backend)
        metrics.modelSource?.takeIf { it.isNotBlank() }?.let { append(" model=").append(it) }
        append(" outcome=").append(metrics.outcome)
        append(" audio=").append(metrics.audioDurationMs).append("ms")
        append(" wall=").append(metrics.sessionWallMs).append("ms")
        append(" compute=").append(metrics.asrComputeMs).append("ms")
        metrics.computeRealtimeFactor?.let {
            append(" computeRTF=").append(String.format(Locale.US, "%.3f", it))
        }
        append(" finish=").append(metrics.finishMs).append("ms")
        metrics.firstPartialMs?.let { append(" firstPartial=").append(it).append("ms") }
        metrics.processCpuMs?.let { append(" cpu=").append(it).append("ms") }
        metrics.pssAfterKb?.let { append(" pss=").append(it).append("KB") }
        metrics.pssDeltaKb?.let { append(" pssDelta=").append(it).append("KB") }
        append(" endpoint=").append(metrics.endpointSeen)
        metrics.vadSpeechRmsThreshold?.let {
            append(" vadRms=").append(String.format(Locale.US, "%.3f", it))
        }
        metrics.vadMinSpeechMs?.let { append(" minSpeech=").append(it).append("ms") }
        metrics.vadTrailingSilenceMs?.let { append(" silence=").append(it).append("ms") }
        metrics.maxListenMs?.let { append(" maxListen=").append(it).append("ms") }
        append(" chars=").append(metrics.finalTextChars)
    }
}

/** Read-only summary intended for future debug UI/log export on the Coolray head unit. */
object AsrDiagnostics {
    data class Snapshot(
        val totalBuffered: Int,
        val successful: Int,
        val averageWallMs: Long?,
        val averageComputeRtf: Double?,
        val averageProcessCpuMs: Long?,
        val peakPssKb: Long?,
        val lines: List<String>
    )

    fun snapshot(
        limit: Int = 20,
        store: AsrMetricsStore = AsrDiagnosticsRegistry.store
    ): Snapshot {
        val all = store.snapshot()
        val safeLimit = limit.coerceAtLeast(0)
        val rtfs = all.mapNotNull { it.computeRealtimeFactor }
        val cpu = all.mapNotNull { it.processCpuMs }
        val pss = all.mapNotNull { it.pssAfterKb }
        return Snapshot(
            totalBuffered = all.size,
            successful = all.count { it.outcome == AsrSessionOutcome.SUCCESS },
            averageWallMs = all.takeIf { it.isNotEmpty() }?.map { it.sessionWallMs }?.average()?.toLong(),
            averageComputeRtf = rtfs.takeIf { it.isNotEmpty() }?.average(),
            averageProcessCpuMs = cpu.takeIf { it.isNotEmpty() }?.average()?.toLong(),
            peakPssKb = pss.maxOrNull(),
            lines = all.takeLast(safeLimit).map(AsrDiagnosticsFormatter::format)
        )
    }

    fun clear(store: AsrMetricsStore = AsrDiagnosticsRegistry.store) = store.clear()
}
