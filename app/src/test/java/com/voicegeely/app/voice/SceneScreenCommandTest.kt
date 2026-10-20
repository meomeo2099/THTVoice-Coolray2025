package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneScreenCommandTest {
    @Test
    fun restPhrasesFromReferenceDataset() {
        listOf(
            "chế độ nghỉ ngơi", "nghỉ ngơi", "bật chế độ nghỉ ngơi", "chế độ thư giãn", "nghỉ ngơi trong xe",
            "chế độ nghỉ trưa", "nghỉ trưa", "ngủ trưa", "chợp mắt", "bật chế độ nghỉ trưa", "chế độ nap"
        ).forEach { text ->
            assertEquals(text, VoiceCommand.OpenSceneScreen(SceneType.REST), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun campingPhrasesFromReferenceDataset() {
        listOf("chế độ cắm trại", "cắm trại", "bật chế độ cắm trại", "đi cắm trại").forEach { text ->
            assertEquals(text, VoiceCommand.OpenSceneScreen(SceneType.CAMPING), VoiceCommandParser.parse(text)?.command)
        }
    }

    @Test
    fun capabilityQuestionsStayReadOnly() {
        assertEquals(VoiceCommand.QuerySceneCapability(SceneType.REST), VoiceCommandParser.parse("kiểm tra chế độ nghỉ")?.command)
        assertEquals(VoiceCommand.QuerySceneCapability(SceneType.CAMPING), VoiceCommandParser.parse("xe có chế độ cắm trại không")?.command)
    }

    @Test
    fun openingSceneScreenIsParkedOnly() {
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(VoiceCommand.OpenSceneScreen(SceneType.REST)))
    }

    @Test
    fun executorReportsOnlyThatTheScreenOpened() = runBlocking {
        val opened = mutableListOf<SceneType>()
        val result = executor { scene ->
            opened += scene
            AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, "Đã mở màn hình chế độ cắm trại")
        }.execute(VoiceCommand.OpenSceneScreen(SceneType.CAMPING))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(SceneType.CAMPING), opened)
    }

    @Test
    fun missingPermissionIsRejected() = runBlocking {
        val result = executor { AndroidBackendResult(false, false, detail = "Ứng dụng chưa có quyền mở màn hình chế độ nghỉ.") }
            .execute(VoiceCommand.OpenSceneScreen(SceneType.REST))
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test
    fun launcherExceptionIsFailed() = runBlocking {
        val result = executor { error("boom") }.execute(VoiceCommand.OpenSceneScreen(SceneType.REST))
        assertTrue(result is CommandExecutionResult.Failed)
    }

    @Test
    fun responseDoesNotClaimSceneActivated() {
        val text = VoiceResponseFormatter.result(
            VoiceCommand.OpenSceneScreen(SceneType.REST),
            CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE, "Đã mở màn hình chế độ nghỉ")
        )
        assertTrue(text, text.contains("màn hình"))
    }

    private fun executor(launcher: (SceneType) -> AndroidBackendResult) = AndroidVoiceCommandExecutor(
        backend = NoopBackend,
        mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
        navigationResolver = NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
        sceneScreenLauncher = launcher
    )

    private object NoopBackend : AndroidActionBackend {
        override fun openApp(packageCandidates: List<String>) = AndroidBackendResult(false, false)
        override fun openUri(uri: String, packageCandidates: List<String>) = AndroidBackendResult(false, false)
        override fun media(action: MediaAction) = AndroidBackendResult(false, false)
        override fun querySceneCapability(scene: SceneType) = AndroidBackendResult(false, false)
        override fun queryWindowingCapability(mode: WindowingMode) = AndroidBackendResult(false, false)
        override fun queryWifiState() = AndroidBackendResult(false, false)
        override fun queryWifiIpAddress() = AndroidBackendResult(false, false)
        override fun queryWifiSsid() = AndroidBackendResult(false, false)
        override fun queryDisplayBrightness() = AndroidBackendResult(false, false)
        override fun setManualDisplayBrightness(percent: Int) = AndroidBackendResult(false, false)
    }
}
