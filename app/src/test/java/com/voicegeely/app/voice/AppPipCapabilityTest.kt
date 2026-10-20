package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPipCapabilityTest {
    @Test fun systemFeatureMustExistBeforeTargetCanBeCandidate() {
        assertEquals(
            AppPipCapabilityState.SYSTEM_UNSUPPORTED,
            AppPipCapabilityClassifier.classify(
                systemDeclaresPip = false,
                appVisible = true
            )
        )
    }

    @Test fun invisibleTargetNeverBecomesCandidate() {
        assertEquals(
            AppPipCapabilityState.APP_NOT_VISIBLE,
            AppPipCapabilityClassifier.classify(
                systemDeclaresPip = true,
                appVisible = false
            )
        )
    }

    @Test fun visibleTargetStaysExplicitlyUnverified() {
        val capability = AppPipCapability(
            state = AppPipCapabilityClassifier.classify(
                systemDeclaresPip = true,
                appVisible = true
            ),
            packageName = "example.video",
            activityName = "example.video.MainActivity"
        )

        assertEquals(AppPipCapabilityState.APP_VISIBLE_UNVERIFIED, capability.state)
        assertTrue(capability.isVisibleCandidate)
        assertEquals("example.video", capability.packageName)
        assertEquals("example.video.MainActivity", capability.activityName)
    }

    @Test fun unsupportedOrInvisibleStatesAreNeverRuntimeCandidates() {
        assertFalse(
            AppPipCapability(AppPipCapabilityState.SYSTEM_UNSUPPORTED).isVisibleCandidate
        )
        assertFalse(
            AppPipCapability(AppPipCapabilityState.APP_NOT_VISIBLE).isVisibleCandidate
        )
    }
}
