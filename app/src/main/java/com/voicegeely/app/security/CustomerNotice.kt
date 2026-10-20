package com.voicegeely.app.security

import com.voicegeely.app.voice.safety.VehicleMotionState

/** "Thông báo cho khách" sent by the license server with a successful license check. */
data class CustomerNotice(val id: String, val text: String, val until: Long)

object CustomerNoticePolicy {
    const val MAX_TEXT = 500

    fun of(id: String?, text: String?, until: Long): CustomerNotice? {
        val cleanId = id?.trim().orEmpty()
        val cleanText = text?.trim().orEmpty().take(MAX_TEXT)
        if (cleanId.isEmpty() || cleanText.isEmpty()) return null
        return CustomerNotice(cleanId, cleanText, until.coerceAtLeast(0L))
    }

    /**
     * Show once, before it expires, and never while the car is moving or out of P. When speed/gear
     * cannot be read (phone, service not ready) it is still only shown inside the app's own screen.
     */
    fun shouldShow(notice: CustomerNotice?, seenId: String?, now: Long, motion: VehicleMotionState): Boolean {
        if (notice == null || notice.id == seenId) return false
        if (notice.until > 0 && now > notice.until) return false
        if (motion.isMovingOrNotParked) return false
        return true
    }
}
