package com.voicegeely.app.security

import com.voicegeely.app.voice.safety.VehicleMotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerNoticePolicyTest {
    private val parked = VehicleMotionState(0f, VehicleMotionState.GEAR_PARK)
    private val moving = VehicleMotionState(42f, 1)
    private val stoppedInDrive = VehicleMotionState(0f, 1)
    private val unknown = VehicleMotionState(null, null)
    private val notice = CustomerNotice("1700", "Bản 1.0.6 đã có", 0L)

    @Test
    fun showsOnceWhenParkedOrWhenTelemetryIsMissing() {
        assertTrue(CustomerNoticePolicy.shouldShow(notice, null, 1L, parked))
        assertTrue(CustomerNoticePolicy.shouldShow(notice, "older", 1L, unknown))
        assertFalse(CustomerNoticePolicy.shouldShow(notice, "1700", 1L, parked))
    }

    @Test
    fun neverWhileMovingOrOutOfPark() {
        assertFalse(CustomerNoticePolicy.shouldShow(notice, null, 1L, moving))
        assertFalse(CustomerNoticePolicy.shouldShow(notice, null, 1L, stoppedInDrive))
    }

    @Test
    fun expiredOrEmptyIsIgnored() {
        val until = CustomerNotice("1", "x", 1000L)
        assertTrue(CustomerNoticePolicy.shouldShow(until, null, 999L, parked))
        assertFalse(CustomerNoticePolicy.shouldShow(until, null, 1001L, parked))
        assertNull(CustomerNoticePolicy.of("", "text", 0L))
        assertNull(CustomerNoticePolicy.of("1", "   ", 0L))
        assertEquals(500, CustomerNoticePolicy.of("1", "a".repeat(900), -5L)!!.text.length)
        assertEquals(0L, CustomerNoticePolicy.of("1", "a", -5L)!!.until)
    }
}
