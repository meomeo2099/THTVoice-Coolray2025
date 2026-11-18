package com.voicegeely.app.kws

/**
 * Wake-word sensitivity (owner request 08/11/2026: a quiet "Hey Ge-ly" was missed).
 * LOW keeps the pre-1.0.15 tuning. NORMAL/HIGH loosen the pre-KWS level gate and the
 * keyword spotter; the Vietnamese ASR confirmation ([WakeVerifier]) filters the extra
 * false triggers. Values are REFERENCE starting points, to be tuned on the car.
 */
enum class WakeSensitivity(
    /** Speech must be this many dB above the tracked noise floor to reach the spotter. */
    val gateMarginDb: Double,
    val maxActivePaths: Int,
    val keywordsScore: Float,
    val keywordsThreshold: Float,
    /** Confirmation heard nothing intelligible: wake anyway (quiet voice) or drop it. */
    val acceptBlankConfirmation: Boolean
) {
    LOW(9.0, 4, 1.5f, 0.25f, false),
    NORMAL(6.0, 8, 2.5f, 0.20f, false),
    HIGH(4.0, 8, 2.5f, 0.15f, true)
}
