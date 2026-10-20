package com.voicegeely.app.voice

/** Read-only classification for target-app Picture-in-Picture preconditions. */
enum class AppPipCapabilityState {
    SYSTEM_UNSUPPORTED,
    APP_NOT_VISIBLE,
    APP_VISIBLE_UNVERIFIED
}

data class AppPipCapability(
    val state: AppPipCapabilityState,
    val packageName: String? = null,
    val activityName: String? = null
) {
    /**
     * A visible target is only a candidate for later device validation. This is
     * deliberately not named `canEnterPip`: public PackageManager data used by
     * this probe does not prove the target Activity declared or can enter PiP.
     */
    val isVisibleCandidate: Boolean
        get() = state == AppPipCapabilityState.APP_VISIBLE_UNVERIFIED
}

/** Pure fail-closed classifier used by JVM regressions. */
object AppPipCapabilityClassifier {
    fun classify(
        systemDeclaresPip: Boolean,
        appVisible: Boolean
    ): AppPipCapabilityState = when {
        !systemDeclaresPip -> AppPipCapabilityState.SYSTEM_UNSUPPORTED
        !appVisible -> AppPipCapabilityState.APP_NOT_VISIBLE
        else -> AppPipCapabilityState.APP_VISIBLE_UNVERIFIED
    }
}
