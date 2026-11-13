package com.voicegeely.app.voice

import com.voicegeely.app.car.Coolray2025Functions as F
import com.voicegeely.app.car.VehicleTelemetryRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OwnerFeedback114Test {
    @Test fun saiOnlyMeansDemistInVehicleContext() {
        for (word in listOf("sai", "sái", "sáy")) {
            assertEquals(VoiceParseChain.parse("bật sấy kính sau")?.command, VoiceParseChain.parse("bật $word kính sau")?.command)
            assertEquals(VoiceParseChain.parse("tắt sấy kính trước")?.command, VoiceParseChain.parse("tắt $word kính trước")?.command)
        }
        for (text in listOf("mở ứng dụng sai", "đi đến tiệm sai", "sai rồi", "bài hát sai kính"))
            assertEquals(text, VoiceTextNormalizer.normalize(text))
    }

    @Test fun damagedRowNeverDefaultsToFrontWindow() {
        for (text in listOf("đóng kính xe bên lái", "hạ kính xe bên phụ 50 phần trăm")) {
            assertNull(text, VoiceParseChain.parse(text))
            assertNotNull(text, WindowClarification().check(text, 0))
        }
    }

    @Test fun rowAnswerPreservesSideAndPercentage() {
        val c = WindowClarification()
        c.check("đóng kính xe bên lái", 0)
        assertEquals(VoiceParseChain.parse("đóng kính sau bên lái")?.command, c.resolve("sau", 1000)?.command)
        c.check("hạ kính xe bên phụ 50 phần trăm", 0)
        assertEquals(VoiceParseChain.parse("hạ kính trước bên phụ 50 phần trăm")?.command, c.resolve("trước", 1000)?.command)
        c.check("đóng kính xe bên lái", 0)
        assertNull(c.resolve("tất cả", 1000))
        c.check("đóng kính xe bên lái", 0)
        assertNull(c.resolve("sau bên phụ", 1000))
        c.check("đóng kính xe bên lái", 0)
        assertNull(c.resolve("sau", WindowClarification.WINDOW_MS + 1))
        assertNotNull(VoiceParseChain.parse("đóng kính sau bên lái"))
        assertNotNull(VoiceParseChain.parse("đóng kính bên lái"))
    }

    @Test fun autoOffMismatchRetainsExactReadbackEvidence() = runBlocking {
        val records = mutableListOf<String>()
        VehicleTelemetryRegistry.diagnosticSink = { records += it }
        try {
            val port = FakeEcarxPort(reflectWrites = false).apply { ints[F.HVAC_FUNC_AUTO to 0] = 1 }
            val result = CoolrayOemCommandExecutor(port) {}.execute(VoiceCommand.HvacAuto(false))
            assertTrue(result is CommandExecutionResult.AcceptedUnverified)
            assertEquals(listOf(Triple(F.HVAC_FUNC_AUTO, 0, 0)), port.intWrites)
            assertTrue(records.joinToString("\n"), records.any { it.contains("requested=0 actual=1") && it.contains("MISMATCH") })
        } finally { VehicleTelemetryRegistry.diagnosticSink = null }
    }
}
