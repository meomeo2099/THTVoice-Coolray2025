package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Car log 18/11/2026: "mở bản đồ" was heard as "mở bán đồ" and searched for an app named "bán đồ". */
class MapSlipTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test
    fun toneSlipsOpenTheMap() {
        for (t in listOf("mở bán đồ", "mở bạn đồ", "mở bàn đồ", "mở bản đố", "bật bán đồ", "mở bán đồ đi", "mở bản đồ"))
            assertEquals(t, VoiceCommand.OpenApp(AppTarget.DEFAULT_MAPS), cmd(t))
    }

    @Test
    fun otherSentencesWithTheWordStayAsTheyAre() {
        // Only the whole "mở … đồ" command is corrected; buying / selling and destinations are untouched.
        assertTrue(VoiceTextNormalizer.normalize("tìm cửa hàng bán đồ ăn").contains("bán đồ"))
    }
}
