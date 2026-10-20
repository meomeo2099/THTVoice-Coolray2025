package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCapabilityQueryResolverTest {
    private val component = AndroidBackendResult(
        accepted = true,
        confirmed = true,
        verificationSource = VerificationSource.ANDROID_STATE,
        actualValue = "Có lối mở Android cho chế độ nghỉ; trạng thái scene và khả năng điều khiển chưa được xác minh"
    )

    @Test fun readableModeIsReportedAsEvidenceWithoutSemanticMapping() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = true,
            columns = listOf("scene_mode", "scene_changed"),
            firstRow = mapOf("scene_mode" to "7", "scene_changed" to "1")
        )
        val result = SceneCapabilityQueryResolver.resolve(component, snapshot)
        assertTrue(result.confirmed)
        assertTrue(result.actualValue!!.contains("scene_mode đọc được"))
        assertTrue(result.actualValue!!.contains("chưa ánh xạ"))
        assertFalse(result.actualValue!!.contains("scene_mode=7"))
    }

    @Test fun unknownSchemaNeverClaimsSceneState() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = true,
            columns = listOf("mode"),
            firstRow = mapOf("mode" to "1")
        )
        val result = SceneCapabilityQueryResolver.resolve(component, snapshot)
        assertTrue(result.actualValue!!.contains("schema chưa có scene_mode"))
        assertFalse(result.actualValue!!.contains("đang bật"))
    }

    @Test fun permissionFailureStaysDiagnosticOnly() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = false,
            detail = "Android không cho phép đọc scene provider."
        )
        val result = SceneCapabilityQueryResolver.resolve(component, snapshot)
        assertTrue(result.confirmed)
        assertTrue(result.actualValue!!.contains("không cho phép đọc"))
    }

    @Test fun unconfirmedComponentIsNeverPromotedByProviderProbe() {
        val unconfirmed = AndroidBackendResult(
            accepted = true,
            confirmed = false,
            detail = "Component chưa xác minh"
        )
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = true,
            columns = listOf("scene_mode"),
            firstRow = mapOf("scene_mode" to "1")
        )
        assertEquals(unconfirmed, SceneCapabilityQueryResolver.resolve(unconfirmed, snapshot))
    }

    @Test fun missingRuntimeProbePreservesExistingCapabilityObservation() {
        assertEquals(component, SceneCapabilityQueryResolver.resolve(component, null))
    }
}
