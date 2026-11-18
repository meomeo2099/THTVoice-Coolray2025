package com.voicegeely.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.voicegeely.app.security.PlatformVinProviders
import com.voicegeely.app.security.LicenseCodes
import com.voicegeely.app.security.LicenseGateState
import com.voicegeely.app.security.LicenseManager
import com.voicegeely.app.security.VinReadResult
import com.voicegeely.app.ui.ActivationScreenBinding
import com.voicegeely.app.ui.ActivationScreenView
import com.voicegeely.app.ui.ActivationUiState

class ActivationActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.voicegeely.app.ui.ResponsiveContext.wrap(newBase))
    }

    private lateinit var licenseManager: LicenseManager
    private var binding: ActivationScreenBinding? = null
    private var openingMain = false
    private val forVoiceDownload: Boolean
        get() = !com.tht.voice.BuildConfig.LICENSE_GATE_ENABLED && intent.getBooleanExtra(EXTRA_DOWNLOAD_LICENSE, false)

    private fun activationState(): LicenseGateState =
        if (forVoiceDownload && licenseManager.authHeaders().isEmpty()) LicenseGateState.UNLICENSED
        else licenseManager.gateState()

    /** Active key, or free mode on the license server ("Yêu cầu key" off). Voice-pack download still needs a key. */
    private fun unlocked(): Boolean =
        if (forVoiceDownload) activationState() == LicenseGateState.ACTIVE else licenseManager.isLicensed()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        licenseManager = LicenseManager(this)
        Log.i(TAG, "activation_activity_created")
        enforceGate()
    }

    override fun onResume() {
        super.onResume()
        if (::licenseManager.isInitialized && unlocked()) {
            Log.i(TAG, "activation_resume_license_active")
            openMainUi()
        }
    }

    private fun enforceGate() {
        val gateState = activationState()
        Log.i(TAG, "gate_state=$gateState")
        if (unlocked()) {
            openMainUi()
            return
        }

        // Defense in depth: an invalid license must never leave an already-running Voice core alive.
        if (!forVoiceDownload) {
            val serviceStopped = runCatching {
                stopService(Intent(this, VoiceAssistantService::class.java))
            }.getOrDefault(false)
            Log.i(TAG, "invalid_license_voice_service_stop_requested result=$serviceStopped")
        }

        val vinLabel = when (val vin = PlatformVinProviders.create(this).readVin()) {
            is VinReadResult.Available -> {
                Log.i(TAG, "vin_telemetry=AVAILABLE source=${vin.source}")
                "${vin.vin} (${vin.source.label})"
            }
            is VinReadResult.Unavailable -> {
                Log.i(TAG, "vin_telemetry=UNAVAILABLE reason=${vin.reason}")
                "Chưa khả dụng"
            }
        }

        val screen = ActivationScreenView.build(
            context = this,
            requestCode = licenseManager.formattedRequestCode(),
            deviceLabel = "Thiết bị Android • SDK ${android.os.Build.VERSION.SDK_INT}",
            vinLabel = vinLabel,
            initialState = ActivationScreenBinding.fromGateState(gateState),
            onActivate = { code -> verifyActivation(code) },
            onRestore = { restoreLicense() },
            onExit = {
                Log.i(TAG, "activation_exit_requested")
                if (forVoiceDownload) finish() else finishAndRemoveTask()
            }
        )
        binding = screen
        setContentView(screen.root)
        // The server may be in free mode ("Yêu cầu key" off): ask once, quietly, and open if so.
        if (!forVoiceDownload && (gateState == LicenseGateState.UNLICENSED || gateState == LicenseGateState.EXPIRED)) {
            Thread {
                val checked = licenseManager.refreshLicenseMode()
                Log.i(TAG, "activation_license_mode_checked ok=$checked")
                if (checked && licenseManager.isLicensed()) runOnUiThread { openMainUi() }
            }.start()
        }
    }

    private fun verifyActivation(rawCode: String) {
        when (LicenseCodes.parse(rawCode)) {
            LicenseCodes.Parsed.Invalid -> {
                Log.i(TAG, "activation_rejected_local reason=INVALID_FORMAT")
                binding?.render(ActivationUiState.INVALID_CODE, "Nhập đủ 16 ký tự (0-9, A-F) hoặc mã 20 số; mã mở khóa gồm 8 số.")
                return
            }
            LicenseCodes.Parsed.Typo -> {
                Log.i(TAG, "activation_rejected_local reason=CHECK_DIGIT")
                binding?.render(ActivationUiState.INVALID_CODE, "Mã có một chữ số bị sai hoặc bị đảo chỗ. Kiểm tra lại từng nhóm 4 số.")
                return
            }
            else -> Unit
        }
        Log.i(TAG, "activation_verify_started")
        binding?.render(ActivationUiState.VERIFYING)
        runLicenseCall { licenseManager.activate(rawCode) }
    }

    /** "Khôi phục bản quyền": same device after reinstalling, or a renewal made by the seller. */
    private fun restoreLicense() {
        Log.i(TAG, "activation_restore_started")
        binding?.render(ActivationUiState.VERIFYING, "Đang tìm bản quyền của thiết bị này...")
        runLicenseCall(ActivationUiState.NOTICE) { licenseManager.restore() }
    }

    private fun runLicenseCall(failState: ActivationUiState = ActivationUiState.INVALID_CODE, block: () -> LicenseManager.ActivationResult) {
        Thread {
            val result = block()
            val finalGate = activationState()
            Log.i(TAG, "activation_call_finished ok=${result.ok} locked=${result.locked} unlocked=${result.unlocked} gate=$finalGate")
            runOnUiThread {
                when {
                    finalGate == LicenseGateState.ACTIVE -> {
                        binding?.render(ActivationUiState.ACTIVE, result.message)
                        binding?.root?.postDelayed({ openMainUi() }, 350L)
                    }
                    result.locked -> binding?.render(ActivationUiState.LOCKED, result.message)
                    result.unlocked -> {
                        binding?.clearCode()
                        binding?.render(ActivationUiState.NOTICE, result.message)
                    }
                    finalGate != LicenseGateState.UNLICENSED ->
                        binding?.render(ActivationScreenBinding.fromGateState(finalGate), result.message)
                    else -> binding?.render(failState, result.message)
                }
            }
        }.start()
    }

    private fun openMainUi() {
        if (forVoiceDownload) {
            if (activationState() == LicenseGateState.ACTIVE) finish()
            return
        }
        if (openingMain) {
            Log.i(TAG, "open_main_skipped reason=ALREADY_OPENING")
            return
        }
        if (!licenseManager.isLicensed()) {
            Log.w(TAG, "open_main_blocked reason=LICENSE_INACTIVE")
            return
        }
        openingMain = true
        Log.i(TAG, "open_main_allowed")
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    companion object {
        const val EXTRA_DOWNLOAD_LICENSE = "download_license"
        private const val TAG = "THTVoiceSecurity"
    }
}
