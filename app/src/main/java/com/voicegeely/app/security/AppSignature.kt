package com.voicegeely.app.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.tht.voice.BuildConfig
import java.security.MessageDigest

/** SHA-256 of the certificate(s) the running APK is signed with, and the verdict for this build. */
object AppSignature {
    private const val TAG = "THTSignature"
    @Volatile private var cached: List<String>? = null

    fun enforced(): Boolean = SignaturePolicy.enforced(BuildConfig.RELEASE_CERT_SHA256)

    fun isGenuine(context: Context): Boolean {
        if (!enforced()) return true
        val ok = SignaturePolicy.isGenuine(BuildConfig.RELEASE_CERT_SHA256, certificates(context))
        if (!ok) Log.w(TAG, "signature_mismatch")
        return ok
    }

    /** First certificate hash, sent to the license server so it can refuse re-signed copies. */
    fun primary(context: Context): String = certificates(context).firstOrNull().orEmpty()

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    fun certificates(context: Context): List<String> {
        cached?.let { return it }
        val pm = context.packageManager
        val name = context.packageName
        val raw: List<ByteArray> = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                when {
                    info == null -> emptyList()
                    info.hasMultipleSigners() -> info.apkContentsSigners.map { it.toByteArray() }
                    else -> info.signingCertificateHistory.map { it.toByteArray() }
                }
            } else {
                pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() }.orEmpty()
            }
        }.getOrElse {
            Log.w(TAG, "signature_read_failed type=${it.javaClass.simpleName}")
            emptyList()
        }
        val hashes = raw.map { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
        cached = hashes
        return hashes
    }
}
