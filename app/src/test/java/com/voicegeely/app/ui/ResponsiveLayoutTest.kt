package com.voicegeely.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveLayoutTest {
    @Test
    fun approvedReferenceLayoutIsUnchangedAt1920x1080() {
        assertEquals(1f, ResponsiveLayout.uiScale(1920, 1080))
        val spec = ResponsiveLayout.spec(1920, 1080)
        assertEquals(ResponsiveLayout.WidthClass.EXPANDED, spec.widthClass)
        assertEquals(232, spec.railWidthDp)
        assertFalse(spec.railIconsOnly)
        assertEquals(72, spec.railButtonHeightDp)
        assertEquals(2, spec.settingsColumns)
        assertEquals(2, spec.appColumns)
        assertFalse(spec.scrollScreens)
        assertEquals(470, spec.fitDp(470, 0.42f, 200, heightFraction = 0.5f))
    }

    @Test
    fun largerScreensNeverScaleUp() {
        assertEquals(1f, ResponsiveLayout.uiScale(2560, 1600))
        assertEquals(2, ResponsiveLayout.spec(2000, 1200).appColumns)
    }

    @Test
    fun head1280x720KeepsTwoColumnsWithoutScrolling() {
        val spec = ResponsiveLayout.spec(1280, 720)
        assertEquals(ResponsiveLayout.MIN_SCALE, ResponsiveLayout.uiScale(1280, 720))
        assertEquals(ResponsiveLayout.WidthClass.EXPANDED, spec.widthClass)
        assertEquals(2, spec.settingsColumns)
        assertFalse(spec.scrollScreens)
    }

    @Test
    fun head1024x600UsesNarrowRailAndScrolls() {
        val spec = ResponsiveLayout.spec(1024, 600)
        assertEquals(ResponsiveLayout.WidthClass.MEDIUM, spec.widthClass)
        assertEquals(190, spec.railWidthDp)
        assertFalse(spec.railIconsOnly)
        assertEquals(56, spec.railButtonHeightDp)
        assertTrue(spec.scrollScreens)
        assertEquals(2, spec.settingsColumns)
    }

    @Test
    fun ultraWide1920x720ShowsThreeAppColumns() {
        val spec = ResponsiveLayout.spec(1920, 720)
        assertEquals(ResponsiveLayout.WidthClass.EXPANDED, spec.widthClass)
        assertEquals(3, spec.appColumns)
    }

    @Test
    fun portraitIsCompactSingleColumnScrolling() {
        val spec = ResponsiveLayout.spec(1080, 1920)
        assertTrue(spec.portrait)
        assertEquals(ResponsiveLayout.WidthClass.COMPACT, spec.widthClass)
        assertTrue(spec.railIconsOnly)
        assertEquals(1, spec.settingsColumns)
        assertEquals(1, spec.appColumns)
        assertTrue(spec.scrollScreens)
    }

    @Test
    fun scaleNeverGoesBelowReadableMinimum() {
        assertEquals(ResponsiveLayout.MIN_SCALE, ResponsiveLayout.uiScale(640, 360))
        assertEquals(1f, ResponsiveLayout.uiScale(0, 0))
    }

    @Test
    fun fixedBlocksAreClampedButKeepMinimum() {
        val small = ResponsiveLayout.specForVirtual(900, 600)
        val qr = small.fitDp(470, 0.42f, 200, heightFraction = 0.5f)
        assertTrue(qr in 200..470)
        assertTrue(qr <= (small.contentWidthDp * 0.42f).toInt() || qr == 200)
        assertEquals(200, ResponsiveLayout.specForVirtual(300, 300).fitDp(470, 0.42f, 200))
    }
}
