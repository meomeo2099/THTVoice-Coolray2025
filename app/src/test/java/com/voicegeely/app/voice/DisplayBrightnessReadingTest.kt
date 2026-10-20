package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayBrightnessReadingTest {
    @Test fun percentEncodingStaysWithinSystemRange() {
        assertEquals(3, DisplayBrightnessReading.levelForPercent(1))
        assertEquals(128, DisplayBrightnessReading.levelForPercent(50))
        assertEquals(255, DisplayBrightnessReading.levelForPercent(100))
        assertNull(DisplayBrightnessReading.levelForPercent(0))
        assertNull(DisplayBrightnessReading.levelForPercent(101))
    }

    @Test fun autoModeNeverReportsStaleManualPercent() {
        assertEquals("Thiết lập độ sáng hệ thống đang ở chế độ tự động",
            DisplayBrightnessReading.describe(DisplayBrightnessReading.MODE_AUTOMATIC, 255))
    }

    @Test fun manualSettingIsReportedAsApproximatePercent() {
        assertEquals("Mức đặt độ sáng hệ thống là 50 phần trăm",
            DisplayBrightnessReading.describe(DisplayBrightnessReading.MODE_MANUAL, 128))
    }

    @Test fun invalidSettingDoesNotBecomeAConfirmedRead() {
        assertNull(DisplayBrightnessReading.describe(DisplayBrightnessReading.MODE_MANUAL, null))
        assertNull(DisplayBrightnessReading.describe(DisplayBrightnessReading.MODE_MANUAL, 300))
        assertNull(DisplayBrightnessReading.describe(7, 128))
    }

    @Test fun autoAndManualModeValuesRoundTripExactly() {
        assertEquals(DisplayBrightnessReading.MODE_MANUAL,
            DisplayBrightnessReading.settingValue(DisplayBrightnessReading.Mode.MANUAL))
        assertEquals(DisplayBrightnessReading.MODE_AUTOMATIC,
            DisplayBrightnessReading.settingValue(DisplayBrightnessReading.Mode.AUTOMATIC))
        assertEquals(DisplayBrightnessReading.Mode.MANUAL,
            DisplayBrightnessReading.modeFromSetting(DisplayBrightnessReading.MODE_MANUAL))
        assertEquals(DisplayBrightnessReading.Mode.AUTOMATIC,
            DisplayBrightnessReading.modeFromSetting(DisplayBrightnessReading.MODE_AUTOMATIC))
        assertNull(DisplayBrightnessReading.modeFromSetting(7))
    }

    @Test fun modeReadBackMustMatchRequestedModeExactly() {
        assertTrue(DisplayBrightnessReading.modeMatches(
            DisplayBrightnessReading.Mode.MANUAL,
            DisplayBrightnessReading.MODE_MANUAL))
        assertTrue(DisplayBrightnessReading.modeMatches(
            DisplayBrightnessReading.Mode.AUTOMATIC,
            DisplayBrightnessReading.MODE_AUTOMATIC))
        assertFalse(DisplayBrightnessReading.modeMatches(
            DisplayBrightnessReading.Mode.MANUAL,
            DisplayBrightnessReading.MODE_AUTOMATIC))
        assertFalse(DisplayBrightnessReading.modeMatches(
            DisplayBrightnessReading.Mode.AUTOMATIC,
            7))
    }
}
