package com.voicegeely.app.security

import android.content.Context

/**
 * Owner 25/10/2026: THT Voice can run without a key ("free") until the admin turns on
 * "Yêu cầu key" on the license server. The mode comes from /api/policy signed for this device
 * (same key as licenses), so an edited, replayed or another car's reply is ignored.
 * Owner 25/10/2026 (later): a key is required until a signed "free" reply for this car arrives,
 * so a car that cannot reach the server needs a key, as before 1.0.11.
 */
object LicenseAccessPolicy {
    /** A blocked device or a re-signed APK stays locked even in free mode. */
    fun allows(gate: LicenseGateState, keyRequired: Boolean): Boolean = when (gate) {
        LicenseGateState.ACTIVE -> true
        LicenseGateState.TAMPERED, LicenseGateState.REMOTE_BLOCKED -> false
        else -> !keyRequired
    }

    /**
     * Whether a key is needed now. No valid policy for this device → required. A free reply older than
     * [FREE_MAX_AGE_MS], or dated ahead of the clock, counts as required: an old "free" copied back
     * after the admin turned the key on stops working.
     */
    fun keyRequired(policy: PolicyTokenPayload?, requestCode: String, now: Long): Boolean {
        if (policy == null || policy.requestCode != requestCode) return true
        if (policy.keyRequired) return true
        return now - policy.issuedAt > FREE_MAX_AGE_MS || policy.issuedAt - now > CLOCK_SKEW_MS
    }

    const val REFRESH_INTERVAL_MS = LicenseSessionPolicy.REMOTE_REFRESH_INTERVAL_MS
    const val FREE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val CLOCK_SKEW_MS = 10L * 60L * 1000L

    fun refreshDue(checkedAt: Long, now: Long): Boolean =
        checkedAt <= 0L || now < checkedAt || now - checkedAt >= REFRESH_INTERVAL_MS
}

/** Last signed /api/policy reply. Editing it breaks the signature, so it is kept as plain text. */
class LicenseModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tht_license_policy", Context.MODE_PRIVATE)

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val checkedAt: Long get() = prefs.getLong(KEY_CHECKED_AT, 0L)

    fun save(token: String, now: Long) {
        prefs.edit().putString(KEY_TOKEN, token).putLong(KEY_CHECKED_AT, now).apply()
    }

    private companion object {
        const val KEY_TOKEN = "policy_token"
        const val KEY_CHECKED_AT = "checked_at"
    }
}
