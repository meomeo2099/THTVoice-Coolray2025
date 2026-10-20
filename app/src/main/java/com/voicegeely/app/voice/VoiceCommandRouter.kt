package com.voicegeely.app.voice

import com.voicegeely.app.car.EcarxFunctionPort
import com.voicegeely.app.car.VehiclePropertyAccess
import com.voicegeely.app.settings.DefaultNavigationApp
import com.voicegeely.app.voice.safety.VehicleSafetyGate

/** Single typed-command entry point for vehicle and Android command families. */
class VoiceCommandRouter(
    vehicle: VehiclePropertyAccess?,
    androidBackend: AndroidActionBackend,
    mediaResolver: MediaPreferenceResolver,
    navigationResolver: NavigationPreferenceResolver =
        NavigationPreferenceResolver { DefaultNavigationApp.AUTO },
    waitForReadback: (Long) -> Unit = { Thread.sleep(it) },
    /** Result of CoolrayHeadUnit.detect() != null; VoiceAssistantService passes the real value. */
    headUnitSupported: Boolean = true,
    /** ECARX AdaptAPI for body functions (windows, sunroof, seats, lights); null = use the router default. */
    ecarx: EcarxFunctionPort? = null
) : VoiceCommandExecutor {
    private val vehicleRouter = vehicle?.let {
        if (ecarx != null) {
            VehicleCommandRouter(it, waitForReadback, headUnitSupported = headUnitSupported || it.isMock, ecarx = ecarx)
        } else {
            VehicleCommandRouter(it, waitForReadback, headUnitSupported = headUnitSupported || it.isMock)
        }
    }

    private val displayBrightnessModeSetter: ((DisplayBrightnessMode) -> AndroidBackendResult)? =
        vehicle?.let { vehicleAccess ->
            { mode ->
                val context = AndroidRuntimeContext.getOrNull()
                if (context == null) {
                    AndroidBackendResult(
                        accepted = false,
                        confirmed = false,
                        detail = "Android context chưa sẵn sàng để đổi chế độ độ sáng."
                    )
                } else {
                    val writer = AndroidDisplayBrightnessModeWriter(
                        context = context,
                        parkedGate = VehicleSafetyGate(vehicleAccess)
                    )
                    val writerMode = when (mode) {
                        DisplayBrightnessMode.MANUAL -> DisplayBrightnessReading.Mode.MANUAL
                        DisplayBrightnessMode.AUTOMATIC -> DisplayBrightnessReading.Mode.AUTOMATIC
                    }
                    writer.set(writerMode)
                }
            }
        }

    private val androidRouter = AndroidVoiceCommandExecutor(
        backend = androidBackend,
        mediaResolver = mediaResolver,
        navigationResolver = navigationResolver,
        displayBrightnessModeSetter = displayBrightnessModeSetter
    )

    override suspend fun execute(command: VoiceCommand): CommandExecutionResult = when (command) {
        is VoiceCommand.OpenApp,
        is VoiceCommand.OpenInstalledApp,
        is VoiceCommand.OpenMedia,
        is VoiceCommand.SearchMedia,
        is VoiceCommand.MediaControl,
        is VoiceCommand.SetMediaVolume,
        is VoiceCommand.AdjustMediaVolume,
        is VoiceCommand.Navigate,
        is VoiceCommand.QuerySceneCapability,
        is VoiceCommand.OpenSceneScreen,
        VoiceCommand.CloseAllApps,
        is VoiceCommand.ScreenBlackout,
        is VoiceCommand.QueryLocalInfo,
        is VoiceCommand.QueryWindowingCapability,
        is VoiceCommand.SplitScreen,
        VoiceCommand.QueryWifiState,
        VoiceCommand.QueryWifiIpAddress,
        VoiceCommand.QueryWifiSsid,
        VoiceCommand.OpenWifiSettings,
        VoiceCommand.QueryDisplayBrightness,
        is VoiceCommand.SetManualDisplayBrightness -> androidRouter.execute(command)
        else -> vehicleRouter?.execute(command)
            ?: CommandExecutionResult.Rejected("Vehicle backend chưa sẵn sàng.")
    }
}
