package com.voicegeely.app.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPackageCatalogTest {
    @Test
    fun vietnamTikTokBuildIsTriedFirst() {
        assertEquals("com.ss.android.ugc.trill", AndroidAppCatalog.packagesFor(AppTarget.TIKTOK).first())
        assertTrue("com.zhiliaoapp.musically" in AndroidAppCatalog.packagesFor(AppTarget.TIKTOK))
    }

    @Test
    fun vietmapIsTheLiveNavigationApp() {
        assertEquals(listOf("vn.vietmap.live"), AndroidAppCatalog.packagesFor(AppTarget.VIETMAP))
    }

    @Test
    fun everyCatalogPackageIsDeclaredInManifestQueries() {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File).first { it.isFile }.readText()
        val missing = AppTarget.values().flatMap { AndroidAppCatalog.packagesFor(it) }.distinct()
            .filterNot { manifest.contains("<package android:name=\"$it\" />") }
        assertEquals("Missing <queries> entries: $missing", emptyList<String>(), missing)
    }
}
