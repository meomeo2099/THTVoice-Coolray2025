package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.voice.InstalledAppMatcher.InstalledApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenInstalledAppTest {
    private val installed = listOf(
        InstalledApp("Zalo", "com.zing.zalo"),
        InstalledApp("FPT Play", "com.fptplay.activity"),
        InstalledApp("VTV Go", "vn.vtv.vtvgo"),
        InstalledApp("TV360", "com.viettel.tv360"),
        InstalledApp("Navitel", "com.navitel"),
        InstalledApp("MX Player", "com.mxtech.videoplayer.ad"),
        InstalledApp("NhacCuaTui", "ht.nhaccuatui"),
        InstalledApp("AuresEyes", "com.bilang042.aureseyes"),
        InstalledApp("ES File Explorer", "com.estrongs.android.pop")
    )

    @Test
    fun catalogAppsStillUseTypedTargets() {
        assertEquals(VoiceCommand.OpenApp(AppTarget.WAZE), VoiceParseChain.parse("mở quê dơ")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.VIETMAP), VoiceParseChain.parse("mở việt mắp")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.SPOTIFY), VoiceParseChain.parse("mở sờ ti phai")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.TIKTOK), VoiceParseChain.parse("xem tiktok")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.WAZE), VoiceParseChain.parse("vào waze")?.command)
    }

    @Test
    fun unknownAppNamesBecomeInstalledAppLookups() {
        assertEquals(VoiceCommand.OpenInstalledApp("zalo"), VoiceParseChain.parse("mở Zalo")?.command)
        assertEquals(VoiceCommand.OpenInstalledApp("fpt play"), VoiceParseChain.parse("bật fpt play")?.command)
        assertEquals(VoiceCommand.OpenInstalledApp("gia lô"), VoiceParseChain.parse("mở gia lô")?.command)
    }

    @Test
    fun vehicleLeftoversAreNotTreatedAsAppNames() {
        listOf("mở cửa bên tài", "bật đèn", "mở chế độ gì đó", "bật ghế massage").forEach { text ->
            assertTrue(text, VoiceParseChain.parse(text)?.command !is VoiceCommand.OpenInstalledApp)
        }
        assertNull(VoiceParseChain.parse("xem cái gì đó"))
    }

    @Test
    fun matcherUsesLabelsAndAsrAliases() {
        assertEquals(listOf("com.zing.zalo"), InstalledAppMatcher.match("zalo", installed))
        assertEquals(listOf("com.zing.zalo"), InstalledAppMatcher.match("da lô", installed))
        assertEquals(listOf("com.zing.zalo"), InstalledAppMatcher.match("tin nhắn gia lô", installed))
        assertEquals(listOf("com.fptplay.activity"), InstalledAppMatcher.match("ép phê tê", installed))
        assertEquals(listOf("vn.vtv.vtvgo"), InstalledAppMatcher.match("vê tê vê gô", installed))
        assertEquals(listOf("com.viettel.tv360"), InstalledAppMatcher.match("tê vê 360", installed))
        assertEquals(listOf("com.navitel"), InstalledAppMatcher.match("na vi ten", installed))
        assertEquals(listOf("ht.nhaccuatui"), InstalledAppMatcher.match("nhạc của tui", installed))
        assertEquals(listOf("com.bilang042.aureseyes"), InstalledAppMatcher.match("âu rét", installed))
        assertTrue(InstalledAppMatcher.match("không có app này", installed).isEmpty())
        assertTrue(InstalledAppMatcher.match("x", installed).isEmpty())
    }

    @Test
    fun executorOpensMatchedPackageOrSaysNotFound() = runBlocking {
        val opened = mutableListOf<List<String>>()
        val backend = RecordingBackend(opened)
        val executor = AndroidVoiceCommandExecutor(
            backend = backend,
            mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
            navigationResolver = NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
            installedApps = { installed }
        )
        assertTrue(executor.execute(VoiceCommand.OpenInstalledApp("fpt play")) is CommandExecutionResult.Confirmed)
        assertEquals(listOf(listOf("com.fptplay.activity")), opened)
        val missing = executor.execute(VoiceCommand.OpenInstalledApp("netflix kids"))
        assertTrue(missing is CommandExecutionResult.Rejected)
        assertEquals(VoiceResponseFormatter.APP_NOT_FOUND, (missing as CommandExecutionResult.Rejected).reason)
    }

    @Test
    fun spokenResultNeverReadsTheDynamicAppName() {
        val text = VoiceResponseFormatter.result(
            VoiceCommand.OpenInstalledApp("fpt play"),
            CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE, "com.fptplay.activity")
        )
        assertEquals("Đã mở ứng dụng.", text)
    }

    private class RecordingBackend(private val opened: MutableList<List<String>>) : AndroidActionBackend {
        override fun openApp(packageCandidates: List<String>): AndroidBackendResult {
            opened += packageCandidates
            return AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, packageCandidates.first())
        }
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
