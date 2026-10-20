package com.voicegeely.app.ui

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import kotlin.math.roundToInt

/** Android glue for [ResponsiveLayout]: density override for activities and spec lookup. */
object ResponsiveContext {
    /**
     * Returns a context whose density (and therefore every dp/sp size) is reduced by
     * [ResponsiveLayout.uiScale]. Use from Activity.attachBaseContext.
     */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        val scale = ResponsiveLayout.uiScale(config.screenWidthDp, config.screenHeightDp)
        if (scale >= 0.999f || config.densityDpi <= 0) return base
        config.densityDpi = (config.densityDpi * scale).roundToInt().coerceAtLeast(DisplayMetrics.DENSITY_LOW)
        config.screenWidthDp = (config.screenWidthDp / scale).roundToInt()
        config.screenHeightDp = (config.screenHeightDp / scale).roundToInt()
        config.smallestScreenWidthDp = (config.smallestScreenWidthDp / scale).roundToInt()
        return base.createConfigurationContext(config)
    }

    /** Layout spec for a context that was already wrapped (its configuration holds virtual dp). */
    fun spec(context: Context): ResponsiveLayout.LayoutSpec {
        val config = context.resources.configuration
        val widthDp = config.screenWidthDp.takeIf { it > 0 } ?: ResponsiveLayout.REFERENCE_WIDTH_DP
        val heightDp = config.screenHeightDp.takeIf { it > 0 } ?: ResponsiveLayout.REFERENCE_HEIGHT_DP
        // The context is already scaled, so its dp values are virtual; spec() must not scale again.
        return ResponsiveLayout.specForVirtual(widthDp, heightDp)
    }
}
