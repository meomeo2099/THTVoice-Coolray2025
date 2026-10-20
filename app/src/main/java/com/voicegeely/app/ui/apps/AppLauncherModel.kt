package com.voicegeely.app.ui.apps

import com.voicegeely.app.settings.DefaultMediaApp
import com.voicegeely.app.settings.DefaultNavigationApp

enum class AppUiCategory {
    ALL,
    FAVORITES,
    MEDIA,
    MAP,
    OTHER
}

data class LauncherAppEntry(
    val packageName: String,
    val label: String,
    val category: AppUiCategory,
    val defaultMedia: DefaultMediaApp? = null,
    val defaultNavigation: DefaultNavigationApp? = null,
    val voiceHint: String = ""
)

object AppLauncherFilter {
    fun filter(
        apps: List<LauncherAppEntry>,
        category: AppUiCategory,
        query: String,
        favorites: Set<String>
    ): List<LauncherAppEntry> {
        val needle = query.trim().lowercase()
        return apps.asSequence()
            .filter { app ->
                when (category) {
                    AppUiCategory.ALL -> true
                    AppUiCategory.FAVORITES -> app.packageName in favorites
                    AppUiCategory.MEDIA -> app.category == AppUiCategory.MEDIA
                    AppUiCategory.MAP -> app.category == AppUiCategory.MAP
                    AppUiCategory.OTHER -> app.category == AppUiCategory.OTHER
                }
            }
            .filter { app ->
                needle.isBlank() ||
                    app.label.lowercase().contains(needle) ||
                    app.packageName.lowercase().contains(needle)
            }
            .sortedWith(
                compareByDescending<LauncherAppEntry> { it.packageName in favorites }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
            .toList()
    }
}
