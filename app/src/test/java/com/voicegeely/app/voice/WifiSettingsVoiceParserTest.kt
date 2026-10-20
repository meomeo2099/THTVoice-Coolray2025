package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSettingsVoiceParserTest {
    @Test fun explicitSettingsPhrasesOpenSettingsOnly() {
        listOf(
            "mở cài đặt wifi",
            "vào cài đặt Wi-Fi",
            "hiện bảng wifi",
            "mở màn hình cài đặt wifi"
        ).forEach { text ->
            assertEquals(VoiceCommand.OpenWifiSettings, WifiSettingsVoiceParser.parse(text)?.command)
            assertEquals(VoiceCommand.OpenWifiSettings, MediaVoiceParser.parse(text)?.command)
        }
    }

    @Test fun togglePhrasesRemainUnsupported() {
        listOf(
            "bật wifi",
            "tắt wifi",
            "mở wifi",
            "đóng wifi",
            "bật mạng wifi"
        ).forEach { text ->
            assertNull(WifiSettingsVoiceParser.parse(text))
            assertNull(MediaVoiceParser.parse(text))
        }
    }

    @Test fun executorUsesSettingsLauncherAndNeverCallsAppLaunch() = runBlocking {
        val backend = FakeBackend()
        var launcherCalls = 0
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            wifiSettingsLauncher = {
                launcherCalls++
                AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    verificationSource = VerificationSource.ANDROID_STATE,
                    actualValue = "Đã mở cài đặt Wi-Fi"
                )
            }
        )

        val result = executor.execute(VoiceCommand.OpenWifiSettings) as CommandExecutionResult.Confirmed
        assertEquals(1, launcherCalls)
        assertEquals(0, backend.openAppCalls)
        assertEquals(0, backend.openUriCalls)
        assertEquals("Đã mở cài đặt Wi-Fi", result.actualValue)
        assertEquals(VerificationSource.ANDROID_STATE, result.verificationSource)
    }

    @Test fun launcherFailureDoesNotPretendWifiStateChanged() = runBlocking {
        val executor = AndroidVoiceCommandExecutor(
            backend = FakeBackend(),
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            wifiSettingsLauncher = {
                AndroidBackendResult(false, false, detail = "Không mở được cài đặt Wi-Fi.")
            }
        )
        val result = executor.execute(VoiceCommand.OpenWifiSettings)
        assertTrue(result is CommandExecutionResult.Rejected)
        val spoken = VoiceResponseFormatter.result(VoiceCommand.OpenWifiSettings, result)
        assertTrue(spoken.contains("Không thể thực hiện"))
        assertTrue(!spoken.contains("Đã bật Wi-Fi"))
    }

    private class FakeBackend : AndroidActionBackend {
        var openAppCalls = 0
        var openUriCalls = 0

        override fun openApp(packageCandidates: List<String>): AndroidBackendResult {
            openAppCalls++
            return AndroidBackendResult(false, false)
        }
        override fun openUri(uri: String, packageCandidates: List<String>): AndroidBackendResult {
            openUriCalls++
            return AndroidBackendResult(false, false)
        }
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
