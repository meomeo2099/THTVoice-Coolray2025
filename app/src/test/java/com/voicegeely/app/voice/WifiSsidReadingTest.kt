package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiSsidReadingTest {
    @Test fun exposesNormalQuotedSsid() {
        assertEquals("Xe nha", WifiSsidReading.visibleName("\"Xe nha\""))
    }

    @Test fun refusesRedactedOrUnsafeValues() {
        assertNull(WifiSsidReading.visibleName("<unknown ssid>"))
        assertNull(WifiSsidReading.visibleName("   "))
        assertNull(WifiSsidReading.visibleName("bad\nname"))
        assertNull(WifiSsidReading.visibleName("x".repeat(49)))
        assertNull(WifiSsidReading.visibleName(null))
    }
}
