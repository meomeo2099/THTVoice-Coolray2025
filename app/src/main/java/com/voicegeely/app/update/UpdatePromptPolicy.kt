package com.voicegeely.app.update

/**
 * Automatic update check and "required update" rules (owner request 01/10/2026). Pure logic.
 *
 * - The app checks by itself when it is opened, at most every [AUTO_CHECK_INTERVAL_MS], and only
 *   while parked (the OTA parked gate still applies to the check itself).
 * - A release published with forceUpdate makes that version required: while parked the
 *   assistant is held until the update is installed; while driving nothing is blocked.
 */
object UpdatePromptPolicy {
    const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    fun shouldAutoCheck(now: Long, lastCheckAt: Long): Boolean =
        lastCheckAt <= 0L || now < lastCheckAt || now - lastCheckAt >= AUTO_CHECK_INTERVAL_MS

    fun isRequired(requiredVersionCode: Long, currentVersionCode: Long): Boolean =
        requiredVersionCode > currentVersionCode

    fun blockAssistant(updateRequired: Boolean, parked: Boolean): Boolean = updateRequired && parked
}
