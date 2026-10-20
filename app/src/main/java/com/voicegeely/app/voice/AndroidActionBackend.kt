package com.voicegeely.app.voice

/** Pure interface; Android implementation lives behind this boundary. */
interface AndroidActionBackend {
    fun openApp(packageCandidates: List<String>): AndroidBackendResult
    fun openUri(uri: String, packageCandidates: List<String>): AndroidBackendResult
    fun media(action: MediaAction): AndroidBackendResult
    fun setMediaVolume(value: Int, unit: VolumeUnit): AndroidBackendResult =
        AndroidBackendResult(false, false, detail = "Chưa hỗ trợ chỉnh âm lượng theo số.")
    fun adjustMediaVolume(steps: Int): AndroidBackendResult =
        AndroidBackendResult(false, false, detail = "Chưa hỗ trợ chỉnh âm lượng theo số.")
    /** Reports component visibility only; must never launch or change scene state. */
    fun querySceneCapability(scene: SceneType): AndroidBackendResult
    fun queryWindowingCapability(mode: WindowingMode): AndroidBackendResult
    fun queryWifiState(): AndroidBackendResult
    fun queryWifiIpAddress(): AndroidBackendResult
    fun queryWifiSsid(): AndroidBackendResult
    fun queryDisplayBrightness(): AndroidBackendResult
    fun setManualDisplayBrightness(percent: Int): AndroidBackendResult
}

data class AndroidBackendResult(
    val accepted: Boolean,
    val confirmed: Boolean,
    val verificationSource: VerificationSource = VerificationSource.ANDROID_STATE,
    val actualValue: String? = null,
    val detail: String? = null
)

object AndroidAppCatalog {
    /** "Mở YouTube": the official app first, then the owner's order of alternative clients. */
    val youtubeFamily: List<AppTarget> = listOf(
        AppTarget.YOUTUBE, AppTarget.VANCED, AppTarget.REVANCED, AppTarget.MORPHE, AppTarget.SMARTTUBE
    )

    fun packagesFor(target: AppTarget): List<String> = when (target) {
        AppTarget.YOUTUBE -> listOf("com.google.android.youtube")
        AppTarget.VANCED -> listOf("com.vanced.android.youtube")
        AppTarget.REVANCED -> listOf(
            "app.revanced.android.youtube",
            "app.rvx.android.youtube",
            "com.revanced.android.youtube",
            "anddea.youtube"
        )
        AppTarget.MORPHE -> listOf("app.morphe.android.youtube")
        AppTarget.YOUTUBE_MUSIC -> listOf(
            "com.google.android.apps.youtube.music",
            "app.revanced.android.apps.youtube.music",
            "anddea.youtube.music"
        )
        AppTarget.ZING_MP3 -> listOf("com.zing.mp3")
        AppTarget.SPOTIFY -> listOf("com.spotify.music")
        AppTarget.SMARTTUBE -> listOf(
            "org.smarttube.stable",
            "org.smarttube.beta",
            "com.liskovsoft.smarttubetv",
            "com.liskovsoft.smarttubetv.beta",
            "com.teamsmart.videomanager.tv"
        )
        AppTarget.MAPS -> listOf("com.google.android.apps.maps", "app.morphe.android.apps.maps")
        AppTarget.DEFAULT_MAPS -> packagesFor(NavigationProvider.AUTO)
        AppTarget.WAZE -> listOf("com.waze")
        AppTarget.VIETMAP -> listOf("vn.vietmap.live")
        AppTarget.CHROME -> listOf("com.android.chrome")
        AppTarget.NETFLIX -> listOf("com.netflix.mediaclient")
        // TikTok ships as the Asia build (trill) in Vietnam; the global build is the fallback.
        AppTarget.TIKTOK -> listOf("com.ss.android.ugc.trill", "com.zhiliaoapp.musically")
        AppTarget.VLC -> listOf("org.videolan.vlc")
        AppTarget.KIKI -> listOf("ai.zalo.kiki.car")
    }

    fun packagesFor(provider: MediaProvider): List<String> = when (provider) {
        MediaProvider.DEFAULT -> emptyList()
        MediaProvider.YOUTUBE -> packagesFor(AppTarget.YOUTUBE)
        MediaProvider.VANCED -> packagesFor(AppTarget.VANCED)
        MediaProvider.REVANCED -> packagesFor(AppTarget.REVANCED)
        MediaProvider.MORPHE -> packagesFor(AppTarget.MORPHE)
        MediaProvider.YOUTUBE_MUSIC -> packagesFor(AppTarget.YOUTUBE_MUSIC)
        MediaProvider.ZING_MP3 -> packagesFor(AppTarget.ZING_MP3)
        MediaProvider.SPOTIFY -> packagesFor(AppTarget.SPOTIFY)
        MediaProvider.SMARTTUBE -> packagesFor(AppTarget.SMARTTUBE)
    }

    fun packagesFor(provider: NavigationProvider): List<String> = when (provider) {
        NavigationProvider.AUTO -> packagesFor(AppTarget.MAPS) + packagesFor(AppTarget.WAZE) + packagesFor(AppTarget.VIETMAP)
        NavigationProvider.GOOGLE_MAPS -> packagesFor(AppTarget.MAPS)
        NavigationProvider.WAZE -> packagesFor(AppTarget.WAZE)
        NavigationProvider.VIETMAP -> packagesFor(AppTarget.VIETMAP)
    }
}
