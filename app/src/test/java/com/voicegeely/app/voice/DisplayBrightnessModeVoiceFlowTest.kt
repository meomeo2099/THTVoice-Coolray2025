package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayBrightnessModeVoiceFlowTest {
    @Test fun strictParserAcceptsOnlyExplicitBrightnessModePhrases() {
        val automatic = VoiceCommand.SetManualDisplayBrightness(0, DisplayBrightnessMode.AUTOMATIC)
        val manual = VoiceCommand.SetManualDisplayBrightness(0, DisplayBrightnessMode.MANUAL)

        assertEquals(automatic, DisplayBrightnessModeVoiceParser.parse("bật độ sáng tự động")?.command)
        assertEquals(automatic, DisplayBrightnessModeVoiceParser.parse("chuyển màn hình sang chế độ tự động")?.command)
        assertEquals(manual, DisplayBrightnessModeVoiceParser.parse("tắt độ sáng tự động")?.command)
        assertEquals(manual, DisplayBrightnessModeVoiceParser.parse("đổi độ sáng qua thủ công")?.command)

        // Integration path used by VoiceAssistantService before the generic parser.
        assertEquals(automatic, VehicleStatusQueryParser.parse("đặt độ sáng ở chế độ tự động")?.command)

        // Vehicle theme and ambiguous display phrases must stay out of this parser.
        assertNull(DisplayBrightnessModeVoiceParser.parse("giao diện tự động"))
        assertNull(DisplayBrightnessModeVoiceParser.parse("màn hình tối"))
        assertNull(DisplayBrightnessModeVoiceParser.parse("tự động"))
        assertNull(DisplayBrightnessModeVoiceParser.parse("độ sáng tự động không"))
    }

    @Test fun automaticModeUsesDedicatedSetterAndNeverWritesManualLevel() = runBlocking {
        val backend = FakeBackend()
        var requested: DisplayBrightnessMode? = null
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            displayBrightnessModeSetter = { mode ->
                requested = mode
                AndroidBackendResult(
                    accepted = true,
                    confirmed = true,
                    actualValue = "chế độ tự động"
                )
            }
        )
        val command = VoiceCommand.SetManualDisplayBrightness(0, DisplayBrightnessMode.AUTOMATIC)
        val result = executor.execute(command)

        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(DisplayBrightnessMode.AUTOMATIC, requested)
        assertTrue(backend.brightnessWrites.isEmpty())
        assertEquals(
            "Đang chuyển độ sáng hệ thống sang chế độ tự động.",
            VoiceResponseFormatter.acknowledgement(command)
        )
        assertEquals(
            "Đã chuyển độ sáng hệ thống sang chế độ tự động.",
            VoiceResponseFormatter.result(command, result)
        )
    }

    @Test fun missingModeSetterFailsClosed() = runBlocking {
        val backend = FakeBackend()
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO }
        )
        val result = executor.execute(
            VoiceCommand.SetManualDisplayBrightness(0, DisplayBrightnessMode.MANUAL)
        )

        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(backend.brightnessWrites.isEmpty())
    }

    @Test fun staleModeReadbackNeverSpeaksConfirmed() = runBlocking {
        val backend = FakeBackend()
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            displayBrightnessModeSetter = {
                AndroidBackendResult(
                    accepted = true,
                    confirmed = false,
                    detail = "Chế độ độ sáng đọc lại chưa khớp yêu cầu."
                )
            }
        )
        val command = VoiceCommand.SetManualDisplayBrightness(0, DisplayBrightnessMode.AUTOMATIC)
        val result = executor.execute(command)

        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
        val spoken = VoiceResponseFormatter.result(command, result)
        assertTrue(spoken.contains("chưa xác nhận"))
        assertFalse(spoken.startsWith("Đã chuyển"))
    }

    @Test fun modeChangeKeepsExistingParkedOnlySafetyRequirement() {
        val command = VoiceCommand.SetManualDisplayBrightness(0, DisplayBrightnessMode.AUTOMATIC)
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(command))
    }

    @Test fun legacyDiagnosticsRecordModeInsteadOfFakeZeroPercent() {
        val parsed = requireNotNull(DisplayBrightnessModeVoiceParser.parse("bật độ sáng tự động"))
        val payload = LegacyPredictionBridge.toPayload(parsed)

        assertEquals("display_brightness_mode", payload.intent)
        assertEquals("automatic", payload.entities["mode"])
        assertFalse(payload.entities.containsKey("percent"))
    }

    private class FakeBackend : AndroidActionBackend {
        val brightnessWrites = mutableListOf<Int>()

        override fun openApp(packageCandidates: List<String>) = AndroidBackendResult(false, false)
        override fun openUri(uri: String, packageCandidates: List<String>) = AndroidBackendResult(false, false)
        override fun media(action: MediaAction) = AndroidBackendResult(false, false)
        override fun querySceneCapability(scene: SceneType) = AndroidBackendResult(false, false)
        override fun queryWindowingCapability(mode: WindowingMode) = AndroidBackendResult(false, false)
        override fun queryWifiState() = AndroidBackendResult(false, false)
        override fun queryWifiIpAddress() = AndroidBackendResult(false, false)
        override fun queryWifiSsid() = AndroidBackendResult(false, false)
        override fun queryDisplayBrightness() = AndroidBackendResult(false, false)
        override fun setManualDisplayBrightness(percent: Int): AndroidBackendResult {
            brightnessWrites += percent
            return AndroidBackendResult(true, true, actualValue = "$percent phần trăm")
        }
    }
}
