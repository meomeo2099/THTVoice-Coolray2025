package com.voicegeely.app.asr

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AsrEndpointConfigTest {
    @After
    fun tearDown() {
        AsrEndpointRuntime.resetForTests()
    }

    @Test
    fun defaults_preserveOriginalIhu629gBaseline() {
        val config = AsrEndpointConfig()

        assertEquals(0.012f, config.speechRmsThreshold, 0.00001f)
        assertEquals(700L, config.trailingSilenceMs)
        assertEquals(180L, config.minSpeechMs)
        assertEquals(12_000L, config.maxListenMs)
    }

    @Test
    fun normalized_clampsUnsafeLowValues() {
        val config = AsrEndpointConfig(
            speechRmsThreshold = 0.0001f,
            trailingSilenceMs = 1L,
            minSpeechMs = 1L,
            maxListenMs = 1L
        ).normalized()

        assertEquals(AsrEndpointConfig.MIN_SPEECH_RMS, config.speechRmsThreshold, 0.00001f)
        assertEquals(AsrEndpointConfig.MIN_TRAILING_SILENCE_MS, config.trailingSilenceMs)
        assertEquals(AsrEndpointConfig.MIN_MIN_SPEECH_MS, config.minSpeechMs)
        assertEquals(AsrEndpointConfig.MIN_MAX_LISTEN_MS, config.maxListenMs)
    }

    @Test
    fun normalized_clampsUnsafeHighValues() {
        val config = AsrEndpointConfig(
            speechRmsThreshold = 1.0f,
            trailingSilenceMs = 99_999L,
            minSpeechMs = 99_999L,
            maxListenMs = 99_999L
        ).normalized()

        assertEquals(AsrEndpointConfig.MAX_SPEECH_RMS, config.speechRmsThreshold, 0.00001f)
        assertEquals(AsrEndpointConfig.MAX_TRAILING_SILENCE_MS, config.trailingSilenceMs)
        assertEquals(AsrEndpointConfig.MAX_MIN_SPEECH_MS, config.minSpeechMs)
        assertEquals(AsrEndpointConfig.MAX_MAX_LISTEN_MS, config.maxListenMs)
    }

    @Test
    fun runtime_alwaysStoresNormalizedSnapshot() {
        AsrEndpointRuntime.update(
            AsrEndpointConfig(
                speechRmsThreshold = 0.001f,
                trailingSilenceMs = 5_000L,
                minSpeechMs = 5_000L,
                maxListenMs = 30_000L
            )
        )

        val snapshot = AsrEndpointRuntime.snapshot()
        assertEquals(AsrEndpointConfig.MIN_SPEECH_RMS, snapshot.speechRmsThreshold, 0.00001f)
        assertEquals(AsrEndpointConfig.MAX_TRAILING_SILENCE_MS, snapshot.trailingSilenceMs)
        assertEquals(AsrEndpointConfig.MAX_MIN_SPEECH_MS, snapshot.minSpeechMs)
        assertEquals(AsrEndpointConfig.MAX_MAX_LISTEN_MS, snapshot.maxListenMs)
    }
}
