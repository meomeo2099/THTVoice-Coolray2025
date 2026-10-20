package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 15/10/2026: "cốp" heard as "cốt", and slips for the newest command words. */
class NewWordSlipsTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command
    private fun n(text: String) = VoiceTextNormalizer.normalize(text)
    private fun same(variant: String, standard: String) {
        val expected = cmd(standard)
        assertNotNull(standard, expected)
        assertEquals(variant, expected, cmd(variant))
    }

    @Test fun trunkSlips() {
        for (t in listOf("mở cốt", "mở cóp", "mở cộp", "mở kốp", "mở cốc", "mở cái cốt", "mở cốt xe")) assertEquals(t, "mở cốp", n(t).replace(" cái", "").replace(" xe", ""))
        for (t in listOf("mở cốt sau", "mở cóp sau", "mở cốt xau", "mở cốp sao", "mở cộp hậu")) same(t, "mở cốp sau")
        for (t in listOf("mở cốt trước", "mở cốp chước", "mở cóp trướt", "mở cốt chước")) same(t, "mở cốp trước")
        assertEquals("đóng cốp", n("đóng cốt"))
    }

    @Test fun windowSlips() {
        same("mở kinh lái", "mở kính lái")
        same("đóng kín sau", "đóng kính sau")
        same("mở kính chước", "mở kính trước")
        same("đóng kính xau", "đóng kính sau")
    }

    @Test fun volumeSlips() {
        for (t in listOf("âm lường 15", "ăm lượng 15", "am lượng 15", "âm lượt 15", "vô lum 15")) same(t, "âm lượng 15")
        same("chỉnh noa lên 12", "chỉnh loa lên 12")
        same("tăng âm lượng 3 nất", "tăng âm lượng 3 nấc")
        same("giảm loa 2 mứt", "giảm loa 2 mức")
        same("âm lượng 50 phần chăm", "âm lượng 50 phần trăm")
        same("âm lượng 50 phầm trăm", "âm lượng 50 phần trăm")
        same("âm lượng tối da", "âm lượng tối đa")
        same("âm lượng lớn nhứt", "âm lượng lớn nhất")
        same("loa nhỏ nhứt", "loa nhỏ nhất")
    }

    @Test fun othersUnchanged() {
        assertEquals("cốt truyện hay", n("cốt truyện hay"))
        assertEquals("ly cốc", n("ly cốc"))
        assertEquals("kinh doanh", n("kinh doanh"))
        assertEquals("đóng kính lại", n("đóng kính lại"))
        assertEquals("sao rồi", n("sao rồi"))
        assertEquals(VoiceCommand.SetFanSpeed(3), cmd("quạt mức 3"))
    }
}
