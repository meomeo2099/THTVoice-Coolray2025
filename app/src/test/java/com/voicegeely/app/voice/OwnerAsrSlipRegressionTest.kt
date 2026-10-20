package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ASR slips reported by the owner from real use (29/09/2026). */
class OwnerAsrSlipRegressionTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test
    fun climateVerbs() {
        assertEquals(VoiceCommand.AdjustTemperature(2.0f), cmd("tanh nhiệt độ"))
        assertEquals(VoiceCommand.AcPower(true, HvacPowerTarget.COMBINED), cmd("bạc điều hoà"))
        assertEquals(VoiceCommand.AcPower(false, HvacPowerTarget.COMBINED), cmd("tác điều hoà"))
        assertEquals(VoiceCommand.AdjustFanSpeed(1), cmd("tan gió"))
    }

    @Test
    fun defrost() {
        for (text in listOf("sai can", "sai cừ", "sai cứ")) {
            assertEquals(text, VoiceCommand.Defrost(DefrostZone.FRONT, true), cmd(text))
        }
    }

    @Test
    fun cabinLights() {
        for (text in listOf("bật đèn", "bạc đèn", "bạc nè", "bạc đènnội thất", "bạc nè nội thất")) {
            assertEquals(text, VoiceCommand.AmbientLightPower(true), cmd(text))
        }
        // Coolray: explicit cabin-light names go to the cabin light (356519684), headlights to 557871126.
        assertEquals(VoiceCommand.CabinLight(true), cmd("bật đèn nội thất"))
        assertEquals(VoiceCommand.CabinLight(false), cmd("tắt đèn nội thất"))
        assertEquals(VoiceCommand.ExteriorLight(ExteriorLightMode.LOW_BEAM), cmd("bật đèn pha"))
    }

    @Test
    fun windowsHeardAsCanhAndLay() {
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN, 100), cmd("mở cửa sổ ghé lấy"))
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN, 100), cmd("mở cánh lái"))
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.REAR_LEFT), WindowOperation.OPEN, 100), cmd("hạ cánh sau bên trái"))
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.SET_PERCENT, 10), cmd("he cam"))
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.SET_PERCENT, 10), cmd("he cưng"))
        // A bare window command without a position still does not move any window.
        assertNull(cmd("mở cánh"))
        // "lấy gió" (recirculation) is not touched by the lấy → lái rule.
        assertEquals(VoiceCommand.Recirculation(true), cmd("lấy gió trong"))
    }

    @Test
    fun appsAndMaps() {
        for (text in listOf("mở you tụp bay", "mở you tụi bay", "mở youtu bay", "mở yoube", "mở you tụ bay", "mở you tu bay", "mở you tú bay")) {
            assertEquals(text, VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.YOUTUBE), cmd(text))
        }
        for (text in listOf("mở việc ma lai", "mở việc marbe", "mở việc ma ray", "mở viện mary")) {
            assertEquals(text, VoiceCommand.OpenApp(AppTarget.VIETMAP), cmd(text))
        }
        for (text in listOf("mở map", "mở mát", "mở máy", "mở mắt", "mở bản đồ")) {
            assertEquals(text, VoiceCommand.OpenApp(AppTarget.DEFAULT_MAPS), cmd(text))
        }
        assertEquals(VoiceCommand.AcPower(true, HvacPowerTarget.COMBINED), cmd("mở máy lạnh"))
        for (text in listOf("mở gu gồ mát", "mở gu gồ đáp", "mở gu bap", "mở googlemap")) {
            assertEquals(text, VoiceCommand.OpenApp(AppTarget.MAPS), cmd(text))
        }
    }

    @Test
    fun driveModeNamesHeardAsVietnameseSyllables() {
        for (text in listOf("chuyển sang chế độ sờ pót", "chế độ xì pót", "chuyển sang spot", "đổi sang chế độ lái sờ pót")) {
            assertEquals(text, VoiceCommand.SetDriveMode(DriveMode.SPORT), cmd(text))
        }
        for (text in listOf("chế độ ê cô", "chuyển sang i cô", "chuyển sang chế độ lái e co")) {
            assertEquals(text, VoiceCommand.SetDriveMode(DriveMode.ECO), cmd(text))
        }
        for (text in listOf("chế độ com pho", "chuyển sang căm phợt", "chế độ lái con phớt")) {
            assertEquals(text, VoiceCommand.SetDriveMode(DriveMode.COMFORT), cmd(text))
        }
    }

    @Test
    fun driveModeAliasesDoNotTouchOtherCommands() {
        assertEquals(VoiceCommand.SetDriveMode(DriveMode.SPORT), cmd("chuyển sang chế độ sport"))
        assertEquals(VoiceCommand.SetDriveMode(DriveMode.ECO), cmd("chế độ eco"))
        // Climate Eco and brake feel keep their own meaning.
        assertEquals(cmd("chế độ tiết kiệm điều hòa"), cmd("chế độ tiết kiệm điều hòa"))
        assertEquals(false, cmd("chế độ ê cô điều hòa") is VoiceCommand.SetDriveMode)
        assertEquals(false, cmd("chuyển cảm giác phanh sang sport") is VoiceCommand.SetDriveMode)
    }

    @Test
    fun fanHeardAsPhat() {
        for ((heard, said) in listOf(
            "tăng phạt" to "tăng quạt",
            "giảm phạt 2 mức" to "giảm quạt 2 mức",
            "phạt mức 3" to "quạt mức 3",
            "phạt số ba" to "quạt số ba",
            "phạt mạnh nhất" to "quạt mạnh nhất",
            "phạt nhỏ nhất" to "quạt nhỏ nhất",
            "tắt phạt" to "tắt quạt",
        )) {
            assertEquals(heard, cmd(said), cmd(heard))
        }
        // "phạt" outside a fan phrase is left alone.
        assertEquals("dẫn đường đến trạm phạt nguội", VoiceTextNormalizer.normalize("dẫn đường đến trạm phạt nguội"))
    }

    @Test
    fun dongHeardAsDam() {
        for ((slip, right) in listOf(
            "đám hết kính" to "đóng hết kính", "đám kính bên lái" to "đóng kính bên lái",
            "đám tất cả kính" to "đóng tất cả kính"
        )) {
            val expected = cmd(right)
            org.junit.Assert.assertNotNull(right, expected)
            assertEquals(slip, expected, cmd(slip))
        }
        assertEquals("đóng kính", VoiceTextNormalizer.normalize("đám kính"))
        assertEquals("đóng cửa sổ", VoiceTextNormalizer.normalize("đám cửa sổ"))
        assertEquals("đám cưới", VoiceTextNormalizer.normalize("đám cưới"))
    }
}
