package com.voicegeely.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceOverlayStateMachineTest {
    @Test
    fun partialAndFinalKeepRecognizedTextVisible() {
        val state = VoiceOverlayStateMachine()

        assertEquals(VoiceOverlayPhase.LISTENING, state.listening().phase)
        val partial = state.partial("hạ kính bên lái")
        assertEquals(VoiceOverlayPhase.LISTENING, partial.phase)
        assertEquals("hạ kính bên lái", partial.text)
        assertEquals("hạ kính bên lái", partial.transcript)

        val final = state.processing("hạ kính bên lái")
        assertEquals(VoiceOverlayPhase.PROCESSING, final.phase)
        assertEquals("hạ kính bên lái", final.transcript)
        assertEquals("Vui lòng chờ trong giây lát...", final.result)
    }

    @Test
    fun resultKeepsFinalTranscriptAndAddsExecutionMessage() {
        val state = VoiceOverlayStateMachine()
        state.processing("chỉ đường về nhà")

        val result = state.result("Đang mở Google Maps")
        assertEquals(VoiceOverlayPhase.RESULT, result.phase)
        assertEquals("chỉ đường về nhà", result.transcript)
        assertEquals("Đang mở Google Maps", result.result)
        assertEquals("Đang mở Google Maps", result.text)
    }

    @Test
    fun errorKeepsTranscriptAndIsDistinctFromResult() {
        val state = VoiceOverlayStateMachine()
        state.processing("hạ kính bên lái")

        val error = state.error("Không đọc được trạng thái xe")
        assertEquals(VoiceOverlayPhase.ERROR, error.phase)
        assertEquals("hạ kính bên lái", error.transcript)
        assertEquals("Không đọc được trạng thái xe", error.result)
    }

    @Test
    fun hideClearsAllOldContent() {
        val state = VoiceOverlayStateMachine()
        state.processing("hai mươi hai độ")
        state.result("Đã đặt nhiệt độ 22°C")

        val hidden = state.hide()
        assertEquals(VoiceOverlayPhase.HIDDEN, hidden.phase)
        assertEquals("", hidden.title)
        assertEquals("", hidden.text)
        assertEquals("", hidden.transcript)
        assertEquals("", hidden.result)
    }
}
