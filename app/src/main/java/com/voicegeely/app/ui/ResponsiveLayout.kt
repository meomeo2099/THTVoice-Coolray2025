package com.voicegeely.app.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Screen-size adaptation for the approved 1920×1080 landscape design.
 *
 * Two steps:
 * 1. [uiScale] shrinks the whole UI (dp and sp) toward the reference size, but never below
 *    [MIN_SCALE] so text stays readable on small head units.
 * 2. [LayoutSpec] describes how the remaining gap is absorbed by reflow: narrower or
 *    icon-only navigation rail, one-column settings, app grid columns, scrolling screens and
 *    clamped fixed-size blocks.
 *
 * At the reference size (or larger) the scale is 1 and the spec reproduces the approved
 * layout exactly: 232 dp rail, two-column settings, two app columns, no scrolling.
 */
object ResponsiveLayout {
    const val REFERENCE_WIDTH_DP = 1920
    const val REFERENCE_HEIGHT_DP = 1080
    const val MIN_SCALE = 0.72f

    /**
     * Ultra-wide head units (aspect >= [WIDE_ASPECT], e.g. Coolray 2025: 1920×720 screen, ~1760×720
     * app window next to the system dock) have plenty of width but little height. Scaling them to
     * the 1080 dp reference height shrank text to the 0.72 floor; they use a lower reference height
     * so text stays ~0.85× and the extra width is absorbed by the wide layout instead.
     */
    const val WIDE_ASPECT = 2.0f
    const val WIDE_REFERENCE_HEIGHT_DP = 850

    enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

    data class LayoutSpec(
        /** Virtual content size after [uiScale] is applied. */
        val widthDp: Int,
        val heightDp: Int,
        val widthClass: WidthClass,
        val railWidthDp: Int,
        val railIconsOnly: Boolean,
        val railButtonHeightDp: Int,
        val settingsColumns: Int,
        val appColumns: Int,
        val scrollScreens: Boolean,
        val portrait: Boolean
    ) {
        /** Content width next to the rail, minus the outer paddings used by MainActivity. */
        val contentWidthDp: Int get() = max(widthDp - railWidthDp - 44, 240)

        /**
         * A fixed design size (e.g. the 470 dp QR, 410 dp card) limited to a fraction of the
         * available content width/height, never below [minDp].
         */
        fun fitDp(preferredDp: Int, widthFraction: Float, minDp: Int = 0, heightFraction: Float = 1f): Int {
            val byWidth = (contentWidthDp * widthFraction).roundToInt()
            val byHeight = (heightDp * heightFraction).roundToInt()
            return max(min(preferredDp, min(byWidth, byHeight)), minDp)
        }
    }

    /** UI scale for a window of [widthDp]×[heightDp] measured in the device's own density. */
    fun uiScale(widthDp: Int, heightDp: Int): Float {
        if (widthDp <= 0 || heightDp <= 0) return 1f
        val landscape = widthDp >= heightDp
        val refW = if (landscape) REFERENCE_WIDTH_DP else REFERENCE_HEIGHT_DP
        val ultraWide = landscape && widthDp.toFloat() / heightDp >= WIDE_ASPECT
        val refH = when {
            !landscape -> REFERENCE_WIDTH_DP
            ultraWide -> WIDE_REFERENCE_HEIGHT_DP
            else -> REFERENCE_HEIGHT_DP
        }
        val raw = min(widthDp.toFloat() / refW, heightDp.toFloat() / refH)
        return raw.coerceIn(MIN_SCALE, 1f)
    }

    /** Layout decisions for the virtual size (device dp divided by [uiScale]). */
    fun spec(widthDp: Int, heightDp: Int): LayoutSpec {
        val scale = uiScale(widthDp, heightDp)
        return specForVirtual((widthDp / scale).roundToInt(), (heightDp / scale).roundToInt())
    }

    /** Layout decisions when [w]×[h] is already the virtual (scaled) size. */
    fun specForVirtual(w: Int, h: Int): LayoutSpec {
        val portrait = h > w
        val widthClass = when {
            portrait || w < 1100 -> WidthClass.COMPACT
            w < 1500 -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }
        val railWidth = when (widthClass) {
            WidthClass.EXPANDED -> 232
            WidthClass.MEDIUM -> 190
            WidthClass.COMPACT -> 92
        }
        val shortHeight = h < 1000
        val aspect = w.toFloat() / h
        val appColumns = when {
            widthClass == WidthClass.COMPACT -> 1
            widthClass == WidthClass.EXPANDED && aspect >= 2.2f -> 3
            else -> 2
        }
        return LayoutSpec(
            widthDp = w,
            heightDp = h,
            widthClass = widthClass,
            railWidthDp = railWidth,
            railIconsOnly = widthClass == WidthClass.COMPACT,
            railButtonHeightDp = if (h < 900) 56 else 72,
            settingsColumns = if (widthClass == WidthClass.COMPACT) 1 else 2,
            appColumns = appColumns,
            scrollScreens = shortHeight || widthClass == WidthClass.COMPACT,
            portrait = portrait
        )
    }
}
