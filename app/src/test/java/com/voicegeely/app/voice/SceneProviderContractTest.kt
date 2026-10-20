package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneProviderContractTest {
    @Test fun providerMissingStaysUnavailable() {
        val snapshot = SceneProviderSnapshot(providerVisible = false, querySucceeded = false)
        assertEquals(
            SceneProviderContractClassifier.State.PROVIDER_NOT_VISIBLE,
            SceneProviderContractClassifier.classify(snapshot)
        )
        assertFalse(snapshot.hasReadableSceneMode)
        assertNull(snapshot.sceneModeValueOrNull())
    }

    @Test fun permissionFailureNeverLooksLikeReadableState() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = false,
            detail = "permission denied"
        )
        assertEquals(
            SceneProviderContractClassifier.State.QUERY_DENIED,
            SceneProviderContractClassifier.classify(snapshot)
        )
        assertFalse(snapshot.hasReadableSceneMode)
    }

    @Test fun unknownSchemaDoesNotInventSceneMode() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = true,
            columns = listOf("id", "state"),
            firstRow = mapOf("id" to "1", "state" to "active")
        )
        assertEquals(
            SceneProviderContractClassifier.State.SCHEMA_UNKNOWN,
            SceneProviderContractClassifier.classify(snapshot)
        )
        assertNull(snapshot.sceneModeValueOrNull())
    }

    @Test fun sceneModeColumnIsObservedButNotMappedToRestOrCamping() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = true,
            columns = listOf("scene_mode", "scene_changed"),
            firstRow = mapOf("scene_mode" to "2", "scene_changed" to "1")
        )
        assertEquals(
            SceneProviderContractClassifier.State.MODE_READABLE,
            SceneProviderContractClassifier.classify(snapshot)
        )
        assertTrue(snapshot.hasReadableSceneMode)
        assertEquals("2", snapshot.sceneModeValueOrNull())
        assertTrue(SceneProviderContractClassifier.diagnostic(snapshot).contains("chưa ánh xạ"))
    }

    @Test fun sceneModeLookupIsCaseInsensitive() {
        val snapshot = SceneProviderSnapshot(
            providerVisible = true,
            querySucceeded = true,
            columns = listOf("SCENE_MODE"),
            firstRow = mapOf("SCENE_MODE" to "rest")
        )
        assertEquals("rest", snapshot.sceneModeValueOrNull())
        assertEquals(
            SceneProviderContractClassifier.State.MODE_READABLE,
            SceneProviderContractClassifier.classify(snapshot)
        )
    }
}
