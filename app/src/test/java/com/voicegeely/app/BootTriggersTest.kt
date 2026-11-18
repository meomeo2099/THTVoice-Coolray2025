package com.voicegeely.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wake word must come back after the head unit sleeps, not only after a full reboot. */
class BootTriggersTest {
    @Test
    fun headUnitWakeAndUnlockStartTheService() {
        listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.USER_UNLOCKED",
            "android.intent.action.ACTION_BOOT_HU",
            "ecarx.intent.action.bt.boot.completed",
        ).forEach { assertTrue(it, BootTriggers.startsService(it)) }
    }

    @Test
    fun otherBroadcastsDoNot() {
        assertFalse(BootTriggers.startsService(null))
        assertFalse(BootTriggers.startsService("android.intent.action.SCREEN_OFF"))
        assertFalse(BootTriggers.startsService("android.intent.action.LOCKED_BOOT_COMPLETED"))
    }

    @Test
    fun burstAtStartUpStartsOnce() {
        assertTrue(BootTriggers.accept(nowMs = 10_000, lastMs = 0))
        assertFalse(BootTriggers.accept(nowMs = 10_800, lastMs = 10_000))
        assertTrue(BootTriggers.accept(nowMs = 11_600, lastMs = 10_000))
    }
}
