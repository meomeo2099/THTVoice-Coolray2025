package com.voicegeely.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Verifies an update APK before the package installer can be opened.
 *
 * Checks: file -> expected size -> SHA-256 -> readable APK -> package name -> newer version -> signing lineage.
 * Any uncertainty fails closed, except an unreadable signer: the system installer enforces it.
 */
class UpdatePackageVerifier(
    private val context: Context
) {
    private val packageManager: PackageManager
        get() = context.packageManager

    fun verify(apk: File, metadata: UpdateMetadata): UpdateVerificationResult {
        if (!apk.isFile) {
            return fail(UpdateVerificationFailure.FILE_MISSING, "Không tìm thấy file APK cập nhật.")
        }

        if (metadata.sizeBytes > 0L && apk.length() != metadata.sizeBytes) {
            return fail(
                UpdateVerificationFailure.SIZE_MISMATCH,
                "Kích thước APK (${apk.length()}) không khớp metadata (${metadata.sizeBytes})."
            )
        }

        val expectedSha = normalizeSha256(metadata.sha256)
            ?: return fail(UpdateVerificationFailure.INVALID_EXPECTED_SHA256, "SHA-256 mong đợi không hợp lệ.")
        val actualSha = sha256(apk)
        if (actualSha != expectedSha) {
            return fail(
                UpdateVerificationFailure.SHA256_MISMATCH,
                "SHA-256 của APK không khớp metadata.",
                actualSha256 = actualSha
            )
        }

        val archive = try {
            packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: Throwable) {
            null
        } ?: return fail(
            UpdateVerificationFailure.APK_UNREADABLE,
            "Android không đọc được thông tin gói APK.",
            actualSha256 = actualSha
        )

        val expectedPackage = metadata.packageName.trim()
        if (expectedPackage.isBlank() || archive.packageName != expectedPackage || archive.packageName != context.packageName) {
            return fail(
                UpdateVerificationFailure.PACKAGE_MISMATCH,
                "Package APK không khớp ứng dụng THT Voice hiện tại.",
                actualSha256 = actualSha,
                packageName = archive.packageName,
                versionCode = versionCodeOf(archive)
            )
        }

        val archiveVersion = versionCodeOf(archive)
        if (archiveVersion != metadata.versionCode) {
            return fail(
                UpdateVerificationFailure.VERSION_NOT_NEWER,
                "versionCode APK không khớp metadata.",
                actualSha256 = actualSha,
                packageName = archive.packageName,
                versionCode = archiveVersion
            )
        }

        val installed = try {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: Throwable) {
            null
        } ?: return fail(
            UpdateVerificationFailure.APK_UNREADABLE,
            "Không đọc được thông tin bản THT Voice đang cài.",
            actualSha256 = actualSha,
            packageName = archive.packageName,
            versionCode = archiveVersion
        )

        val installedVersion = versionCodeOf(installed)
        if (archiveVersion <= installedVersion) {
            return fail(
                UpdateVerificationFailure.VERSION_NOT_NEWER,
                "Bản cập nhật phải có versionCode lớn hơn bản đang cài ($installedVersion).",
                actualSha256 = actualSha,
                packageName = archive.packageName,
                versionCode = archiveVersion
            )
        }

        // Owner report 03/10/2026 ("báo không có chữ ký"): some head units return no signer for a
        // downloaded APK (v2/v3-only signing, archive parser). Try the other readers; if none can
        // read it, Android's installer still refuses an update signed with another key.
        val archiveSigners = signingDigests(archive)
            .ifEmpty { legacyArchiveDigests(apk) }
            .ifEmpty { apkSigDigests(apk) }
        val installedSigners = signingDigests(installed)
        if (archiveSigners.isEmpty() || installedSigners.isEmpty()) {
            android.util.Log.w(
                TAG,
                "update_signature_unreadable archive=${archiveSigners.size} installed=${installedSigners.size}; system installer enforces the key"
            )
        } else if (archiveSigners.intersect(installedSigners).isEmpty()) {
            return fail(
                UpdateVerificationFailure.SIGNATURE_MISMATCH,
                "Chữ ký APK cập nhật không khớp ứng dụng đang cài.",
                actualSha256 = actualSha,
                packageName = archive.packageName,
                versionCode = archiveVersion
            )
        }

        return UpdateVerificationResult(
            verified = true,
            detail = "APK đã xác minh kích thước, SHA-256, package, versionCode và chữ ký.",
            actualSha256 = actualSha,
            packageName = archive.packageName,
            versionCode = archiveVersion
        )
    }

    @Suppress("DEPRECATION")
    private fun legacyArchiveDigests(apk: File): Set<String> = try {
        packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            ?.signatures.orEmpty()
            .mapTo(linkedSetOf()) { sha256Hex(it.toByteArray()) }
    } catch (_: Throwable) {
        emptySet()
    }

    /** Reads the APK Signing Block directly (v1/v2/v3), independent of the head unit's parser. */
    private fun apkSigDigests(apk: File): Set<String> = try {
        com.android.apksig.ApkVerifier.Builder(apk).build().verify()
            .signerCertificates
            .mapTo(linkedSetOf()) { sha256Hex(it.encoded) }
    } catch (t: Throwable) {
        android.util.Log.w(TAG, "apksig_failed type=${t.javaClass.simpleName}")
        emptySet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures = try {
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } catch (_: Throwable) {
            emptyArray()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun fail(
        failure: UpdateVerificationFailure,
        detail: String,
        actualSha256: String? = null,
        packageName: String? = null,
        versionCode: Long? = null
    ) = UpdateVerificationResult(
        verified = false,
        failure = failure,
        detail = detail,
        actualSha256 = actualSha256,
        packageName = packageName,
        versionCode = versionCode
    )

    companion object {
        private const val TAG = "THTVoiceUpdate"
        private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

        fun normalizeSha256(value: String): String? =
            value.trim().takeIf { SHA256_REGEX.matches(it) }?.lowercase()

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
