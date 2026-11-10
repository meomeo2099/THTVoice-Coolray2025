package com.voicegeely.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceOverlayStateMachineTest {
    @Test
    fun popupShowsOnlyTheFinalSentence() {
        val state = VoiceOverlayStateMachine()

        assertEquals(VoiceOverlayPhase.LISTENING, state.listening().phase)
        val partial = state.partial("hạ kính bên lái")
        assertEquals(VoiceOverlayPhase.LISTENING, partial.phase)
        assertEquals("hạ kính bên lái", partial.text)
        assertEquals("hạ kính bên lái", partial.transcript)
        assertEquals("", partial.popupText)

        val final = state.processing("hạ kính bên lái")
        assertEquals(VoiceOverlayPhase.PROCESSING, final.phase)
        assertEquals("hạ kính bên lái", final.transcript)
        assertEquals("hạ kính bên lái", final.popupText)
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
        assertEquals("Đang mở Google Maps", result.popupText)
    }

    @Test
    fun errorKeepsTranscriptAndIsDistinctFromResult() {
        val state = VoiceOverlayStateMachine()
        state.processing("hạ kính bên lái")

        val error = state.error("Không đọc được trạng thái xe")
        assertEquals(VoiceOverlayPhase.ERROR, error.phase)
        assertEquals("hạ kính bên lái", error.transcript)
        assertEquals("Không đọc được trạng thái xe", error.result)
        assertEquals("Không đọc được trạng thái xe", error.popupText)
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

    @Test fun finishingUnresolvedProcessingShowsErrorInsteadOfSpinner() {
        val machine = VoiceOverlayStateMachine()
        machine.processing("câu nhận sai")
        val state = machine.finishProcessing()!!
        assertEquals(VoiceOverlayPhase.ERROR, state.phase)
        assertEquals("câu nhận sai", state.transcript)
        assertEquals(null, machine.finishProcessing())
    }

    @Test fun finishingDoesNotOverwriteResultErrorOrHiddenState() {
        val machine = VoiceOverlayStateMachine()
        machine.result("Đã thực hiện")
        assertEquals(null, machine.finishProcessing())
        assertEquals("Đã thực hiện", machine.state.result)
        machine.error("Xe chưa phản hồi")
        assertEquals(null, machine.finishProcessing())
        assertEquals("Xe chưa phản hồi", machine.state.result)
        machine.hide()
        assertEquals(null, machine.finishProcessing())
        assertEquals(VoiceOverlayPhase.HIDDEN, machine.state.phase)
    }
}

