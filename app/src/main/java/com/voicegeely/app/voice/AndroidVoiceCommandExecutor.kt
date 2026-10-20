package com.voicegeely.app.voice

import com.voicegeely.app.settings.DefaultNavigationApp
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Typed executor for app launch, media, navigation and Android capability/settings commands. */
class AndroidVoiceCommandExecutor(
    private val backend: AndroidActionBackend,
    private val mediaResolver: MediaPreferenceResolver,
    private val navigationResolver: NavigationPreferenceResolver =
        NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
    private val appPipInspector: ((List<String>) -> AppPipCapability)? = null,
    private val wifiSettingsLauncher: (() -> AndroidBackendResult)? = null,
    private val displayBrightnessModeSetter: ((DisplayBrightnessMode) -> AndroidBackendResult)? = null,
    private val sceneProviderProbe: (() -> SceneProviderSnapshot)? = null,
    private val sceneScreenLauncher: ((SceneType) -> AndroidBackendResult)? = null,
    private val homeLauncher: (() -> AndroidBackendResult)? = null,
    private val blackoutController: ((Boolean) -> AndroidBackendResult)? = null,
    private val clock: () -> java.time.ZonedDateTime = { java.time.ZonedDateTime.now() },
    private val installedApps: (() -> List<InstalledAppMatcher.InstalledApp>)? = null,
    private val splitScreen: SplitScreenExecutor? = null
) : VoiceCommandExecutor {
    override suspend fun execute(command: VoiceCommand): CommandExecutionResult = when (command) {
        is VoiceCommand.OpenApp -> executeOpenApp(command.target)
        is VoiceCommand.OpenInstalledApp -> executeOpenInstalledApp(command.spokenName)
        is VoiceCommand.OpenMedia -> executeOpenMedia(command)
        is VoiceCommand.SearchMedia -> executeSearch(command)
        is VoiceCommand.MediaControl -> mapResult(backend.media(command.action))
        is VoiceCommand.SetMediaVolume -> mapResult(backend.setMediaVolume(command.value, command.unit))
        is VoiceCommand.AdjustMediaVolume -> mapResult(backend.adjustMediaVolume(command.steps))
        is VoiceCommand.Navigate -> executeNavigation(command)
        is VoiceCommand.QuerySceneCapability -> executeSceneQuery(command)
        is VoiceCommand.OpenSceneScreen -> executeOpenSceneScreen(command.scene)
        VoiceCommand.CloseAllApps -> executeLocal("về màn hình chính", homeLauncher) { context ->
            { AndroidHomeLauncher(context).goHome() }
        }
        is VoiceCommand.ScreenBlackout -> executeLocal("đổi màn hình đen", blackoutController?.let { { it(command.on) } }) { context ->
            { com.voicegeely.app.ui.BlackoutActivity.set(context, command.on) }
        }
        is VoiceCommand.QueryLocalInfo -> executeLocalInfo(command.kind)
        is VoiceCommand.QueryWindowingCapability -> executeWindowingQuery(command)
        is VoiceCommand.SplitScreen -> executeSplitScreen(command)
        VoiceCommand.QueryWifiState -> mapResult(backend.queryWifiState())
        VoiceCommand.QueryWifiIpAddress -> mapResult(backend.queryWifiIpAddress())
        VoiceCommand.QueryWifiSsid -> mapResult(backend.queryWifiSsid())
        VoiceCommand.OpenWifiSettings -> executeOpenWifiSettings()
        VoiceCommand.QueryDisplayBrightness -> mapResult(backend.queryDisplayBrightness())
        is VoiceCommand.SetManualDisplayBrightness -> executeDisplayBrightness(command)
        else -> CommandExecutionResult.Rejected("Lệnh này không thuộc nhóm Android/app/media/navigation.")
    }

    private fun executeSplitScreen(command: VoiceCommand.SplitScreen): CommandExecutionResult {
        val executor = splitScreen ?: AndroidRuntimeContext.getOrNull()?.let { runtimeSplitScreen(it) }
            ?: return CommandExecutionResult.Rejected("Bộ chia màn hình chưa sẵn sàng.")
        return executor.execute(command)
    }

    private fun executeSceneQuery(command: VoiceCommand.QuerySceneCapability): CommandExecutionResult {
        val component = try {
            backend.querySceneCapability(command.scene)
        } catch (t: Throwable) {
            return CommandExecutionResult.Failed(
                t.message ?: "Không kiểm tra được khả năng scene.",
                t::class.java.name
            )
        }
        if (!component.accepted || !component.confirmed) return mapResult(component)

        val runtimeProbe = sceneProviderProbe ?: AndroidRuntimeContext.getOrNull()?.let { context ->
            { AndroidSceneProviderContractProbe(context).probe() }
        }
        val snapshot = try {
            runtimeProbe?.invoke()
        } catch (_: Throwable) {
            null
        }
        return mapResult(SceneCapabilityQueryResolver.resolve(component, snapshot))
    }

    private fun executeDisplayBrightness(command: VoiceCommand.SetManualDisplayBrightness): CommandExecutionResult {
        val requestedMode = command.mode
        if (requestedMode != null) {
            val setter = displayBrightnessModeSetter
                ?: return CommandExecutionResult.Rejected("Bộ đổi chế độ độ sáng hệ thống chưa sẵn sàng.")
            val result = try {
                setter(requestedMode)
            } catch (t: Throwable) {
                return CommandExecutionResult.Failed(
                    t.message ?: "Không đổi được chế độ độ sáng hệ thống.",
                    t::class.java.name
                )
            }
            return mapResult(result)
        }

        return if (command.percent in 1..100) {
            mapResult(backend.setManualDisplayBrightness(command.percent))
        } else {
            CommandExecutionResult.Rejected("Mức sáng màn hình phải từ 1 đến 100 phần trăm.")
        }
    }

    private fun executeLocal(
        label: String,
        injected: (() -> AndroidBackendResult)?,
        runtime: (android.content.Context) -> () -> AndroidBackendResult
    ): CommandExecutionResult {
        val action = injected ?: AndroidRuntimeContext.getOrNull()?.let(runtime)
            ?: return CommandExecutionResult.Rejected("Không có Android context để $label.")
        val result = try {
            action()
        } catch (t: Throwable) {
            return CommandExecutionResult.Failed(t.message ?: "Không $label được.", t::class.java.name)
        }
        return mapResult(result)
    }

    /** Time/date come from the device clock; the spoken text is built in VoiceResponseFormatter. */
    private fun executeLocalInfo(kind: LocalInfoKind): CommandExecutionResult {
        val now = try { clock() } catch (t: Throwable) {
            return CommandExecutionResult.Failed(t.message ?: "Không đọc được giờ hệ thống.", t::class.java.name)
        }
        val value = when (kind) {
            LocalInfoKind.TIME -> "%02d:%02d".format(now.hour, now.minute)
            LocalInfoKind.DATE -> "${now.dayOfWeek.value}|${now.dayOfMonth}|${now.monthValue}"
            LocalInfoKind.ASSISTANT_STATUS -> "ready"
        }
        return CommandExecutionResult.Confirmed(VerificationSource.ANDROID_STATE, value)
    }

    private fun executeOpenInstalledApp(spokenName: String): CommandExecutionResult {
        val runtimeSource: (() -> List<InstalledAppMatcher.InstalledApp>)? =
            AndroidRuntimeContext.getOrNull()?.let { context -> { AndroidInstalledAppSource(context).apps() } }
        val source = installedApps ?: runtimeSource
            ?: return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_LIST_UNAVAILABLE)
        val apps = try { source() } catch (_: Throwable) {
            return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_LIST_UNAVAILABLE)
        }
        val packages = InstalledAppMatcher.match(spokenName, apps)
        if (packages.isEmpty()) return CommandExecutionResult.Rejected(VoiceResponseFormatter.APP_NOT_FOUND)
        return mapResult(backend.openApp(packages))
    }

    private fun executeOpenSceneScreen(scene: SceneType): CommandExecutionResult {
        val launcher = sceneScreenLauncher ?: AndroidRuntimeContext.getOrNull()?.let { context ->
            { target: SceneType -> AndroidSceneScreenLauncher(context).open(target) }
        } ?: return CommandExecutionResult.Rejected("Không có Android context để mở màn hình chế độ.")
        val result = try {
            launcher(scene)
        } catch (t: Throwable) {
            return CommandExecutionResult.Failed(t.message ?: "Không mở được màn hình chế độ.", t::class.java.name)
        }
        return mapResult(result)
    }

    private fun executeOpenWifiSettings(): CommandExecutionResult {
        val launcher = wifiSettingsLauncher ?: AndroidRuntimeContext.getOrNull()?.let { context ->
            { AndroidWifiSettingsLauncher(context).open() }
        } ?: return CommandExecutionResult.Rejected("Không có Android context để mở cài đặt Wi-Fi.")
        val result = try {
            launcher()
        } catch (t: Throwable) {
            return CommandExecutionResult.Failed(
                t.message ?: "Không mở được cài đặt Wi-Fi.",
                t::class.java.name
            )
        }
        return mapResult(result)
    }

    private fun executeWindowingQuery(command: VoiceCommand.QueryWindowingCapability): CommandExecutionResult {
        val target = command.target
        if (command.mode != WindowingMode.PIP || target == null) {
            return mapResult(backend.queryWindowingCapability(command.mode))
        }
        val runtimeContext = AndroidRuntimeContext.getOrNull()
        val inspector = appPipInspector ?: runtimeContext?.let { context ->
            { packages: List<String> -> AndroidWindowingAppInspector(context).inspectPip(packages) }
        } ?: return CommandExecutionResult.Rejected("Bộ dò PiP theo ứng dụng chưa sẵn sàng trong process này.")

        val capability = try {
            inspector(AndroidAppCatalog.packagesFor(target))
        } catch (t: Throwable) {
            return CommandExecutionResult.Failed(
                t.message ?: "Không kiểm tra được điều kiện PiP của ứng dụng.",
                t::class.java.name
            )
        }
        val app = appTargetText(target)
        val actual = when (capability.state) {
            AppPipCapabilityState.SYSTEM_UNSUPPORTED ->
                "Android không khai báo hỗ trợ PiP trên thiết bị này"
            AppPipCapabilityState.APP_NOT_VISIBLE ->
                "Không tìm thấy $app trong các package mục tiêu nhìn thấy được"
            AppPipCapabilityState.APP_VISIBLE_UNVERIFIED ->
                "$app có launch Activity nhìn thấy và hệ thống khai báo PiP; hỗ trợ PiP thực tế của ứng dụng vẫn chưa được xác minh"
        }
        return CommandExecutionResult.Confirmed(
            verificationSource = VerificationSource.ANDROID_STATE,
            actualValue = actual
        )
    }

    private fun executeOpenApp(target: AppTarget): CommandExecutionResult {
        val packages = when (target) {
            // Default map app from Settings/Apps first, then the automatic fallbacks.
            AppTarget.DEFAULT_MAPS -> navigationResolver.providerOrder(NavigationProvider.AUTO)
                .flatMap { AndroidAppCatalog.packagesFor(it) }
            // Saved default YouTube client first, then YouTube → Vanced → ReVanced → Morphe → SmartTube.
            AppTarget.YOUTUBE -> mediaResolver.providerOrder(MediaContentType.VIDEO, MediaProvider.YOUTUBE)
                .flatMap { AndroidAppCatalog.packagesFor(it) }
            else -> AndroidAppCatalog.packagesFor(target)
        }.distinct()
        return mapResult(backend.openApp(packages))
    }

    private fun executeOpenMedia(command: VoiceCommand.OpenMedia): CommandExecutionResult {
        val providers = mediaResolver.providerOrder(command.kind, command.provider)
        for (provider in providers) {
            val result = backend.openApp(AndroidAppCatalog.packagesFor(provider))
            if (result.accepted) return mapResult(result)
        }
        return CommandExecutionResult.Rejected("Không tìm thấy ứng dụng media phù hợp đã cài đặt.")
    }

    private fun executeSearch(command: VoiceCommand.SearchMedia): CommandExecutionResult {
        val query = command.query.trim()
        if (query.isEmpty()) return CommandExecutionResult.Rejected("Thiếu nội dung cần tìm.")
        val providers = mediaResolver.providerOrder(command.kind, command.provider)
        for (provider in providers) {
            val uri = searchUri(provider, query) ?: continue
            val result = backend.openUri(uri, AndroidAppCatalog.packagesFor(provider))
            if (result.accepted) return mapResult(result)
        }
        return CommandExecutionResult.Rejected("Không có ứng dụng media phù hợp nhận được lệnh tìm kiếm.")
    }

    private fun searchUri(provider: MediaProvider, query: String): String? {
        val encoded = encode(query)
        return when (provider) {
            MediaProvider.DEFAULT -> null
            MediaProvider.YOUTUBE,
            MediaProvider.VANCED,
            MediaProvider.REVANCED,
            MediaProvider.MORPHE,
            MediaProvider.SMARTTUBE -> "https://www.youtube.com/results?search_query=$encoded"
            MediaProvider.YOUTUBE_MUSIC -> "https://music.youtube.com/search?q=$encoded"
            MediaProvider.SPOTIFY -> "spotify:search:$encoded"
            MediaProvider.ZING_MP3 -> "https://zingmp3.vn/tim-kiem/tat-ca?q=$encoded"
        }
    }

    private fun executeNavigation(command: VoiceCommand.Navigate): CommandExecutionResult {
        val destination = command.destination.trim()
        if (destination.isEmpty()) return CommandExecutionResult.Rejected("Thiếu điểm đến.")
        val encoded = encode(destination)

        for (provider in navigationResolver.providerOrder(command.provider)) {
            val uri = navigationUri(provider, encoded) ?: continue
            val result = backend.openUri(uri, AndroidAppCatalog.packagesFor(provider))
            if (result.accepted) return mapResult(result)
        }
        return CommandExecutionResult.Rejected("Không tìm thấy ứng dụng bản đồ phù hợp đã cài đặt.")
    }

    private fun navigationUri(provider: NavigationProvider, encodedDestination: String): String? = when (provider) {
        NavigationProvider.AUTO -> null
        NavigationProvider.GOOGLE_MAPS -> "google.navigation:q=$encodedDestination"
        NavigationProvider.WAZE -> "https://waze.com/ul?q=$encodedDestination&navigate=yes"
        NavigationProvider.VIETMAP -> "geo:0,0?q=$encodedDestination"
    }

    private fun appTargetText(target: AppTarget): String = when (target) {
        AppTarget.YOUTUBE -> "YouTube"
        AppTarget.VANCED -> "YouTube Vanced"
        AppTarget.REVANCED -> "YouTube ReVanced"
        AppTarget.MORPHE -> "Morphe"
        AppTarget.YOUTUBE_MUSIC -> "YouTube Music"
        AppTarget.ZING_MP3 -> "Zing MP3"
        AppTarget.SPOTIFY -> "Spotify"
        AppTarget.SMARTTUBE -> "SmartTube"
        AppTarget.MAPS -> "Google Maps"
        AppTarget.DEFAULT_MAPS -> "bản đồ"
        AppTarget.WAZE -> "Waze"
        AppTarget.VIETMAP -> "Vietmap Live"
        AppTarget.CHROME -> "Chrome"
        AppTarget.NETFLIX -> "Netflix"
        AppTarget.TIKTOK -> "TikTok"
        AppTarget.VLC -> "VLC"
        AppTarget.KIKI -> "Kiki"
    }

    private fun mapResult(result: AndroidBackendResult): CommandExecutionResult = when {
        !result.accepted -> CommandExecutionResult.Rejected(result.detail ?: "Không tìm thấy ứng dụng phù hợp hoặc Android từ chối lệnh.")
        result.confirmed -> CommandExecutionResult.Confirmed(result.verificationSource, result.actualValue)
        else -> CommandExecutionResult.AcceptedUnverified(result.detail ?: "Android đã nhận lệnh nhưng chưa xác nhận được trạng thái cuối.")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

/** One pair memory per process so "YouTube toàn màn hình" knows the pair sent earlier. */
private val runtimeSplitMemory = SplitPairMemory()

private fun runtimeSplitScreen(context: android.content.Context): SplitScreenExecutor =
    SplitScreenExecutor(ThtSplitClient(context), runtimeSplitMemory)
