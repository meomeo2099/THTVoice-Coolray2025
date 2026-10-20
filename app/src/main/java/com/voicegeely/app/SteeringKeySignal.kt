package com.voicegeely.app

/**
 * [REF-02 / S02] Steering-wheel voice key sources other than the classic ECARX broadcast.
 * Data: docs/reference/ihu629g-20261011 (REFERENCE / UNVERIFIED, hardware NOT_TESTED).
 */
object VoiceKeyActions {
    const val ECARX = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"
    /** Same key, different letter case ("actiOn") seen in R02; harmless to also listen for. */
    const val ECARX_CASE_VARIANT = "ecarx.intent.actiOn.ECARX_KEY_RVOICEASSIST_EVENT"
    val ALL: Set<String> = setOf(ECARX, ECARX_CASE_VARIANT)

    fun isVoiceKey(action: String?): Boolean = action != null && action in ALL
}
