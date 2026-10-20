package com.voicegeely.app.asr

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class SherpaAsrBackendSpecTest {
    @Test fun defaultsToOnlineForOriginalStreamingArchive() {
        val dir = createTempDirectory("tht-asr-online-").toFile()
        try {
            assertEquals(SherpaAsrBackend.ONLINE_TRANSDUCER, SherpaAsrBackendSpec.detect(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun explicitOfflineMarkerSelectsOfflineRecognizer() {
        val dir = createTempDirectory("tht-asr-offline-").toFile()
        try {
            File(dir, SherpaAsrBackendSpec.MARKER_FILE).writeText("offline-transducer\n")
            assertEquals(SherpaAsrBackend.OFFLINE_TRANSDUCER, SherpaAsrBackendSpec.detect(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
