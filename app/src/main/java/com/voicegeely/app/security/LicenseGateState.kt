package com.voicegeely.app.security

enum class LicenseGateState {
    UNLICENSED,
    ACTIVE,
    EXPIRED,
    REMOTE_BLOCKED,
    BINDING_MISMATCH,
    CLOCK_INVALID,
    /** The APK is not signed with the official certificate (repacked / re-signed copy). */
    TAMPERED
}

object LicenseGateEvaluator {
    fun evaluate(
        hasRecord: Boolean,
        requestMatches: Boolean,
        vinBindingOk: Boolean = true,
        activationPresent: Boolean,
        expiresAt: Long,
        lastSeenAt: Long,
        remoteBlocked: Boolean,
        now: Long,
        rollbackToleranceMs: Long,
        genuineApp: Boolean = true
    ): LicenseGateState {
        if (!genuineApp) return LicenseGateState.TAMPERED
        if (!hasRecord || !activationPresent) return LicenseGateState.UNLICENSED
        if (!requestMatches || !vinBindingOk) return LicenseGateState.BINDING_MISMATCH
        if (remoteBlocked) return LicenseGateState.REMOTE_BLOCKED
        if (expiresAt > 0L && now > expiresAt) return LicenseGateState.EXPIRED
        if (lastSeenAt > 0L && now + rollbackToleranceMs < lastSeenAt) return LicenseGateState.CLOCK_INVALID
        return LicenseGateState.ACTIVE
    }
}
