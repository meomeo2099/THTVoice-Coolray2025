package com.voicegeely.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.voicegeely.app.voice.safety.ParkedAction
import com.voicegeely.app.voice.safety.ParkedActionGate
import com.voicegeely.app.voice.safety.SafetyDecisionType
import java.io.File

/**
 * Opens Android's package installer only after APK verification and PARKED_ONLY safety checks.
 * No silent/root installation path is used here.
 */
class SafeUpdateInstaller(
    private val context: Context,
    private val verifier: UpdatePackageVerifier,
    private val parkedGate: ParkedActionGate,
    private val deferredStore: DeferredUpdateStore = DeferredUpdateStore(context)
) {
    fun requestInstall(apk: File, metadata: UpdateMetadata): UpdateInstallResult {
        val verification = verifier.verify(apk, metadata)
        if (!verification.verified) {
            // A deferred APK that becomes stale, corrupt, wrongly signed, or already superseded
            // must not remain in an endless resume loop.
            deferredStore.clearAndDelete(apk)
            return UpdateInstallResult(
                status = UpdateInstallStatus.VERIFICATION_FAILED,
                detail = verification.detail,
                verification = verification
            )
        }

        val safety = parkedGate.evaluate(ParkedAction.OTA_INSTALL)
        if (safety.type != SafetyDecisionType.ALLOW) {
            deferredStore.save(apk, metadata)
            return UpdateInstallResult(
                status = UpdateInstallStatus.DEFERRED_UNTIL_PARKED,
                detail = safety.reason ?: "Bản cập nhật sẽ chờ đến khi xe đỗ an toàn.",
                verification = verification
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            deferredStore.save(apk, metadata)
            return UpdateInstallResult(
                status = UpdateInstallStatus.INSTALL_PERMISSION_REQUIRED,
                detail = "Chưa có quyền cài ứng dụng từ nguồn này.",
                verification = verification
            )
        }

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            deferredStore.clear()
            UpdateInstallResult(
                status = UpdateInstallStatus.INSTALLER_LAUNCHED,
                detail = "APK đã xác minh; đã mở trình cài đặt khi xe đang đỗ.",
                verification = verification
            )
        } catch (t: Throwable) {
            deferredStore.save(apk, metadata)
            UpdateInstallResult(
                status = UpdateInstallStatus.LAUNCH_FAILED,
                detail = "Không mở được trình cài đặt: ${t.message.orEmpty()}",
                verification = verification
            )
        }
    }

    /** Re-verifies the APK and vehicle state every time a deferred install resumes. */
    fun tryResumeDeferredInstall(): UpdateInstallResult {
        val deferred = deferredStore.load()
            ?: return UpdateInstallResult(UpdateInstallStatus.NO_DEFERRED_UPDATE, "Không có bản cập nhật đang chờ cài.")
        return requestInstall(deferred.apk, deferred.metadata)
    }

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}

enum class UpdateInstallStatus {
    INSTALLER_LAUNCHED,
    DEFERRED_UNTIL_PARKED,
    INSTALL_PERMISSION_REQUIRED,
    VERIFICATION_FAILED,
    LAUNCH_FAILED,
    NO_DEFERRED_UPDATE
}

data class UpdateInstallResult(
    val status: UpdateInstallStatus,
    val detail: String,
    val verification: UpdateVerificationResult? = null
)
