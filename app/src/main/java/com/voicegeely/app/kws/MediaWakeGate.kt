package com.voicegeely.app.kws

/**
 * Wake rules while media plays through the cabin speakers (R03 MediaWakeGate): music and podcasts
 * trigger the loosened spotter often, so under media
 * - the Vietnamese confirmation must hear the brand (a blank transcript is never accepted),
 * - the level gate wants a little more margin,
 * - after a rejected check the spotter rests for [REFRACTORY_AFTER_REJECT_MS] so it is not
 *   re-checked every 2-3 s,
 * - the "second chance" lenient window is not opened.
 * Without media every rule returns today's value. Pure Kotlin.
 */
object MediaWakeGate {
    const val REFRACTORY_AFTER_REJECT_MS = 2_000L
    const val EXTRA_MARGIN_DB = 3.0

    fun acceptBlank(sensitivity: WakeSensitivity, media: Boolean): Boolean = sensitivity.acceptBlankConfirmation && !media

    fun gateMarginDb(sensitivity: WakeSensitivity, media: Boolean): Double =
        sensitivity.gateMarginDb + if (media) EXTRA_MARGIN_DB else 0.0

    fun refractoryAfterRejectMs(media: Boolean): Long = if (media) REFRACTORY_AFTER_REJECT_MS else 0L

    fun allowSecondChance(media: Boolean): Boolean = !media
}
