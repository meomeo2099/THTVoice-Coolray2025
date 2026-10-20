package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.RegenLevel
import com.voicegeely.app.voice.VoiceCommand

object SafetyPromptFormatter {
    fun confirmation(command: VoiceCommand, motion: VehicleMotionState): String {
        val prefix = motion.speedKph?.let { "Xe đang ở ${it.toInt()} km/h. " } ?: "Xe đang di chuyển. "
        val action = when (command) {
            is VoiceCommand.SetDriveMode -> "Xác nhận chuyển sang chế độ ${driveMode(command.mode)}?"
            is VoiceCommand.SetRegenLevel -> "Xác nhận đổi mức hồi năng lượng sang ${regen(command.level)}?"
            is VoiceCommand.Window -> "Xác nhận thực hiện lệnh điều khiển kính vừa nói?"
            is VoiceCommand.ExteriorLight, is VoiceCommand.FogLight -> "Xác nhận thực hiện lệnh điều khiển đèn ngoài xe vừa nói?"
            is VoiceCommand.Sunroof -> "Xác nhận thực hiện lệnh điều khiển cửa sổ trời vừa nói?"
            else -> "Xác nhận thực hiện lệnh này?"
        }
        return prefix + action
    }

    private fun driveMode(mode: DriveMode): String = when (mode) {
        DriveMode.ECO -> "Eco"
        DriveMode.COMFORT -> "Comfort"
        DriveMode.SPORT -> "Sport"
    }

    private fun regen(level: RegenLevel): String = when (level) {
        RegenLevel.LOW -> "thấp"
        RegenLevel.MEDIUM -> "trung bình"
        RegenLevel.HIGH -> "cao"
    }
}
