package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Southern, Central and Northern ways of saying the same command (08/10/2026). */
class RegionalSpeechRegressionTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    private fun same(regional: String, standard: String) {
        val expected = cmd(standard)
        assertNotNull(standard, expected)
        assertEquals(regional, expected, cmd(regional))
    }

    @Test fun climate() {
        same("tắc máy lạnh", "tắt điều hòa")
        same("tắc điều hòa", "tắt điều hòa")
        same("bậc điều hòa", "bật điều hòa")
        same("bật máy lạnh nha", "bật điều hòa")
        same("bật điều hòa nhen", "bật điều hòa")
        same("bật điều hòa hỉ", "bật điều hòa")
        same("mở quạc", "bật quạt")
        same("tăng quạc lên", "tăng quạt")
        same("cho tui hai mươi hai độ", "22 độ")
        same("gió dô chân", "gió xuống chân")
        same("gió thổi dô mặt", "gió vào mặt")
    }

    @Test fun windows() {
        same("hạ kiếng xuống hết", "hạ hết kính")
        same("đóng hết kiếng", "đóng hết kính")
        // Owner decision 14/10/2026: no window named → the assistant asks which (WindowClarification).
        assertEquals(null, cmd("kéo kiếng lên"))
        assertEquals(null, cmd("kéo kính nên"))
        same("kéo kiếng lên hết", "đóng hết kính")
        same("mở kiếng bên tài", "mở kính bên lái")
    }

    @Test fun mediaAndApps() {
        same("mở nhạc giùm", "mở nhạc")
        same("mở nhạc dùm tui", "mở nhạc")
        same("tắt nhạc nghen", "tắt nhạc")
        same("mở dùm cái youtube", "mở youtube")
        same("xài google map", "mở google maps")
    }

    @Test fun questionsAreNotCommands() {
        same("đèn viền có bật hông", "đèn viền đang bật không")
        same("đèn viền có bật không", "đèn viền đang bật không")
        assertEquals(VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.AMBIENT_LIGHT_POWER), cmd("đèn viền có bật hông"))
    }

    @Test fun unrelatedWordsUntouched() {
        assertEquals("mở nhạc nhẹ", VoiceTextNormalizer.normalize("mở nhạc nhẹ"))
        assertEquals("tắt đèn viền", VoiceTextNormalizer.normalize("tắt đèn viền"))
    }
}
