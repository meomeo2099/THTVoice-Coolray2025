package com.voicegeely.app.update

import com.voicegeely.app.voice.safety.ParkedAction
import com.voicegeely.app.voice.safety.ParkedActionGate
import com.voicegeely.app.voice.safety.SafetyDecisionType

/**
 * OTA runs only while the vehicle is parked: gear P and speed below the parked threshold.
 * Missing SPEED/GEAR fails closed. Checked before contacting the update server, while
 * downloading (the download stops if the car starts moving) and again before install.
 */
class OtaParkedPolicy(private val parkedGate: ParkedActionGate) {
    /** Null when OTA may proceed; otherwise the result to show. */
    fun blockedResult(record: Boolean = true): AppUpdateFlowResult? {
        val decision = parkedGate.evaluate(ParkedAction.OTA_INSTALL, record)
        if (decision.type == SafetyDecisionType.ALLOW) return null
        val reason = decision.reason?.let { " ($it)" }.orEmpty()
        return AppUpdateFlowResult(AppUpdateFlowStatus.WAITING_FOR_PARK, "$BLOCKED_MESSAGE$reason")
    }

    fun mayContinueDownload(): Boolean =
        parkedGate.evaluate(ParkedAction.OTA_INSTALL).type == SafetyDecisionType.ALLOW

    companion object {
        const val BLOCKED_MESSAGE = "Chỉ cập nhật khi xe ở số P và đứng yên."
        const val DOWNLOAD_STOPPED_MESSAGE = "Đã dừng tải bản cập nhật vì xe không còn đỗ; file tạm đã bị xóa."
    }
}
