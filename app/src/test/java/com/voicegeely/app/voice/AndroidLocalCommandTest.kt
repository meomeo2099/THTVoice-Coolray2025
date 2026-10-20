package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.voice.safety.SafetyPolicy
import com.voicegeely.app.voice.safety.SafetyRequirement
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLocalCommandTest {
    @Test
    fun closeAllAppsPhrasesFromReferenceDataset() {
        listOf(
            "đóng tất cả ứng dụng", "đóng hết ứng dụng", "đóng toàn bộ ứng dụng", "đóng tất cả app", "đóng hết app",
            "đóng toàn bộ app", "tắt tất cả ứng dụng", "tắt hết ứng dụng", "tắt toàn bộ ứng dụng",
            "tắt các ứng dụng đang chạy", "tắt các app đang chạy", "tắt tất cả app", "tắt hết app", "tắt toàn bộ app",
            "thoát tất cả ứng dụng", "thoát hết ứng dụng", "dọn dẹp ứng dụng", "dọn dẹp app", "đóng các ứng dụng",
            "tắt các ứng dụng", "tắc ứng dụng", "tắt ứng dụng đang chạy", "đóng ứng dụng", "tắt ứng dụng",
            "thoát ứng dụng", "dừng ứng dụng", "kill app", "kill all app", "kill all apps",
            "xóa ứng dụng chạy ngầm", "tắt ứng dụng chạy ngầm"
        ).forEach { text -> assertEquals(text, VoiceCommand.CloseAllApps, VoiceCommandParser.parse(text)?.command) }
    }

    @Test
    fun uninstallLikePhraseIsNotCloseAll() {
        assertTrue(VoiceCommandParser.parse("xóa ứng dụng")?.command != VoiceCommand.CloseAllApps)
    }

    @Test
    fun blackoutPhrases() {
        listOf("tắt màn hình", "màn hình đen", "tối màn hình", "tắt màn hình xe").forEach { text ->
            assertEquals(text, VoiceCommand.ScreenBlackout(true), VoiceCommandParser.parse(text)?.command)
        }
        listOf("bật màn hình", "mở màn hình", "hết màn hình đen", "sáng màn hình").forEach { text ->
            assertEquals(text, VoiceCommand.ScreenBlackout(false), VoiceCommandParser.parse(text)?.command)
        }
        assertTrue(VoiceCommandParser.parse("mở màn hình cài đặt wifi")?.command !is VoiceCommand.ScreenBlackout)
    }

    @Test
    fun blackoutOnIsParkedOnlyButRestoreIsSafe() {
        assertEquals(SafetyRequirement.PARKED_ONLY, SafetyPolicy.requirement(VoiceCommand.ScreenBlackout(true)))
        assertEquals(SafetyRequirement.SAFE, SafetyPolicy.requirement(VoiceCommand.ScreenBlackout(false)))
    }

    @Test
    fun timeDateAndStatusPhrases() {
        listOf("mấy giờ rồi", "bây giờ là mấy giờ", "mấy giờ", "xem giờ", "mấy giờ rồi xe", "bây giờ mấy giờ", "giờ là bao nhiêu")
            .forEach { assertEquals(it, VoiceCommand.QueryLocalInfo(LocalInfoKind.TIME), VoiceCommandParser.parse(it)?.command) }
        listOf("hôm nay ngày bao nhiêu", "hôm nay là ngày gì", "ngày mấy")
            .forEach { assertEquals(it, VoiceCommand.QueryLocalInfo(LocalInfoKind.DATE), VoiceCommandParser.parse(it)?.command) }
        listOf(
            "được không em", "được không xe", "ổn không em", "sẵn sàng chưa em", "trợ lý có khỏe không",
            "trợ lý sẵn sàng chưa", "trợ lý nghe rõ không", "bạn có khỏe không", "bạn nghe rõ không", "bạn có nghe rõ không"
        ).forEach { assertEquals(it, VoiceCommand.QueryLocalInfo(LocalInfoKind.ASSISTANT_STATUS), VoiceCommandParser.parse(it)?.command) }
    }

    @Test
    fun timeAndDateAreSpokenFromDeviceClock() = runBlocking {
        val clock = { ZonedDateTime.of(2026, 9, 29, 12, 5, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh")) }
        val time = executor(clock = clock).execute(VoiceCommand.QueryLocalInfo(LocalInfoKind.TIME))
        assertEquals("Bây giờ là 12 giờ 5 phút.", VoiceResponseFormatter.result(VoiceCommand.QueryLocalInfo(LocalInfoKind.TIME), time))
        val date = executor(clock = clock).execute(VoiceCommand.QueryLocalInfo(LocalInfoKind.DATE))
        assertEquals("Hôm nay là thứ ba, ngày 29 tháng 9.", VoiceResponseFormatter.result(VoiceCommand.QueryLocalInfo(LocalInfoKind.DATE), date))
    }

    @Test
    fun homeAndBlackoutUseInjectedAndroidActions() = runBlocking {
        var home = 0
        val states = mutableListOf<Boolean>()
        val exec = executor(
            home = { home++; AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, "home") },
            blackout = { on -> states += on; AndroidBackendResult(true, true, VerificationSource.ANDROID_STATE, "x") }
        )
        assertTrue(exec.execute(VoiceCommand.CloseAllApps) is CommandExecutionResult.Confirmed)
        assertTrue(exec.execute(VoiceCommand.ScreenBlackout(true)) is CommandExecutionResult.Confirmed)
        assertTrue(exec.execute(VoiceCommand.ScreenBlackout(false)) is CommandExecutionResult.Confirmed)
        assertEquals(1, home)
        assertEquals(listOf(true, false), states)
    }

    @Test
    fun closeAllNeverClaimsAppsWereKilled() {
        val text = VoiceResponseFormatter.result(
            VoiceCommand.CloseAllApps,
            CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE, "home")
        )
        assertTrue(text, text.contains("màn hình chính") && !text.contains("Đã đóng"))
    }

    private fun executor(
        home: (() -> AndroidBackendResult)? = null,
        blackout: ((Boolean) -> AndroidBackendResult)? = null,
        clock: () -> ZonedDateTime = { ZonedDateTime.now() }
    ) = AndroidVoiceCommandExecutor(
        backend = NoopBackend,
        mediaResolver = MediaPreferenceResolver { DefaultMediaApp.AUTO },
        navigationResolver = NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
        homeLauncher = home,
        blackoutController = blackout,
        clock = clock
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
