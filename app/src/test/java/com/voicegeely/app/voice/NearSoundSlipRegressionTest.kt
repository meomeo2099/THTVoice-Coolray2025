package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Command words heard with the wrong tone or a close syllable (09/10/2026). */
class NearSoundSlipRegressionTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    private fun same(slip: String, standard: String) {
        val expected = cmd(standard)
        assertNotNull(standard, expected)
        assertEquals(slip, expected, cmd(slip))
    }

    @Test fun climate() {
        for (s in listOf("bặt điều hòa", "bất điều hòa", "bật điều hoa", "bật điều khoa", "bật điều hào")) same(s, "bật điều hòa")
        for (s in listOf("tất điều hòa", "tát điều hòa", "tặt điều hòa")) same(s, "tắt điều hòa")
        for (s in listOf("tăng nhiệt đổ", "tăng nhiệt đồ")) same(s, "tăng nhiệt độ")
        for (s in listOf("giảm nhiệt đổ", "dảm nhiệt độ", "giám nhiệt độ")) same(s, "giảm nhiệt độ")
        for (s in listOf("tăng quạ", "tăng quát")) same(s, "tăng quạt")
        same("giảm quạ", "giảm quạt")
        for (s in listOf("lấy dó trong", "lấy gió chong", "lấy giò trong")) same(s, "lấy gió trong")
        for (s in listOf("sấy kín", "xấy kính")) same(s, "sấy kính")
    }

    @Test fun mediaWindowsLights() {
        for (s in listOf("mỡ nhạc", "mớ nhạc", "mở nhạt", "mở nhặc")) same(s, "mở nhạc")
        same("tắt nhạt", "tắt nhạc")
        same("bày tiếp", "bài tiếp theo")
        for (s in listOf("tăng âm lượn", "tăng âm lương")) same(s, "tăng âm lượng")
        same("giảm âm lượn", "giảm âm lượng")
        for (s in listOf("đông hết kính", "đống hết kính")) same(s, "đóng hết kính")
        same("mở kín bên lái", "mở kính bên lái")
        for (s in listOf("bật đèn viên", "bật đen viền", "bật đèn viện")) same(s, "bật đèn viền")
        for (s in listOf("mở bảng đồ", "mở bản độ")) same(s, "mở bản đồ")
    }

    @Test fun ordinaryWordsUntouched() {
        assertEquals("hoa hồng", VoiceTextNormalizer.normalize("hoa hồng"))
        assertEquals("trời đông lạnh", VoiceTextNormalizer.normalize("trời đông lạnh"))
        assertEquals("bánh mì nhạt", VoiceTextNormalizer.normalize("bánh mì nhạt"))
    }
}
