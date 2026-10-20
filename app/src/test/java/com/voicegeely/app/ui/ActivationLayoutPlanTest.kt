package com.voicegeely.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationLayoutPlanTest {
    @Test
    fun approvedLayoutUnchangedAt1920x1080() {
        val plan = ActivationLayoutPlan.from(ResponsiveLayout.spec(1920, 1080))
        assertFalse(plan.stacked)
        assertEquals(ActivationLayoutPlan.HERO_IMAGE_DP, plan.heroImageDp)
        assertFalse(plan.scroll)
    }

    @Test
    fun smallHeadUnitKeepsTwoColumnsButShrinksImageAndScrolls() {
        val plan = ActivationLayoutPlan.from(ResponsiveLayout.spec(1024, 600))
        assertFalse(plan.stacked)
        assertTrue(plan.heroImageDp in 120 until ActivationLayoutPlan.HERO_IMAGE_DP)
        assertTrue(plan.scroll)
    }

    @Test
    fun portraitStacksHidesImageAndScrolls() {
        val plan = ActivationLayoutPlan.from(ResponsiveLayout.spec(1080, 1920))
        assertTrue(plan.stacked)
        assertEquals(0, plan.heroImageDp)
        assertTrue(plan.scroll)
    }
}
