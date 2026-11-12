package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OwnerShadeWindowAsrTest {
    private fun parse(text: String) = VoiceParseChain.parse(text)?.command

    @Test fun driverSideAsrTruncationNeverWidensToRearPair() {
        for (phrase in listOf("hạ kính sau bên lá", "mở kính phía sau bên lá", "hạ kính sau lái")) {
            assertEquals(phrase, VoiceCommand.Window(setOf(WindowTarget.REAR_LEFT), WindowOperation.OPEN, 100), parse(phrase))
        }
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.REAR_LEFT), WindowOperation.CLOSE, 0), parse("đóng kính sau bên lá"))
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.REAR_RIGHT), WindowOperation.OPEN, 100), parse("hạ kính sau phụ"))
    }

    @Test fun unrecognizedSideDoesNotBecomeBothRearWindows() {
        assertFalse(parse("hạ kính sau bên lạ") is VoiceCommand.Window)
        assertFalse(parse("đóng kính sau ghế không rõ") is VoiceCommand.Window)
        assertEquals(VoiceCommand.Window(setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT), WindowOperation.OPEN, 100), parse("hạ cả hai kính sau"))
    }

    @Test fun fanMisheardAsHoatInFanCommandContextOnly() {
        assertEquals(VoiceCommand.AdjustFanSpeed(1), parse("tăng hoạt"))
        assertEquals(VoiceCommand.AdjustFanSpeed(-1), parse("giảm hoạt"))
        for (text in listOf("chỉnh hoạt mức ba", "hoạt số ba", "tốc độ hoạt ba")) {
            assertEquals(text, VoiceCommand.SetFanSpeed(3), parse(text))
        }
        assertEquals("kiểm tra hoạt động của xe", VoiceTextNormalizer.normalize("kiểm tra hoạt động của xe"))
        assertEquals("mở hoạt cảnh", VoiceTextNormalizer.normalize("mở hoạt cảnh"))
        assertEquals("đi đến đường hoạt", VoiceTextNormalizer.normalize("đi đến đường Hoạt"))
    }

    @Test fun closingShadeWithStuckZeroReadStillSendsCloseAndNeverClaimsConfirmed() = runBlocking {
        val port = FakeEcarxPort(reflectWrites = false).apply { mcuAccepts = true; props[F.INFO_ID_WINDOW_SUNSHADE] = 0 }
        val result = SunroofVoiceCommandExecutor(port) {}.execute(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.CLOSE))
        assertEquals(1, port.mcuWrites.size)
        assertEquals(listOf<Byte>(-1, -1, -1, -1, -1, -1, 0, -1), port.mcuWrites.single().second.toList())
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    @Test fun closingShadeFromObservedOpenPositionCanConfirmAfterReadback() = runBlocking {
        val port = FakeEcarxPort().apply { mcuAccepts = true; props[F.INFO_ID_WINDOW_SUNSHADE] = 100 }
        val command = parse("đóng rèm cửa sổ trời") ?: error("missing shade parser")
        assertEquals(VoiceCommand.Sunroof(SunroofPart.SHADE, WindowOperation.CLOSE, 0), command)
        val result = SunroofVoiceCommandExecutor(port) {}.execute(command)
        assertEquals(1, port.mcuWrites.size)
        assertTrue(result is CommandExecutionResult.Confirmed)
    }

    @Test fun singleRearWindowPayloadTouchesOnlyRequestedMotor() = runBlocking {
        for ((phrase, index, percent) in listOf(Triple("hạ kính sau bên lá", F.GROUP_RL, 100), Triple("hạ kính sau bên phụ", F.GROUP_RR, 100),
            Triple("đóng kính sau bên lá", F.GROUP_RL, 0), Triple("đóng kính sau bên phụ", F.GROUP_RR, 0), Triple("hạ kính sau bên lá 50 phần trăm", F.GROUP_RL, 50))) {
            val port = FakeEcarxPort().apply { mcuAccepts = true; props[F.INFO_ID_WINDOW_RL] = 40; props[F.INFO_ID_WINDOW_RR] = 40 }
            WindowVoiceCommandExecutor(port) {}.execute(parse(phrase) ?: error("missing window parser"))
            assertEquals(phrase, 1, port.mcuWrites.size)
            val bytes = port.mcuWrites.single().second
            assertEquals(percent.toByte(), bytes[index])
            assertTrue(bytes.indices.filter { it != index }.all { bytes[it] == (-1).toByte() })
        }
    }
}
