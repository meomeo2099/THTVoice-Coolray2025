package com.voicegeely.app.kws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaKwsModelSpecTest {
    @Test
    fun usesDedicatedSmallKwsModel() {
        assertTrue(SherpaKwsModelSpec.MODEL_DIR.contains("kws-zipformer-zh-en-3M"))
        assertTrue(SherpaKwsModelSpec.ENCODER.endsWith(".int8.onnx"))
        assertTrue(SherpaKwsModelSpec.JOINER.endsWith(".int8.onnx"))
        assertEquals(16_000, SherpaKwsModelSpec.SAMPLE_RATE)
        assertEquals("HEY_THT", SherpaKwsModelSpec.WAKE_LABEL)
    }

    @Test
    fun everyWakeProfileUsesDedicatedKeywordFileInsidePinnedModelDirectory() {
        WakePhrase.values().forEach { phrase ->
            val keywordPath = SherpaKwsModelSpec.keywords(phrase)
            assertTrue(keywordPath.startsWith(SherpaKwsModelSpec.MODEL_DIR + "/"))
            assertTrue(keywordPath.endsWith(phrase.keywordFileName))
            assertTrue(SherpaKwsModelSpec.requiredAssets(phrase).contains(keywordPath))
        }
    }

    @Test
    fun allDefaultRuntimeAssetsStayInsidePinnedModelDirectory() {
        assertTrue(SherpaKwsModelSpec.REQUIRED_ASSETS.isNotEmpty())
        SherpaKwsModelSpec.REQUIRED_ASSETS.forEach { path ->
            assertTrue(path.startsWith(SherpaKwsModelSpec.MODEL_DIR + "/"))
        }
    }
}
