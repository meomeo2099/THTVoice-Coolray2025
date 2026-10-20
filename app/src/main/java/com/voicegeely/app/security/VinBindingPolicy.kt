package com.voicegeely.app.security

import java.security.MessageDigest
import java.util.Locale

/** License kind issued by the activation server (`license_type`). */
enum class LicenseType(val wire: String) {
    STANDARD("standard"),
    /** VIP code: never needs the car VIN, also after VIN-required binding is switched on. */
    VIP("vip");

    companion object {
        fun fromWire(raw: String?): LicenseType =
            if (raw?.trim()?.lowercase(Locale.US) == VIP.wire) VIP else STANDARD
    }
}

/**
 * VIN binding decision. VIN is not a secret, so only a hash of VIN + device request code is kept.
 *
 * [VIN_REQUIRED_ENABLED] stays false until the car proves a stable VIN read over several cold
 * boots (docs/IHU689G_HARDWARE_VALIDATION.md §4). While false, every license passes. When true,
 * a STANDARD license must have been activated with a VIN and the current VIN must match; a VIP
 * license always passes.
 */
object VinBindingPolicy {
    const val VIN_REQUIRED_ENABLED = false

    fun bindingOk(
        vinRequired: Boolean,
        licenseType: LicenseType,
        boundVinHash: String?,
        currentVinHash: String?
    ): Boolean {
        if (!vinRequired || licenseType == LicenseType.VIP) return true
        if (boundVinHash.isNullOrBlank() || currentVinHash.isNullOrBlank()) return false
        return boundVinHash == currentVinHash
    }

    fun vinHash(vin: String, requestCode: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$vin|$requestCode".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
