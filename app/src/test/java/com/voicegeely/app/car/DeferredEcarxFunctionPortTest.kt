package com.voicegeely.app.car

import com.voicegeely.app.voice.FakeEcarxPort
import org.junit.Assert.*
import org.junit.Test

class DeferredEcarxFunctionPortTest {
    @Test fun retriesBodyInitializationWithoutReconnectingHealthyVhal() {
        val backend = FakeEcarxPort().apply { mcuAccepts = true }
        var now = 0L
        var attempts = 0
        val port = DeferredEcarxFunctionPort(
            connect = { attempts++; if (attempts == 1) null else backend },
            clock = { now }
        )
        val function = Coolray2025Functions.BCM_FUNC_WINDOW_POS
        val zone = Coolray2025Functions.WINDOW_ROW_1_LEFT
        assertNull(port.getFloat(function, zone))
        assertFalse(port.setFloat(function, zone, 25f))
        assertEquals(1, attempts)

        now = 5_000L
        assertTrue(port.setFloat(function, zone, 25f))
        assertEquals(25f, port.getFloat(function, zone))
        assertEquals(2, attempts)
        val payload = Coolray2025Functions.windowGroupPayload(Coolray2025Functions.GROUP_FR, 50)
        assertTrue(port.sendBytesToMcu(Coolray2025Functions.INFO_ID_WINDOW_GROUP, payload))
        assertEquals(50, port.getIntByProp(Coolray2025Functions.INFO_ID_WINDOW_FR, function))
        assertEquals(2, attempts)
    }
}
