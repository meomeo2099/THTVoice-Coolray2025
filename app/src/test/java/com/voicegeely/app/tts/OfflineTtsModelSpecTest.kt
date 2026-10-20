package com.voicegeely.app.tts

import com.voicegeely.app.settings.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineTtsModelSpecTest {
    @Test fun bothNamedVoicesUseSharedKokoroRuntimeWithStableSpeakerIds() {
        assertEquals("models/tts/kokoro_vi", OfflineTtsModelSpec.assetDir(TtsVoice.NGOC_HUYEN))
        assertEquals("models/tts/kokoro_vi", OfflineTtsModelSpec.assetDir(TtsVoice.DIEM_TRINH))
        assertEquals(0, OfflineTtsModelSpec.speakerId(TtsVoice.NGOC_HUYEN))
        assertEquals(1, OfflineTtsModelSpec.speakerId(TtsVoice.DIEM_TRINH))
    }

    @Test fun completeKokoroLayoutIsDiscovered() {
        val files = OfflineTtsModelSpec.discover(
            listOf(
                "kokoro_vi.onnx",
                "voices.bin",
                "tokens.txt",
                "lexicon-vi.txt",
                "voice-map.txt",
                "SOURCE.txt",
                "config.json"
            ),
            hasEspeakData = true
        )
        assertNotNull(files)
        assertEquals("kokoro_vi.onnx", files!!.model)
        assertEquals("voices.bin", files.voices)
        assertEquals("lexicon-vi.txt", files.lexicon)
    }

    @Test fun missingRequiredKokoroAssetOrEspeakIsRejected() {
        val complete = listOf(
            "kokoro_vi.onnx", "voices.bin", "tokens.txt", "lexicon-vi.txt", "voice-map.txt", "SOURCE.txt"
        )
        assertNull(OfflineTtsModelSpec.discover(complete - "voices.bin", hasEspeakData = true))
        assertNull(OfflineTtsModelSpec.discover(complete, hasEspeakData = false))
    }
}
