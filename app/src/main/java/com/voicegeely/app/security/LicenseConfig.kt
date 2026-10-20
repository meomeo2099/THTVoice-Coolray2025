package com.voicegeely.app.security

import com.tht.voice.BuildConfig
import java.security.PublicKey

/**
 * License server of this build, from config/license-server.properties (or the
 * THTVOICE_LICENSE_SERVER_URL / THTVOICE_LICENSE_PUBLIC_KEYS build variables).
 *
 * Nothing here is secret: the signing private key, VIN key, admin password and GitHub token
 * live only in the Worker's Cloudflare secrets (worker/README.md).
 */
object LicenseConfig {
    val WORKER_URL: String = BuildConfig.LICENSE_SERVER_URL

    /** Public keys for signed licenses; empty means the build talks to the old server. */
    val PUBLIC_KEYS: Map<String, PublicKey> by lazy { LicenseToken.parseKeys(BuildConfig.LICENSE_PUBLIC_KEYS) }

    val signedLicenses: Boolean get() = PUBLIC_KEYS.isNotEmpty()

    fun isConfigured(): Boolean =
        WORKER_URL.startsWith("https://") && !WORKER_URL.contains("YOUR-WORKER", ignoreCase = true)

    fun endpoint(path: String): String = WORKER_URL.trimEnd('/') + "/" + path.trimStart('/')
}
