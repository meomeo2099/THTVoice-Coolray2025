package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVoiceCommandExecutorTest {
    private fun executor(
        backend: FakeBackend,
        selectedMedia: DefaultMediaApp = DefaultMediaApp.AUTO,
        selectedNavigation: DefaultNavigationApp = DefaultNavigationApp.AUTO
    ) = AndroidVoiceCommandExecutor(
        backend,
        MediaPreferenceResolver { selectedMedia },
        NavigationPreferenceResolver { selectedNavigation }
    )

    @Test fun youtubePackageIsStockOnlyWhenExplicit() {
        assertEquals(listOf("com.google.android.youtube"), AndroidAppCatalog.packagesFor(AppTarget.YOUTUBE))
        assertEquals(listOf("com.vanced.android.youtube"), AndroidAppCatalog.packagesFor(AppTarget.VANCED))
        assertEquals("app.revanced.android.youtube", AndroidAppCatalog.packagesFor(AppTarget.REVANCED).first())
    }

    @Test fun automaticVideoPriorityMatchesVehiclePolicy() {
        assertEquals(
            listOf(
                MediaProvider.YOUTUBE,
                MediaProvider.VANCED,
                MediaProvider.REVANCED,
                MediaProvider.MORPHE,
                MediaProvider.SMARTTUBE
            ),
            MediaPreferenceResolver { DefaultMediaApp.AUTO }
                .providerOrder(MediaContentType.VIDEO, MediaProvider.DEFAULT)
        )
    }

    @Test fun confirmedAppLaunchMapsToConfirmed() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true, actualValue = "com.google.android.youtube") }
        val result = executor(backend).execute(VoiceCommand.OpenApp(AppTarget.YOUTUBE))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals("com.google.android.youtube", (result as CommandExecutionResult.Confirmed).actualValue)
    }

    @Test fun mediaKeyWithoutReadbackStaysUnverified() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, false, VerificationSource.MEDIA_SESSION, detail = "sent") }
        val result = executor(backend).execute(VoiceCommand.MediaControl(MediaAction.NEXT))
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
    }

    @Test fun autoVideoFallbackStopsImmediatelyAfterFirstAcceptedProvider() = runBlocking {
        val backend = FakeBackend().apply {
            queued += AndroidBackendResult(false, false, detail = "stock missing")
            queued += AndroidBackendResult(false, false, detail = "vanced missing")
            queued += AndroidBackendResult(true, true, actualValue = "app.revanced.android.youtube")
            queued += AndroidBackendResult(true, true, actualValue = "must-not-run")
        }

        val result = executor(backend).execute(
            VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.DEFAULT)
        )

        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(3, backend.appCalls.size)
        assertEquals(AndroidAppCatalog.packagesFor(MediaProvider.YOUTUBE), backend.appCalls[0])
        assertEquals(AndroidAppCatalog.packagesFor(MediaProvider.VANCED), backend.appCalls[1])
        assertEquals(AndroidAppCatalog.packagesFor(MediaProvider.REVANCED), backend.appCalls[2])
    }

    @Test fun explicitMediaProviderIsStrictAndNeverFallsBack() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(false, false, detail = "not installed") }

        val result = executor(backend, selectedMedia = DefaultMediaApp.YOUTUBE).execute(
            VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.VANCED)
        )

        assertTrue(result is CommandExecutionResult.Rejected)
        assertEquals(1, backend.appCalls.size)
        assertEquals(AndroidAppCatalog.packagesFor(MediaProvider.VANCED), backend.appCalls.single())
    }

    @Test fun openYoutubeFallsBackToAlternativeClientsInOwnerOrder() = runBlocking {
        val backend = FakeBackend().apply {
            queued += AndroidBackendResult(false, false, detail = "stock missing")
            queued += AndroidBackendResult(false, false, detail = "vanced missing")
            queued += AndroidBackendResult(false, false, detail = "revanced missing")
            queued += AndroidBackendResult(false, false, detail = "morphe missing")
            queued += AndroidBackendResult(true, true, actualValue = "org.smarttube.stable")
        }
        val result = executor(backend, selectedMedia = DefaultMediaApp.ZING_MP3).execute(
            VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.YOUTUBE)
        )
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(
            listOf(MediaProvider.YOUTUBE, MediaProvider.VANCED, MediaProvider.REVANCED, MediaProvider.MORPHE, MediaProvider.SMARTTUBE)
                .map { AndroidAppCatalog.packagesFor(it) },
            backend.appCalls
        )
    }

    @Test fun openAppYoutubeTriesTheWholeYoutubeFamilyInOrder() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend).execute(VoiceCommand.OpenApp(AppTarget.YOUTUBE))
        val packages = backend.lastPackages
        assertEquals("com.google.android.youtube", packages.first())
        assertTrue(packages.indexOf("com.vanced.android.youtube") < packages.indexOf("app.revanced.android.youtube"))
        assertTrue(packages.indexOf("app.revanced.android.youtube") < packages.indexOf("app.morphe.android.youtube"))
        assertTrue(packages.indexOf("app.morphe.android.youtube") < packages.indexOf("org.smarttube.stable"))
    }

    @Test fun openYoutubeUsesTheSavedDefaultYoutubeClientFirst() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend, selectedMedia = DefaultMediaApp.REVANCED).execute(
            VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.YOUTUBE)
        )
        assertEquals(AndroidAppCatalog.packagesFor(MediaProvider.REVANCED), backend.appCalls.first())

        val appBackend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(appBackend, selectedMedia = DefaultMediaApp.SMARTTUBE).execute(VoiceCommand.OpenApp(AppTarget.YOUTUBE))
        assertEquals("org.smarttube.stable", appBackend.lastPackages.first())
        assertTrue("com.google.android.youtube" in appBackend.lastPackages)
    }

    @Test fun openMapsUsesTheSavedDefaultMapApp() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend, selectedNavigation = DefaultNavigationApp.VIETMAP).execute(VoiceCommand.OpenApp(AppTarget.DEFAULT_MAPS))
        assertEquals("vn.vietmap.live", backend.lastPackages.first())
        assertTrue("com.google.android.apps.maps" in backend.lastPackages)
    }

    @Test fun explicitYoutubeSearchUsesStockYoutubeOnlyAndNeverChrome() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend).execute(VoiceCommand.SearchMedia(MediaProvider.YOUTUBE, "sơn tùng", MediaContentType.VIDEO))
        assertTrue(backend.lastUri?.startsWith("https://www.youtube.com/results?search_query=") == true)
        assertTrue(backend.lastUri?.contains("+") == false)
        assertEquals(listOf("com.google.android.youtube"), backend.lastPackages)
        assertTrue("com.android.chrome" !in backend.lastPackages)
        assertEquals(1, backend.uriCalls.size)
    }

    @Test fun explicitSearchProviderIsStrictWhenRejected() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(false, false, detail = "not installed") }

        val result = executor(backend, selectedMedia = DefaultMediaApp.YOUTUBE).execute(
            VoiceCommand.SearchMedia(MediaProvider.REVANCED, "hello", MediaContentType.VIDEO)
        )

        assertTrue(result is CommandExecutionResult.Rejected)
        assertEquals(1, backend.uriCalls.size)
        assertEquals(AndroidAppCatalog.packagesFor(MediaProvider.REVANCED), backend.uriCalls.single().second)
        assertTrue("com.android.chrome" !in backend.uriCalls.single().second)
    }

    @Test fun blankMediaSearchFailsBeforeCallingAndroidBackend() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        val result = executor(backend).execute(
            VoiceCommand.SearchMedia(MediaProvider.YOUTUBE, "   ", MediaContentType.VIDEO)
        )
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(backend.uriCalls.isEmpty())
        assertTrue(backend.appCalls.isEmpty())
    }

    @Test fun selectedRevancedIsFirstForDefaultMusicOpen() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend, selectedMedia = DefaultMediaApp.REVANCED).execute(
            VoiceCommand.OpenMedia(MediaContentType.MUSIC, MediaProvider.DEFAULT)
        )
        assertEquals("app.revanced.android.youtube", backend.lastPackages.first())
        assertEquals(1, backend.appCalls.size)
    }

    @Test fun selectedVietmapHandlesAutoNavigation() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend, selectedNavigation = DefaultNavigationApp.VIETMAP).execute(
            VoiceCommand.Navigate(NavigationProvider.AUTO, "sân bay")
        )
        assertTrue(backend.lastUri?.startsWith("geo:0,0?q=") == true)
        assertEquals(listOf("vn.vietmap.live"), backend.lastPackages)
        assertEquals(1, backend.uriCalls.size)
    }

    @Test fun automaticNavigationFallbackStopsAfterFirstAcceptedProvider() = runBlocking {
        val backend = FakeBackend().apply {
            queued += AndroidBackendResult(false, false, detail = "maps missing")
            queued += AndroidBackendResult(true, true, actualValue = "com.waze")
            queued += AndroidBackendResult(true, true, actualValue = "must-not-run")
        }

        val result = executor(backend, selectedNavigation = DefaultNavigationApp.AUTO).execute(
            VoiceCommand.Navigate(NavigationProvider.AUTO, "sân bay")
        )

        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(2, backend.uriCalls.size)
        assertEquals(AndroidAppCatalog.packagesFor(NavigationProvider.GOOGLE_MAPS), backend.uriCalls[0].second)
        assertEquals(AndroidAppCatalog.packagesFor(NavigationProvider.WAZE), backend.uriCalls[1].second)
        assertTrue(backend.uriCalls.none { "com.android.chrome" in it.second })
    }

    @Test fun explicitlySpokenWazeOverridesSavedGoogleMapsAndIsStrict() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        executor(backend, selectedNavigation = DefaultNavigationApp.GOOGLE_MAPS).execute(
            VoiceCommand.Navigate(NavigationProvider.WAZE, "sân bay")
        )
        assertTrue(backend.lastUri?.startsWith("https://waze.com/ul?") == true)
        assertEquals(listOf("com.waze"), backend.lastPackages)
        assertEquals(1, backend.uriCalls.size)
    }

    @Test fun blankNavigationFailsBeforeCallingAndroidBackend() = runBlocking {
        val backend = FakeBackend().apply { next = AndroidBackendResult(true, true) }
        val result = executor(backend).execute(VoiceCommand.Navigate(NavigationProvider.AUTO, "  "))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(backend.uriCalls.isEmpty())
    }

    @Test fun manualBrightnessRequiresBackendReadbackForSuccess() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true, actualValue = "50 phần trăm")
        }
        val command = VoiceCommand.SetManualDisplayBrightness(50)
        val result = executor(backend).execute(command)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(50), backend.brightnessWrites)
        assertEquals("Đã đặt mức sáng hệ thống 50 phần trăm.",
            VoiceResponseFormatter.result(command, result))
    }

    @Test fun staleBrightnessReadbackNeverSpeaksSuccess() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, false, detail = "Đọc lại chưa khớp.")
        }
        val command = VoiceCommand.SetManualDisplayBrightness(50)
        val result = executor(backend).execute(command)
        assertTrue(result is CommandExecutionResult.AcceptedUnverified)
        assertTrue(VoiceResponseFormatter.result(command, result).contains("chưa xác nhận"))
    }

    @Test fun brightnessOutOfRangeNeverCallsAndroid() = runBlocking {
        val backend = FakeBackend()
        val result = executor(backend).execute(VoiceCommand.SetManualDisplayBrightness(101))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(backend.brightnessWrites.isEmpty())
    }

    @Test fun displaySettingReadbackDoesNotClaimPhysicalLuminance() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true,
                actualValue = "Thiết lập độ sáng hệ thống đang ở chế độ tự động")
        }
        val result = executor(backend).execute(VoiceCommand.QueryDisplayBrightness)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(1, backend.displayQueries)
        assertTrue(backend.appCalls.isEmpty())
        assertEquals("Thiết lập độ sáng hệ thống đang ở chế độ tự động.",
            VoiceResponseFormatter.result(VoiceCommand.QueryDisplayBrightness, result))
    }

    @Test fun displaySettingUnavailableDoesNotClaimSuccess() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(false, false, detail = "Không đọc được thiết lập.")
        }
        val result = executor(backend).execute(VoiceCommand.QueryDisplayBrightness)
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test fun wifiSsidRequiresActualAndroidNetworkName() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true, actualValue = "Tên mạng Wi-Fi mặc định là Xe nha")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWifiSsid)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(1, backend.wifiSsidQueries)
        assertEquals("Tên mạng Wi-Fi mặc định là Xe nha.",
            VoiceResponseFormatter.result(VoiceCommand.QueryWifiSsid, result))
    }

    @Test fun redactedWifiSsidDoesNotBecomeSuccess() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(false, false, detail = "Android không cung cấp tên mạng.")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWifiSsid)
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test fun wifiIpIsReportedOnlyFromAndroidReadback() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true, actualValue = "IP nội bộ của mạng Wi-Fi mặc định là 192.168.1.8")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWifiIpAddress)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(1, backend.wifiIpQueries)
        assertTrue(backend.appCalls.isEmpty())
        assertEquals("IP nội bộ của mạng Wi-Fi mặc định là 192.168.1.8.",
            VoiceResponseFormatter.result(VoiceCommand.QueryWifiIpAddress, result))
    }

    @Test fun missingWifiLinkNeverClaimsIp() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(false, false, detail = "Wi-Fi không phải mạng mặc định.")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWifiIpAddress)
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(VoiceResponseFormatter.result(VoiceCommand.QueryWifiIpAddress, result).startsWith("Không thể"))
    }

    @Test fun wifiReadbackReportsCurrentStateWithoutAction() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true, actualValue = "Wi-Fi đang bật")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWifiState)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(1, backend.wifiQueries)
        assertTrue(backend.appCalls.isEmpty())
        assertTrue(backend.uriCalls.isEmpty())
        assertEquals("Wi-Fi đang bật.", VoiceResponseFormatter.result(VoiceCommand.QueryWifiState, result))
    }

    @Test fun wifiReadPermissionFailureDoesNotClaimState() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(false, false, detail = "Android không cho phép đọc trạng thái Wi-Fi.")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWifiState)
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(VoiceResponseFormatter.result(VoiceCommand.QueryWifiState, result).startsWith("Không thể"))
    }

    @Test fun windowingFeatureObservationDoesNotLaunchAnotherApp() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true,
                actualValue = "Android khai báo hỗ trợ PiP; chưa xác minh ứng dụng mục tiêu")
        }
        val result = executor(backend).execute(VoiceCommand.QueryWindowingCapability(WindowingMode.PIP))
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(WindowingMode.PIP), backend.windowingCalls)
        assertTrue(backend.appCalls.isEmpty())
        assertTrue(backend.uriCalls.isEmpty())
    }

    @Test fun splitScreenWithoutContractIsRejected() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(false, false, detail = "Chưa có API OEM.")
        }
        val result = executor(backend).execute(
            VoiceCommand.QueryWindowingCapability(WindowingMode.SPLIT_SCREEN))
        assertTrue(result is CommandExecutionResult.Rejected)
    }

    @Test fun sceneProbeIsReadOnlyAndReportsCapabilityObservation() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(true, true, actualValue = "Có lối mở Android cho chế độ nghỉ")
        }
        val command = VoiceCommand.QuerySceneCapability(SceneType.REST)
        val result = executor(backend).execute(command)
        assertTrue(result is CommandExecutionResult.Confirmed)
        assertEquals(listOf(SceneType.REST), backend.sceneCalls)
        assertTrue(backend.appCalls.isEmpty())
        assertTrue(backend.uriCalls.isEmpty())
        assertTrue(VoiceResponseFormatter.result(command, result).contains("lối mở Android"))
    }

    @Test fun sceneProbePermissionDeniedDoesNotClaimAvailability() = runBlocking {
        val backend = FakeBackend().apply {
            next = AndroidBackendResult(false, false, detail = "Android không cho phép kiểm tra.")
        }
        val result = executor(backend).execute(VoiceCommand.QuerySceneCapability(SceneType.CAMPING))
        assertTrue(result is CommandExecutionResult.Rejected)
        assertTrue(VoiceResponseFormatter.result(VoiceCommand.QuerySceneCapability(SceneType.CAMPING), result)
            .startsWith("Không thể"))
    }

    private class FakeBackend : AndroidActionBackend {
        var next = AndroidBackendResult(false, false)
        val queued = mutableListOf<AndroidBackendResult>()
        var lastUri: String? = null
        var lastPackages: List<String> = emptyList()
        val sceneCalls = mutableListOf<SceneType>()
        val windowingCalls = mutableListOf<WindowingMode>()
        var wifiQueries = 0
        var wifiIpQueries = 0
        var wifiSsidQueries = 0
        var displayQueries = 0
        val brightnessWrites = mutableListOf<Int>()
        val appCalls = mutableListOf<List<String>>()
        val uriCalls = mutableListOf<Pair<String, List<String>>>()

        override fun openApp(packageCandidates: List<String>): AndroidBackendResult {
            lastPackages = packageCandidates
            appCalls += packageCandidates.toList()
            return takeResult()
        }

        override fun openUri(uri: String, packageCandidates: List<String>): AndroidBackendResult {
            lastUri = uri
            lastPackages = packageCandidates
            uriCalls += uri to packageCandidates.toList()
            return takeResult()
        }

        override fun media(action: MediaAction): AndroidBackendResult = takeResult()

        override fun setManualDisplayBrightness(percent: Int): AndroidBackendResult {
            brightnessWrites += percent
            return takeResult()
        }

        override fun queryDisplayBrightness(): AndroidBackendResult {
            displayQueries++
            return takeResult()
        }

        override fun queryWifiSsid(): AndroidBackendResult {
            wifiSsidQueries++
            return takeResult()
        }

        override fun queryWifiIpAddress(): AndroidBackendResult {
            wifiIpQueries++
            return takeResult()
        }

        override fun queryWifiState(): AndroidBackendResult {
            wifiQueries++
            return takeResult()
        }

        override fun queryWindowingCapability(mode: WindowingMode): AndroidBackendResult {
            windowingCalls += mode
            return takeResult()
        }

        override fun querySceneCapability(scene: SceneType): AndroidBackendResult {
            sceneCalls += scene
            return takeResult()
        }

        private fun takeResult(): AndroidBackendResult = if (queued.isNotEmpty()) queued.removeAt(0) else next
    }
}
