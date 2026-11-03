package com.voicegeely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeyEventPolicyTest {
    @Test
    fun eachSupportedKeyStartsOnlyOnceThroughLongPressAndRelease() {
        for (code in listOf(84, 219, 231)) {
            val policy = VoiceKeyEventPolicy()
            var starts = 0
            fun send(action: Int, repeat: Int = 0): Boolean =
                policy.handle(code, action, repeat, false, 1) { starts++; true }

            assertTrue(send(0))
            assertTrue(send(0, 1))
            assertTrue(send(0, 20))
            assertTrue(send(1))
            assertEquals(1, starts)
            assertFalse(send(1))
            assertTrue(send(0))
            assertEquals(2, starts)
        }
    }

    @Test
    fun unrelatedKeysNeverLaunchOrConsume() {
        val policy = VoiceKeyEventPolicy()
        for (code in listOf(4, 24, 25, 66, 79, 85)) {
            for (action in listOf(0, 1, 2)) {
                assertFalse(policy.handle(code, action, 0, false, 1) {
                    error("Unrelated key must not invoke launcher")
                })
            }
        }
    }

    @Test
    fun decliningDownLetsProjectionReceiveTheEntireGesture() {
        val policy = VoiceKeyEventPolicy()
        var attempts = 0
        assertFalse(policy.handle(231, 0, 0, false, 1) { attempts++; false })
        assertFalse(policy.handle(231, 0, 1, false, 1) { error("repeat") })
        assertFalse(policy.handle(231, 1, 0, false, 1) { error("release") })
        assertEquals(1, attempts)
    }

    @Test
    fun orphanRepeatOrReleaseAndCanceledDownNeverStartListening() {
        val policy = VoiceKeyEventPolicy()
        fun send(action: Int, repeat: Int = 0, canceled: Boolean = false) =
            policy.handle(231, action, repeat, canceled, 1) { error("unexpected launch") }
        assertFalse(send(0, repeat = 1))
        assertFalse(send(1))
        assertFalse(send(2))
        assertFalse(send(0, canceled = true))
        assertFalse(send(1, canceled = true))
    }

    @Test
    fun canceledReleaseConsumesCapturedGestureWithoutAnotherLaunch() {
        val policy = VoiceKeyEventPolicy()
        assertTrue(policy.handle(231, 0, 0, false, 1) { true })
        assertTrue(policy.handle(231, 1, 0, true, 1) { error("release") })
        assertFalse(policy.handle(231, 1, 0, false, 1) { error("orphan release") })
    }

    @Test
    fun capturedGesturesAreSeparatedByDeviceAndKeyCode() {
        val policy = VoiceKeyEventPolicy()
        assertTrue(policy.handle(231, 0, 0, false, 1) { true })
        assertTrue(policy.handle(84, 0, 0, false, 1) { true })
        assertFalse(policy.handle(231, 1, 0, false, 2) { error("other device") })
        assertTrue(policy.handle(84, 1, 0, false, 1) { error("release") })
        assertTrue(policy.handle(231, 1, 0, false, 1) { error("release") })
    }

    @Test
    fun clearingDisconnectedHandlerDropsOldGestures() {
        val policy = VoiceKeyEventPolicy()
        assertTrue(policy.handle(231, 0, 0, false, 1) { true })
        policy.clear()
        assertFalse(policy.handle(231, 0, 1, false, 1) { error("repeat") })
        assertFalse(policy.handle(231, 1, 0, false, 1) { error("release") })
        assertTrue(policy.handle(231, 0, 0, false, 1) { true })
    }

    @Test
    fun simultaneousBroadcastAndAndroidKeyOpenOneSession() {
        val policy = VoiceKeyEventPolicy()
        val debounce = VoiceKeyDebounce()
        var sessions = 0
        // ECARX broadcast arrives before Android DOWN for the same physical press.
        if (debounce.accept(10_000)) sessions++
        assertTrue(policy.handle(231, 0, 0, false, 1) {
            if (debounce.accept(10_005)) sessions++
            true // Launcher consumes an already-handled press too.
        })
        assertTrue(policy.handle(231, 1, 0, false, 1) { error("release") })
        assertEquals(1, sessions)
    }
}
