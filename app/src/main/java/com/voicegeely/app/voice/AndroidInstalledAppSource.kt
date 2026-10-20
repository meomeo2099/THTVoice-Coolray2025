package com.voicegeely.app.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Launchable apps installed on the head unit (same source as the Apps screen). */
class AndroidInstalledAppSource(context: Context) {
    private val appContext = context.applicationContext

    fun apps(): List<InstalledAppMatcher.InstalledApp> {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }
            .getOrDefault(emptyList())
        return activities.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == appContext.packageName) return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString().trim() }.getOrNull().orEmpty()
            InstalledAppMatcher.InstalledApp(label.ifBlank { packageName }, packageName)
        }.distinctBy { it.packageName }
    }
}
