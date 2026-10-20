package com.voicegeely.app.voice.safety

/** Result of asking to install an OTA/APK through the parked-only boundary. */
sealed class SafeInstallResult {
    data class Installed(val action: ParkedAction) : SafeInstallResult()
    data class Deferred(val action: ParkedAction, val decision: SafetyDecision) : SafeInstallResult()
    data class NothingPending(val detail: String = "Không có bản cài đặt nào đang chờ.") : SafeInstallResult()
}

/**
 * Keeps install execution separate from download. Download may happen while driving;
 * actual install only runs after a fresh SPEED + GEAR check reports P and < 1 km/h.
 *
 * No executable callback is persisted. The caller must provide the install callback again
 * when retrying, preventing stale process state from silently installing later.
 */
class SafeInstallGate(
    private val parkedGate: ParkedActionGate,
    private val deferredStore: DeferredParkedActionStore = DeferredParkedActionStore()
) {
    fun requestInstall(action: ParkedAction, install: () -> Unit): SafeInstallResult {
        require(action == ParkedAction.OTA_INSTALL || action == ParkedAction.APK_INSTALL) {
            "SafeInstallGate only accepts OTA_INSTALL or APK_INSTALL"
        }
        val decision = parkedGate.evaluate(action)
        return if (decision.type == SafetyDecisionType.ALLOW) {
            deferredStore.clear()
            install()
            SafeInstallResult.Installed(action)
        } else {
            deferredStore.defer(action)
            SafeInstallResult.Deferred(action, decision)
        }
    }

    fun retryPending(install: (ParkedAction) -> Unit): SafeInstallResult {
        val pending = deferredStore.peek() ?: return SafeInstallResult.NothingPending()
        val decision = parkedGate.evaluate(pending.action)
        if (decision.type != SafetyDecisionType.ALLOW) {
            return SafeInstallResult.Deferred(pending.action, decision)
        }

        deferredStore.take()
        install(pending.action)
        return SafeInstallResult.Installed(pending.action)
    }

    fun pendingAction(): DeferredParkedAction? = deferredStore.peek()

    fun cancelPending() = deferredStore.clear()
}
