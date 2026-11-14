package com.voicegeely.app.kws

/** Internal wake tuning; preserves the shipped default spotter parameters. */
enum class WakeSensitivity(val maxActivePaths: Int, val keywordsScore: Float, val keywordsThreshold: Float,
    val gateMarginDb: Double, val acceptBlankConfirmation: Boolean) {
    LOW(4, 1.5f, 0.25f, 9.0, false), NORMAL(4, 1.5f, 0.25f, 9.0, false), HIGH(8, 2.0f, 0.20f, 6.0, true)
}
