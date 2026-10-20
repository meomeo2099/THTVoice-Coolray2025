package com.voicegeely.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRuntimeStateTest {
    @Test
    fun partialThenFinalMovesToProcessingAndCreatesHistory() {
        var now = 1_000L
        val store = AssistantRuntimeStore(now = { now })
        store.onServiceReady()

        store.onRecognition("đặt nhiệt độ hai mươi hai", isFinal = false)
        assertEquals(AssistantRuntimePhase.LISTENING, store.state.phase)
        assertEquals("đặt nhiệt độ hai mươi hai", store.state.partialText)

        now = 2_000L
        store.onRecognition("đặt nhiệt độ hai mươi hai", isFinal = true)
        assertEquals(AssistantRuntimePhase.PROCESSING, store.state.phase)
        assertEquals("đặt nhiệt độ hai mươi hai", store.state.finalText)
        assertEquals(1, store.state.history.size)
        assertEquals("Đang xử lý...", store.state.history.first().result)
    }

    @Test
    fun engineHealthComesOnlyFromBroadcastValues() {
        val store = AssistantRuntimeStore()
        store.onEngineStatus(
            message = "Hey THT đang chờ lệnh",
            asrReady = true,
            kwsReady = false,
            ttsReady = true
        )

        assertEquals(AssistantRuntimePhase.KWS_LISTENING, store.state.phase)
        assertTrue(store.state.asrReady)
        assertFalse(store.state.kwsReady)
        assertTrue(store.state.ttsReady)
    }

    @Test
    fun finalCommandHistoryIsUpdatedByResultStatus() {
        var now = 10L
        val store = AssistantRuntimeStore(now = { now })
        store.onRecognition("mở youtube", isFinal = true)
        now = 20L
        store.onEngineStatus("Đã mở YouTube", asrReady = true, kwsReady = true, ttsReady = true)

        assertEquals(AssistantRuntimePhase.RESULT, store.state.phase)
        assertEquals(1, store.state.history.size)
        assertEquals("Đã mở YouTube", store.state.history.first().result)
        assertEquals(20L, store.state.history.first().timestampMs)
    }

    @Test
    fun laterIdleStatusesDoNotOverwriteCommandResult() {
        val store = AssistantRuntimeStore()
        store.onRecognition("mở youtube", isFinal = true)
        store.onEngineStatus("Đã mở YouTube.", asrReady = true, kwsReady = true, ttsReady = true)
        store.onEngineStatus("Hey Ge-ly đang chờ lệnh", asrReady = true, kwsReady = true, ttsReady = true)
        assertEquals(AssistantRuntimePhase.KWS_LISTENING, store.state.phase)
        store.onEngineStatus("Đã nghe Hey Ge-ly", asrReady = true, kwsReady = true, ttsReady = true)
        store.onEngineStatus("Không nghe rõ lệnh", asrReady = true, kwsReady = true, ttsReady = true)
        assertEquals("Đã mở YouTube.", store.state.history.single().result)
    }

    @Test
    fun errorStatusIsNotRenderedAsSuccess() {
        val store = AssistantRuntimeStore()
        store.onRecognition("hạ kính bên lái", isFinal = true)
        store.onEngineStatus("Không đọc được trạng thái xe", asrReady = true, kwsReady = true, ttsReady = true)

        assertEquals(AssistantRuntimePhase.ERROR, store.state.phase)
        assertEquals("Không đọc được trạng thái xe", store.state.history.first().result)
    }

    @Test
    fun historyIsBounded() {
        var now = 0L
        val store = AssistantRuntimeStore(maxHistory = 2, now = { ++now })
        listOf("lệnh một", "lệnh hai", "lệnh ba").forEach {
            store.onRecognition(it, isFinal = true)
            store.onEngineStatus("Đã xử lý $it", true, true, true)
        }

        assertEquals(2, store.state.history.size)
        assertEquals("lệnh ba", store.state.history.first().command)
    }

    @Test
    fun screenOpenedAfterServiceStartShowsLastReadiness() {
        val store = AssistantRuntimeStore()
        store.initialize(serviceReady = true, readiness = EngineReadiness(asrReady = true, kwsReady = true, ttsReady = true))
        assertTrue(store.state.asrReady)
        assertTrue(store.state.kwsReady)
        assertTrue(store.state.ttsReady)
        assertEquals(AssistantRuntimePhase.READY, store.state.phase)
    }

    @Test
    fun noServiceMeansNotReadyEvenWithStaleReadiness() {
        val store = AssistantRuntimeStore()
        store.initialize(serviceReady = false, readiness = EngineReadiness(asrReady = true, kwsReady = true, ttsReady = true))
        assertFalse(store.state.asrReady)
        assertFalse(store.state.ttsReady)
    }

    @Test
    fun ttsFailureReasonIsKeptForHome() {
        val store = AssistantRuntimeStore()
        store.onEngineStatus("THT Voice", asrReady = true, kwsReady = true, ttsReady = false, ttsDetail = "Chưa có Kokoro Vietnamese offline")
        assertFalse(store.state.ttsReady)
        assertEquals("Chưa có Kokoro Vietnamese offline", store.state.ttsDetail)
    }
}
