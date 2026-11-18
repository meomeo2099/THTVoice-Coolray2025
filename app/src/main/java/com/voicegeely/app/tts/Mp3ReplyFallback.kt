package com.voicegeely.app.tts

import com.voicegeely.app.voice.CommandExecutionResult
import com.voicegeely.app.voice.VoiceCommand

/**
 * Short replies (as in THTVoice) spoken when the full result sentence is not in the MP3/M4A pack and
 * no other voice is available, e.g. split-screen replies. They describe execution state only and never
 * invent a query result.
 */
internal object Mp3ReplyFallback {
    fun single(command: VoiceCommand, result: CommandExecutionResult): String? = when (result) {
        is CommandExecutionResult.AcceptedUnverified -> "Đã gửi lệnh, nhưng chưa xác nhận được kết quả."
        is CommandExecutionResult.Failed, is CommandExecutionResult.Rejected -> "Không thể thực hiện lệnh."
        is CommandExecutionResult.Confirmed -> when (command) {
            is VoiceCommand.QueryVehicleStatus, is VoiceCommand.QueryLocalInfo,
            is VoiceCommand.QuerySceneCapability, is VoiceCommand.QueryWindowingCapability,
            VoiceCommand.QueryWifiState, VoiceCommand.QueryDisplayBrightness -> null
            VoiceCommand.QueryWifiSsid -> "Đã hiển thị tên mạng Wi-Fi."
            VoiceCommand.QueryWifiIpAddress -> "Đã hiển thị IP nội bộ."
            is VoiceCommand.SearchMedia -> "Đã mở kết quả tìm kiếm."
            is VoiceCommand.Navigate -> "Đã mở dẫn đường."
            else -> "Đã thực hiện lệnh."
        }
    }
}
