package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultNavigationApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneCapabilityVoiceFlowTest {
    @Test fun sceneQueryUsesReadOnlyProviderProbeWhenAvailable() = runBlocking {
        var probeCalls = 0
        val executor = AndroidVoiceCommandExecutor(
            backend = FakeBackend(),
            mediaResolver = MediaPreferenceResolver { com.voicegeely.app.settings.DefaultMediaApp.AUTO },
            navigationResolver = NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
            sceneProviderProbe = {
                probeCalls++
                SceneProviderSnapshot(
                    providerVisible = true,
                    querySucceeded = true,
                    columns = listOf("scene_mode"),
                    firstRow = mapOf("scene_mode" to "9")
                )
            }
        )

        val result = executor.execute(VoiceCommand.QuerySceneCapability(SceneType.REST))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(1, probeCalls)
        val actual = (result as CommandExecutionResult.Confirmed).actualValue.orEmpty()
        assertTrue(actual.contains("scene_mode đọc được"))
        assertTrue(!actual.contains("scene_mode=9"))
    }

    @Test fun rejectedComponentProbeDoesNotTouchProvider() = runBlocking {
        var probeCalls = 0
        val executor = AndroidVoiceCommandExecutor(
            backend = FakeBackend(sceneAccepted = false),
            mediaResolver = MediaPreferenceResolver { com.voicegeely.app.settings.DefaultMediaApp.AUTO },
            navigationResolver = NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
            sceneProviderProbe = {
                probeCalls++
                SceneProviderSnapshot(true, true)
            }
        )

        val result = executor.execute(VoiceCommand.QuerySceneCapability(SceneType.CAMPING))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertEquals(0, probeCalls)
    }

    private class FakeBackend(
        private val sceneAccepted: Boolean = true
    ) : AndroidActionBackend {
        override fun openApp(packageCandidates: List<String>) = AndroidBackendResult(false, false)
        override fun openUri(uri: String, packageCandidates: List<String>) = AndroidBackendResult(false, false)
        override fun media(action: MediaAction) = AndroidBackendResult(false, false)
        override fun querySceneCapability(scene: SceneType) = if (sceneAccepted) {
            AndroidBackendResult(
                accepted = true,
                confirmed = true,
                verificationSource = VerificationSource.ANDROID_STATE,
                actualValue = "Có lối mở Android cho chế độ ${if (scene == SceneType.REST) "nghỉ" else "cắm trại"}; trạng thái scene và khả năng điều khiển chưa được xác minh"
            )
        } else {
            AndroidBackendResult(false, false, detail = "Scene component không khả dụng")
        }
        override fun queryWindowingCapability(mode: WindowingMode) = AndroidBackendResult(false, false)
        override fun queryWifiState() = AndroidBackendResult(false, false)
        override fun queryWifiIpAddress() = AndroidBackendResult(false, false)
        override fun queryWifiSsid() = AndroidBackendResult(false, false)
        override fun queryDisplayBrightness() = AndroidBackendResult(false, false)
        override fun setManualDisplayBrightness(percent: Int) = AndroidBackendResult(false, false)
    }
}
