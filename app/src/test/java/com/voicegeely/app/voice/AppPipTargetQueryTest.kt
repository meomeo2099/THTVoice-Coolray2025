package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPipTargetQueryTest {
    @Test fun parserAcceptsOnlyExplicitReadOnlyTargetQuestions() {
        assertEquals(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, AppTarget.YOUTUBE),
            AppPipTargetQueryParser.parse("YouTube có hỗ trợ PiP không")?.command
        )
        assertEquals(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, AppTarget.REVANCED),
            AppPipTargetQueryParser.parse("kiểm tra PiP ReVanced")?.command
        )
        assertEquals(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, AppTarget.VIETMAP),
            AppPipTargetQueryParser.parse("VietMap Live dùng PiP được không")?.command
        )
        assertEquals(null, AppPipTargetQueryParser.parse("mở YouTube bằng PiP"))
        assertEquals(null, AppPipTargetQueryParser.parse("bật PiP YouTube"))
        assertEquals(null, AppPipTargetQueryParser.parse("ứng dụng lạ có hỗ trợ PiP không"))
    }

    @Test fun targetQueryUsesInspectorWithoutLaunchingAnything() = runBlocking {
        val backend = FakeBackend()
        var inspectedPackages: List<String> = emptyList()
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            appPipInspector = { packages ->
                inspectedPackages = packages
                AppPipCapability(
                    state = AppPipCapabilityState.APP_VISIBLE_UNVERIFIED,
                    packageName = packages.firstOrNull(),
                    activityName = "example.MainActivity"
                )
            }
        )

        val result = executor.execute(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, AppTarget.YOUTUBE)
        ) as CommandExecutionResult.Confirmed

        assertEquals(AndroidAppCatalog.packagesFor(AppTarget.YOUTUBE), inspectedPackages)
        assertTrue(result.actualValue.orEmpty().contains("chưa được xác minh"))
        assertEquals(0, backend.openAppCalls)
        assertEquals(0, backend.openUriCalls)
        assertEquals(0, backend.genericWindowingCalls)
    }

    @Test fun genericWindowingQueryKeepsExistingBackendPath() = runBlocking {
        val backend = FakeBackend()
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            appPipInspector = { error("Target inspector must not run for generic query") }
        )

        val result = executor.execute(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP)
        ) as CommandExecutionResult.Confirmed

        assertEquals("generic PIP", result.actualValue)
        assertEquals(1, backend.genericWindowingCalls)
        assertEquals(0, backend.openAppCalls)
    }

    @Test fun missingInspectorFailsClosedInsteadOfFallingBackToLaunch() = runBlocking {
        val backend = FakeBackend()
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO }
        )

        val result = executor.execute(
            VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, AppTarget.NETFLIX)
        )

        assertTrue(result is CommandExecutionResult.Rejected)
        assertEquals(0, backend.openAppCalls)
        assertEquals(0, backend.openUriCalls)
        assertEquals(0, backend.genericWindowingCalls)
    }

    private class FakeBackend : AndroidActionBackend {
        var openAppCalls = 0
        var openUriCalls = 0
        var genericWindowingCalls = 0

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
        override fun queryWindowingCapability(mode: WindowingMode): AndroidBackendResult {
            genericWindowingCalls++
            return AndroidBackendResult(true, true, actualValue = "generic ${mode.name}")
        }
        override fun queryWifiState() = AndroidBackendResult(false, false)
        override fun queryWifiIpAddress() = AndroidBackendResult(false, false)
        override fun queryWifiSsid() = AndroidBackendResult(false, false)
        override fun queryDisplayBrightness() = AndroidBackendResult(false, false)
        override fun setManualDisplayBrightness(percent: Int) = AndroidBackendResult(false, false)
    }
}
