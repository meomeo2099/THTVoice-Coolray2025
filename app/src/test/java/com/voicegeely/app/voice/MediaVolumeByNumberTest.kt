package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner request 15/10/2026: chỉnh âm lượng theo số. */
class MediaVolumeByNumberTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command
    private fun level(n: Int) = VoiceCommand.SetMediaVolume(n)
    private fun percent(n: Int) = VoiceCommand.SetMediaVolume(n, VolumeUnit.PERCENT)

    @Test fun absoluteLevels() {
        for ((t, n) in listOf(
            "âm lượng mười lăm" to 15, "âm lượng 15" to 15, "chỉnh âm lượng lên hai mươi" to 20, "volume 10" to 10,
            "để loa mức mười" to 10, "âm lượng hai lăm" to 25, "âm lượng hăm hai" to 22, "âm lượng số 8" to 8,
            "vặn loa lên 12" to 12, "âm lượng mức 7" to 7, "giảm âm lượng xuống 10" to 10,
            "tăng âm lượng 20" to 20, "âm lượng 0" to 0
        )) assertEquals(t, level(n), cmd(t))
    }

    @Test fun percentAndExtremes() {
        assertEquals(percent(50), cmd("âm lượng năm mươi phần trăm"))
        assertEquals(percent(50), cmd("âm lượng 50%"))
        for (t in listOf("âm lượng tối đa", "âm lượng lớn nhất", "loa to nhất", "âm lượng hết cỡ")) assertEquals(t, percent(100), cmd(t))
        for (t in listOf("âm lượng nhỏ nhất", "âm lượng thấp nhất", "loa bé nhất")) assertEquals(t, level(1), cmd(t))
    }

    @Test fun steps() {
        assertEquals(VoiceCommand.AdjustMediaVolume(3), cmd("tăng âm lượng ba nấc"))
        assertEquals(VoiceCommand.AdjustMediaVolume(-2), cmd("giảm loa hai mức"))
        assertEquals(VoiceCommand.AdjustMediaVolume(3), cmd("tăng âm lượng 3"))
        assertEquals(VoiceCommand.AdjustMediaVolume(-4), cmd("hạ âm lượng 4 bậc"))
    }

    @Test fun outOfRangeIsRejectedNotGuessed() {
        for (t in listOf("âm lượng 150", "âm lượng 120 phần trăm", "tăng âm lượng 20 nấc")) assertNull(t, cmd(t))
    }

    @Test fun othersUnchanged() {
        assertEquals(VoiceCommand.MediaControl(MediaAction.VOLUME_UP), cmd("tăng âm lượng"))
        assertEquals(VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN), cmd("giảm âm lượng"))
        assertEquals(VoiceCommand.SetFanSpeed(3), cmd("quạt mức 3"))
        assertEquals(VoiceCommand.SetTemperature(22f), cmd("nhiệt độ 22"))
        assertNull(cmd("mở loa"))
    }

    @Test fun math() {
        assertEquals(15, MediaVolumeMath.target(15, VolumeUnit.LEVEL, 0, 39))
        assertNull(MediaVolumeMath.target(40, VolumeUnit.LEVEL, 0, 39))
        assertEquals(20, MediaVolumeMath.target(50, VolumeUnit.PERCENT, 0, 39))
        assertEquals(39, MediaVolumeMath.target(100, VolumeUnit.PERCENT, 0, 39))
        assertEquals(1, MediaVolumeMath.target(0, VolumeUnit.PERCENT, 1, 39))
        assertEquals(39, MediaVolumeMath.adjust(37, 5, 0, 39))
        assertEquals(0, MediaVolumeMath.adjust(2, -5, 0, 39))
    }
}
