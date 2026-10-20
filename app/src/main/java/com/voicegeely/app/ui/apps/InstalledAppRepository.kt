package com.voicegeely.app.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.voice.AndroidAppCatalog
import com.voicegeely.app.voice.AppTarget

class InstalledAppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun load(): List<LauncherAppEntry> {
        val known = knownDefinitions().associateBy { it.packageName }
        val launchable = queryLaunchablePackages().toMutableSet()
        known.keys.filterTo(launchable) { hasLaunchIntent(it) }

        return launchable.mapNotNull { packageName ->
            val definition = known[packageName]
            val label = applicationLabel(packageName) ?: definition?.label ?: packageName
            definition?.copy(label = label) ?: LauncherAppEntry(
                packageName = packageName,
                label = label,
                category = AppUiCategory.OTHER,
                voiceHint = "mở $label"
            )
        }.filterNot { it.packageName == appContext.packageName }
    }

    fun open(packageName: String): Boolean {
        val intent = runCatching { packageManager.getLaunchIntentForPackage(packageName) }.getOrNull() ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun queryLaunchablePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = runCatching {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        return activities.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    private fun hasLaunchIntent(packageName: String): Boolean = runCatching {
        packageManager.getLaunchIntentForPackage(packageName) != null
    }.getOrDefault(false)

    private fun applicationLabel(packageName: String): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString().trim().takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun knownDefinitions(): List<LauncherAppEntry> = buildList {
        addTarget(
            AppTarget.YOUTUBE, "YouTube", AppUiCategory.MEDIA,
            media = DefaultMediaApp.YOUTUBE, voiceHint = "mở YouTube"
        )
        addTarget(
            AppTarget.VANCED, "YouTube Vanced", AppUiCategory.MEDIA,
            media = DefaultMediaApp.VANCED, voiceHint = "mở YouTube Vanced"
        )
        addTarget(
            AppTarget.REVANCED, "ReVanced", AppUiCategory.MEDIA,
            media = DefaultMediaApp.REVANCED, voiceHint = "mở ReVanced"
        )
        addTarget(
            AppTarget.MORPHE, "Morphe", AppUiCategory.MEDIA,
            media = DefaultMediaApp.MORPHE, voiceHint = "mở Morphe"
        )
        addTarget(
            AppTarget.SMARTTUBE, "SmartTube", AppUiCategory.MEDIA,
            media = DefaultMediaApp.SMARTTUBE, voiceHint = "mở SmartTube"
        )
        addTarget(
            AppTarget.YOUTUBE_MUSIC, "YouTube Music", AppUiCategory.MEDIA,
            media = DefaultMediaApp.YOUTUBE_MUSIC, voiceHint = "mở YouTube Music"
        )
        addTarget(
            AppTarget.ZING_MP3, "Zing MP3", AppUiCategory.MEDIA,
            media = DefaultMediaApp.ZING_MP3, voiceHint = "mở Zing MP3"
        )
        addTarget(
            AppTarget.SPOTIFY, "Spotify", AppUiCategory.MEDIA,
            media = DefaultMediaApp.SPOTIFY, voiceHint = "mở Spotify"
        )
        addTarget(
            AppTarget.MAPS, "Google Maps", AppUiCategory.MAP,
            navigation = DefaultNavigationApp.GOOGLE_MAPS, voiceHint = "mở Google Maps"
        )
        addTarget(
            AppTarget.VIETMAP, "Vietmap Live", AppUiCategory.MAP,
            navigation = DefaultNavigationApp.VIETMAP, voiceHint = "mở Vietmap Live"
        )
        addTarget(
            AppTarget.WAZE, "Waze", AppUiCategory.MAP,
            navigation = DefaultNavigationApp.WAZE, voiceHint = "mở Waze"
        )
    }

    private fun MutableList<LauncherAppEntry>.addTarget(
        target: AppTarget,
        label: String,
        category: AppUiCategory,
        media: DefaultMediaApp? = null,
        navigation: DefaultNavigationApp? = null,
        voiceHint: String
    ) {
        AndroidAppCatalog.packagesFor(target).forEach { packageName ->
            add(
                LauncherAppEntry(
                    packageName = packageName,
                    label = label,
                    category = category,
                    defaultMedia = media,
                    defaultNavigation = navigation,
                    voiceHint = voiceHint
                )
            )
        }
    }
}
