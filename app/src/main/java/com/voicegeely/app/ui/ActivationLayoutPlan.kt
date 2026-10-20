package com.voicegeely.app.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How the activation screen follows the same [ResponsiveLayout] rules as the main app.
 * At 1920×1080 it is the approved two-column layout (300 dp car image, no scrolling).
 */
data class ActivationLayoutPlan(
    /** Hero above the activation card instead of beside it (portrait / narrow screens). */
    val stacked: Boolean,
    /** Height of the neutral car image; 0 hides it. */
    val heroImageDp: Int,
    /** Wrap the screen in a vertical scroll so the activate button is always reachable. */
    val scroll: Boolean
) {
    companion object {
        const val HERO_IMAGE_DP = 300
        private const val HERO_IMAGE_MIN_DP = 120

        fun from(spec: ResponsiveLayout.LayoutSpec): ActivationLayoutPlan {
            val stacked = spec.widthClass == ResponsiveLayout.WidthClass.COMPACT
            val image = if (stacked) 0 else max(HERO_IMAGE_MIN_DP, min(HERO_IMAGE_DP, (spec.heightDp * 0.28f).roundToInt()))
            return ActivationLayoutPlan(stacked = stacked, heroImageDp = image, scroll = stacked || spec.scrollScreens)
        }
    }
}
