package com.voicegeely.app.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner request 15/10/2026: volume by number → executor result and SHORT result wording. */
class MediaVolumeExecutorTest {
    private fun level(n: Int) = VoiceCommand.SetMediaVolume(n)

    @Test fun executorAndWording() = runBlocking {
        val calls = mutableListOf<String>()
        val backend = object : AndroidActionBackend {
            override fun openApp(packageCandidates: List<String>) = AndroidBackendResult(false, false)
            override fun openUri(uri: String, packageCandidates: List<String>) = AndroidBackendResult(false, false)
            override fun media(action: MediaAction) = AndroidBackendResult(false, false)
            override fun setMediaVolume(value: Int, unit: VolumeUnit): AndroidBackendResult {
                calls += "set $value $unit"
                return if (value > 39) AndroidBackendResult(false, false, detail = "Âm lượng chỉ từ 0 đến 39.")
                else AndroidBackendResult(true, true, actualValue = value.toString())
            }
            override fun adjustMediaVolume(steps: Int): AndroidBackendResult {
                calls += "adjust $steps"
                return AndroidBackendResult(true, false, detail = "Âm lượng đang ở mức 9.")
            }
            override fun querySceneCapability(scene: SceneType) = AndroidBackendResult(false, false)
            override fun queryWindowingCapability(mode: WindowingMode) = AndroidBackendResult(false, false)
            override fun queryWifiState() = AndroidBackendResult(false, false)
            override fun queryWifiIpAddress() = AndroidBackendResult(false, false)
            override fun queryWifiSsid() = AndroidBackendResult(false, false)
            override fun queryDisplayBrightness() = AndroidBackendResult(false, false)
            override fun setManualDisplayBrightness(percent: Int) = AndroidBackendResult(false, false)
        }
        val executor = AndroidVoiceCommandExecutor(backend, MediaPreferenceResolver { com.voicegeely.app.settings.DefaultMediaApp.AUTO })
        val ok = executor.execute(level(15))
        assertTrue(ok is CommandExecutionResult.Confirmed)
        assertEquals("Đã chỉnh âm lượng mức 15.", VoiceResponseFormatter.result(level(15), ok))
        val rejected = executor.execute(level(50))
        assertTrue(rejected is CommandExecutionResult.Rejected)
        assertFalse(VoiceResponseFormatter.result(level(50), rejected).startsWith("Đã"))
        val unverified = executor.execute(VoiceCommand.AdjustMediaVolume(2))
        assertTrue(unverified is CommandExecutionResult.AcceptedUnverified)
        assertFalse(VoiceResponseFormatter.result(VoiceCommand.AdjustMediaVolume(2), unverified).startsWith("Đã tăng"))
        assertEquals(listOf("set 15 LEVEL", "set 50 LEVEL", "adjust 2"), calls)
    }
}
