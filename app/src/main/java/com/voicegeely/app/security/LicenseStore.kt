package com.voicegeely.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Secure local storage for the activation record. */
class LicenseStore(context: Context) {
    data class Record(
        val requestCode: String,
        val activationCode: String,
        val expiresAt: Long,
        val activatedAt: Long,
        val lastSeenAt: Long,
        val remoteBlocked: Boolean = false,
        val remoteCheckedAt: Long = 0L,
        val licenseType: LicenseType = LicenseType.STANDARD,
        /** SHA-256 of VIN + request code at activation; empty when no VIN was readable. */
        val vinHash: String = "",
        /** Signed license from the new server (empty for a record from the old server). */
        val token: String = ""
    )

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(record: Record) {
        val payload = JSONObject().apply {
            put("request_code", record.requestCode)
            put("activation_code", record.activationCode)
            put("expires_at", record.expiresAt)
            put("activated_at", record.activatedAt)
            put("last_seen_at", record.lastSeenAt)
            put("remote_blocked", record.remoteBlocked)
            put("remote_checked_at", record.remoteCheckedAt)
            put("license_type", record.licenseType.wire)
            put("vin_hash", record.vinHash)
            put("token", record.token)
        }.toString().toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload)
        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(): Record? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            val json = JSONObject(String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8))
            Record(
                requestCode = json.getString("request_code"),
                activationCode = json.getString("activation_code"),
                expiresAt = json.optLong("expires_at", 0L),
                activatedAt = json.optLong("activated_at", 0L),
                lastSeenAt = json.optLong("last_seen_at", 0L),
                remoteBlocked = json.optBoolean("remote_blocked", false),
                remoteCheckedAt = json.optLong("remote_checked_at", 0L),
                licenseType = LicenseType.fromWire(json.optString("license_type", "")),
                vinHash = json.optString("vin_hash", ""),
                token = json.optString("token", "")
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun clear() = prefs.edit().clear().apply()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "tht_voice_license_secure_v1"
        private const val KEY_IV = "license_iv"
        private const val KEY_DATA = "license_data"
        private const val KEY_ALIAS = "tht_voice_license_aes_v1"
    }
}
