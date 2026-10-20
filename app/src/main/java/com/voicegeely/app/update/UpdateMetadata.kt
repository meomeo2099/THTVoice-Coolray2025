package com.voicegeely.app.update

/** Metadata required before an APK is allowed to reach the installer. */
data class UpdateMetadata(
    val packageName: String,
    val versionCode: Long,
    val versionName: String? = null,
    val sha256: String,
    val downloadUrl: String? = null,
    /** Exact GitHub Release asset size reported by the trusted Worker; 0 means unavailable. */
    val sizeBytes: Long = 0L
)

enum class UpdateVerificationFailure {
    FILE_MISSING,
    INVALID_EXPECTED_SHA256,
    SIZE_MISMATCH,
    SHA256_MISMATCH,
    APK_UNREADABLE,
    PACKAGE_MISMATCH,
    VERSION_NOT_NEWER,
    SIGNATURE_UNAVAILABLE,
    SIGNATURE_MISMATCH
}

data class UpdateVerificationResult(
    val verified: Boolean,
    val failure: UpdateVerificationFailure? = null,
    val detail: String,
    val actualSha256: String? = null,
    val packageName: String? = null,
    val versionCode: Long? = null
)
