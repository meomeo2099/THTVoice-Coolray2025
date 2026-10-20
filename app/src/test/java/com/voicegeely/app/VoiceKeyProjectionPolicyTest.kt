package com.voicegeely.app

import com.voicegeely.app.VoiceKeyProjectionPolicy.UsbId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeyProjectionPolicyTest {
    private val iphone = UsbId(0x05AC, 0x12A8)
    private val androidAuto = UsbId(0x18D1, 0x2D01)
    private val usbStick = UsbId(0x0781, 0x5567)
    private val pixelNormalMode = UsbId(0x18D1, 0x4EE7)

    @Test fun alwaysThtNeverYields() {
        assertFalse(VoiceKeyProjectionPolicy.yieldToProjection(true, listOf(iphone)))
        assertFalse(VoiceKeyProjectionPolicy.yieldToProjection(true, listOf(androidAuto)))
    }

    @Test fun offYieldsOnlyWhilePhoneProjected() {
        assertTrue(VoiceKeyProjectionPolicy.yieldToProjection(false, listOf(iphone)))
        assertTrue(VoiceKeyProjectionPolicy.yieldToProjection(false, listOf(usbStick, androidAuto)))
    }

    @Test fun offWithNothingPluggedKeepsTht() {
        assertFalse(VoiceKeyProjectionPolicy.yieldToProjection(false, emptyList()))
        assertFalse(VoiceKeyProjectionPolicy.yieldToProjection(false, listOf(usbStick)))
        assertFalse(VoiceKeyProjectionPolicy.yieldToProjection(false, listOf(pixelNormalMode)))
    }
}
