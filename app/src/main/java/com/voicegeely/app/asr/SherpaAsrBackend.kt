package com.voicegeely.app.asr

import java.io.File

enum class SherpaAsrBackend {
    ONLINE_TRANSDUCER,
    OFFLINE_TRANSDUCER
}

/** Explicit backend marker prevents loading an offline transducer with OnlineRecognizer. */
object SherpaAsrBackendSpec {
    const val MARKER_FILE = "asr-backend.txt"
    const val ONLINE_MARKER = "online-transducer"
    const val OFFLINE_MARKER = "offline-transducer"

    fun detect(modelDir: File): SherpaAsrBackend {
        val marker = File(modelDir, MARKER_FILE)
        val value = runCatching { marker.readText().trim().lowercase() }.getOrDefault("")
        return when (value) {
            OFFLINE_MARKER -> SherpaAsrBackend.OFFLINE_TRANSDUCER
            ONLINE_MARKER -> SherpaAsrBackend.ONLINE_TRANSDUCER
            // Backward compatibility for the original explicitly streaming archive.
            else -> SherpaAsrBackend.ONLINE_TRANSDUCER
        }
    }
}

object SherpaAsrEngineFactory {
    private const val MODEL_SOURCE_FILE = "model-source.txt"

    fun create(modelDir: File, numThreads: Int = 2): OfflineAsrEngine {
        val backend = SherpaAsrBackendSpec.detect(modelDir)
        val delegate = when (backend) {
            SherpaAsrBackend.ONLINE_TRANSDUCER -> SherpaOnlineTransducerAsrEngine(modelDir, numThreads)
            SherpaAsrBackend.OFFLINE_TRANSDUCER -> SherpaBufferedOfflineAsrEngine(modelDir, numThreads)
        }

        return InstrumentedOfflineAsrEngine(
            delegate = delegate,
            backend = when (backend) {
                SherpaAsrBackend.ONLINE_TRANSDUCER -> SherpaAsrBackendSpec.ONLINE_MARKER
                SherpaAsrBackend.OFFLINE_TRANSDUCER -> SherpaAsrBackendSpec.OFFLINE_MARKER
            },
            modelSource = readModelSource(modelDir),
            publisher = AndroidAsrDiagnosticsLog
        )
    }

    private fun readModelSource(modelDir: File): String? = runCatching {
        File(modelDir, MODEL_SOURCE_FILE)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
