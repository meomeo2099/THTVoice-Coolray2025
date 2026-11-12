package com.voicegeely.app.voice

/** Explicit commands only: do not confuse engine shutdown, HVAC AUTO and Auto Hold. */
object CoolrayOemCommandParser {
    private const val AUTO_WORD = "(?:auto|aoto|aotu|ao to|ao tô|ao tu|au to|au tô|au tu|a tô|a to|a tu|a o tô|a u tô)"
    private const val AUTO_CAR_HOMOPHONE = "(?:ô tô|ô tu|o to)"
    private val autoAlone = Regex("^$AUTO_WORD$")
    private val autoClimate = Regex("^(?:điều hòa (?:$AUTO_WORD|$AUTO_CAR_HOMOPHONE)|(?:$AUTO_WORD|$AUTO_CAR_HOMOPHONE) điều hòa)$")
    private val autoModeHomophone = Regex("^chế độ $AUTO_CAR_HOMOPHONE$")
    fun parse(original: String): ParsedVoiceCommand? {
        val text = VoiceTextNormalizer.normalize(original)
        if (text in setOf("nghiêng cửa sổ trời", "mở nghiêng cửa sổ trời", "bật chế độ nghiêng cửa sổ trời")) {
            return ParsedVoiceCommand(original, text, VoiceCommand.SetSunroofTilt(true))
        }
        if (text in setOf("đóng nghiêng cửa sổ trời", "tắt chế độ nghiêng cửa sổ trời")) {
            return ParsedVoiceCommand(original, text, VoiceCommand.SetSunroofTilt(false))
        }
        val query = Regex("^(?:kiểm tra|trạng thái|xem trạng thái) (.+)$").matchEntire(text)?.groupValues?.get(1)
            ?: Regex("^(.+?) (?:có bật (?:không|0)|đang bật hay tắt)$").matchEntire(text)?.groupValues?.get(1)
        if (query != null) feature(query)?.let { return ParsedVoiceCommand(original, text, VoiceCommand.QueryOemSwitch(it)) }
        val switch = Regex("^(bật|mở|kích hoạt|tắt|ngắt|vô hiệu hóa) (.+)$").matchEntire(text)
        if (switch != null) {
            val on = switch.groupValues[1] in setOf("bật", "mở", "kích hoạt")
            val target = switch.groupValues[2].removePrefix("chế độ ")
            feature(target)?.let { return ParsedVoiceCommand(original, text, VoiceCommand.OemSwitch(it, on)) }
            if (target in setOf("điều hòa tự động", "tự động điều hòa") || autoAlone.matches(target) ||
                autoClimate.matches(target) || autoModeHomophone.matches(switch.groupValues[2])) {
                return ParsedVoiceCommand(original, text, VoiceCommand.HvacAuto(on))
            }
        }
        val profile = Regex("^(?:chỉnh|đặt|chuyển) quạt (?:tự động|auto)(?: sang)? (êm|êm dịu|yên tĩnh|bình thường|mạnh)$")
            .matchEntire(text)?.groupValues?.get(1) ?: return null
        val value = when (profile) { "mạnh" -> AutoFanProfile.STRONG; "bình thường" -> AutoFanProfile.NORMAL; else -> AutoFanProfile.QUIET }
        return ParsedVoiceCommand(original, text, VoiceCommand.SetAutoFanProfile(value))
    }

    private fun feature(text: String): OemFeature? = when (text.removePrefix("chế độ ")) {
        "auto hold", "autohold", "giữ phanh tự động" -> OemFeature.AUTO_HOLD
        "start stop", "startstop", "tự động dừng khởi động động cơ" -> OemFeature.START_STOP
        "đèn ban ngày", "đèn chạy ban ngày", "drl" -> OemFeature.DAYTIME_LIGHTS
        "esc sport" -> OemFeature.ESC_SPORT
        else -> null
    }
}
