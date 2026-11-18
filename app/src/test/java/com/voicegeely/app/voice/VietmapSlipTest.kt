package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Owner test 13/11/2026: "Vietmap Live" heard as "viên map li", "việc map lie"… opened the default map. */
class VietmapSlipTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test
    fun vietmapLiveSlipsOpenVietmap() {
        listOf("mở viên map li", "mở việc map lie", "mở vietmap lie", "mở việt map lai", "mở viên map", "mở viện map",
            "mở viết mép li", "mở vietmap live", "mở vietmap", "mở map live", "mở map lie")
            .forEach { assertEquals(it, VoiceCommand.OpenApp(AppTarget.VIETMAP), cmd(it)) }
    }

    @Test
    fun plainMapStillOpensTheSelectedMap() {
        listOf("mở map", "mở bản đồ", "mở bảng đồ")
            .forEach { assertEquals(it, VoiceCommand.OpenApp(AppTarget.DEFAULT_MAPS), cmd(it)) }
        assertEquals(VoiceCommand.OpenApp(AppTarget.MAPS), cmd("mở google map"))
    }

    @Test
    fun vietmapIsSpokenInVietnamese() {
        assertEquals("Đã mở việt mép lai.", SpokenTerms.forSpeech("Đã mở Vietmap Live."))
        assertEquals("Đã mở việt mép.", SpokenTerms.forSpeech("Đã mở Vietmap."))
    }
}
