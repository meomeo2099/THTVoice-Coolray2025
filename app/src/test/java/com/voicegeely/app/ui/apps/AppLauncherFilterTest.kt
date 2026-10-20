package com.voicegeely.app.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLauncherFilterTest {
    private val apps = listOf(
        LauncherAppEntry("youtube", "YouTube", AppUiCategory.MEDIA),
        LauncherAppEntry("maps", "Google Maps", AppUiCategory.MAP),
        LauncherAppEntry("files", "Files", AppUiCategory.OTHER)
    )

    @Test
    fun categoryFilterKeepsOnlyRequestedGroup() {
        val result = AppLauncherFilter.filter(apps, AppUiCategory.MEDIA, "", emptySet())
        assertEquals(listOf("youtube"), result.map { it.packageName })
    }

    @Test
    fun favoritesFilterUsesPersistedPackageSet() {
        val result = AppLauncherFilter.filter(apps, AppUiCategory.FAVORITES, "", setOf("maps"))
        assertEquals(listOf("maps"), result.map { it.packageName })
    }

    @Test
    fun searchMatchesLabelOrPackageIgnoringCase() {
        assertEquals(listOf("maps"), AppLauncherFilter.filter(apps, AppUiCategory.ALL, "GOOGLE", emptySet()).map { it.packageName })
        assertEquals(listOf("files"), AppLauncherFilter.filter(apps, AppUiCategory.ALL, "FILES", emptySet()).map { it.packageName })
    }

    @Test
    fun favoritesSortAheadOfOtherApps() {
        val result = AppLauncherFilter.filter(apps, AppUiCategory.ALL, "", setOf("youtube"))
        assertEquals("youtube", result.first().packageName)
    }
}
