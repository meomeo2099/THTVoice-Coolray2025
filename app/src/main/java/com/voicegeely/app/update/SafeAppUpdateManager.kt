package com.voicegeely.app.update

import android.content.Context
import android.os.Build
import com.voicegeely.app.security.LicenseManager
import com.voicegeely.app.voice.safety.ParkedActionGate
import com.voicegeely.app.voice.safety.VehicleSafetyGate

/** End-to-end update flow: check -> download -> hash -> APK verify -> parked gate -> installer. */
class SafeAppUpdateManager(
    context: Context,
    licenseManager: LicenseManager,
    safetyGate: VehicleSafetyGate
) {
    private val appContext = context.applicationContext
    private val api = UpdateApiClient(appContext, licenseManager)
    private val downloader = UpdateDownloader(appContext, licenseManager)
    private val deferredStore = DeferredUpdateStore(appContext)
    private val parkedGate = ParkedActionGate(safetyGate)
    private val otaPolicy = OtaParkedPolicy(parkedGate)
    private val installer = SafeUpdateInstaller(
        context = appContext,
        verifier = UpdatePackageVerifier(appContext),
        parkedGate = parkedGate,
        deferredStore = deferredStore
    )

    /**
     * If an already-downloaded update is waiting, it is handled first and re-verified.
     * Otherwise this checks the Worker, downloads the new APK and requests installation.
     */
    fun checkDownloadAndInstall(onDownloadProgress: (Int) -> Unit = {}): AppUpdateFlowResult {
        // No OTA while driving: not even the server check or the download.
        otaPolicy.blockedResult()?.let { return it }
        val resumed = installer.tryResumeDeferredInstall()
        if (resumed.status != UpdateInstallStatus.NO_DEFERRED_UPDATE) {
            return mapInstallResult(resumed, null)
        }

        val currentCode = currentVersionCode()
        val check = api.check(currentCode)
        if (!check.ok) return AppUpdateFlowResult(AppUpdateFlowStatus.CHECK_FAILED, check.message)
        if (!check.updateAvailable) return AppUpdateFlowResult(AppUpdateFlowStatus.UP_TO_DATE, check.message)
        val metadata = check.metadata
            ?: return AppUpdateFlowResult(AppUpdateFlowStatus.CHECK_FAILED, "Máy chủ không trả metadata cập nhật.")

        val downloaded = downloader.download(metadata, onDownloadProgress, shouldContinue = otaPolicy::mayContinueDownload)
        if (!downloaded.ok || downloaded.file == null) {
            return AppUpdateFlowResult(AppUpdateFlowStatus.DOWNLOAD_FAILED, downloaded.detail, metadata = metadata)
        }

        val install = installer.requestInstall(downloaded.file, metadata)
        return mapInstallResult(install, metadata).copy(
            changelog = check.changelog,
            forceUpdate = check.forceUpdate,
            downloadedBytes = downloaded.bytes
        )
    }

    /** Server check only (no download), for the automatic check. Null while not parked. */
    fun checkOnly(): UpdateCheckResult? {
        if (otaPolicy.blockedResult() != null) return null
        return api.check(currentVersionCode())
    }

    fun installedVersionCode(): Long = currentVersionCode()

    fun tryResumeDeferredInstall(): AppUpdateFlowResult =
        mapInstallResult(installer.tryResumeDeferredInstall(), null)

    fun unknownSourcesSettingsIntent() = installer.unknownSourcesSettingsIntent()

    private fun currentVersionCode(): Long = try {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (_: Throwable) {
        0L
    }

    private fun mapInstallResult(result: UpdateInstallResult, metadata: UpdateMetadata?): AppUpdateFlowResult {
        val status = when (result.status) {
            UpdateInstallStatus.INSTALLER_LAUNCHED -> AppUpdateFlowStatus.INSTALLER_LAUNCHED
            UpdateInstallStatus.DEFERRED_UNTIL_PARKED -> AppUpdateFlowStatus.WAITING_FOR_PARK
            UpdateInstallStatus.INSTALL_PERMISSION_REQUIRED -> AppUpdateFlowStatus.INSTALL_PERMISSION_REQUIRED
            UpdateInstallStatus.VERIFICATION_FAILED -> AppUpdateFlowStatus.VERIFICATION_FAILED
            UpdateInstallStatus.LAUNCH_FAILED -> AppUpdateFlowStatus.INSTALL_LAUNCH_FAILED
            UpdateInstallStatus.NO_DEFERRED_UPDATE -> AppUpdateFlowStatus.NO_DEFERRED_UPDATE
        }
        return AppUpdateFlowResult(status, result.detail, metadata = metadata)
    }
}

enum class AppUpdateFlowStatus {
    UP_TO_DATE,
    CHECK_FAILED,
    DOWNLOAD_FAILED,
    VERIFICATION_FAILED,
    WAITING_FOR_PARK,
    INSTALL_PERMISSION_REQUIRED,
    INSTALLER_LAUNCHED,
    INSTALL_LAUNCH_FAILED,
    NO_DEFERRED_UPDATE
}

data class AppUpdateFlowResult(
    val status: AppUpdateFlowStatus,
    val detail: String,
    val metadata: UpdateMetadata? = null,
    val changelog: String = "",
    val forceUpdate: Boolean = false,
    val downloadedBytes: Long = 0L
)
