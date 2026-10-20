package com.voicegeely.app.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Parser for Coolray cabin, exterior and fog lights (REFERENCE: GalaxyVoiceAssistant
 * LightControlHandler / ExteriorLightControlHandler, Coolray branch).
 *
 * Uses its own light normalisation (lowercase + punctuation strip) instead of
 * VoiceTextNormalizer, because the shared normalizer rewrites cabin-light names
 * ("đèn nội thất", "đèn trong xe", "đèn cabin") to the ambient-light path, which
 * Coolray does not have. Ambient phrases (đèn viền, LED, màu, độ sáng, phần trăm) and
 * the "khi mở cửa" setting are left to the shared parser so they are rejected as before.
 */
object CoolrayLightCommandParser {
    private val onVerb = Regex("(?:^| )(?:bật|mở)(?: |$)")
    private val offVerb = Regex("(?:^| )(?:tắt|đóng)(?: |$)")

    private val cabin = Regex("đèn (?:trong xe|nội thất|trần|cabin|đọc sách|đọc bản đồ|khoang lái)")
    private val fog = Regex("đèn (?:sương mù|gầm)")
    private val position = Regex("đèn (?:định vị|demi|đờ mi|sườn)")
    private val lowBeam = Regex("đèn (?:cốt|chiếu gần|chiếu sáng|pha cốt|trước|pha)(?: |$)")
    private val exteriorAll = Regex("(?:đèn ngoài|đèn bên ngoài|đèn ngoài xe)")

    /** Phrases owned by other parsers / families. */
    private val excluded = Regex("viền|led|ambient|màu|độ sáng|phần trăm|%|khi mở cửa|thông minh|độ cao|chỉnh đèn|nâng đèn|hạ đèn|tự động|follow|về nhà")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = normalize(text)
        if (normalized.isBlank() || excluded.containsMatchIn(normalized)) return null
        val on = onVerb.containsMatchIn(normalized)
        val off = offVerb.containsMatchIn(normalized)
        if (on == off) return null

        val command = when {
            cabin.containsMatchIn(normalized) -> VoiceCommand.CabinLight(on)
            fog.containsMatchIn(normalized) -> VoiceCommand.FogLight(on)
            position.containsMatchIn(normalized) ->
                VoiceCommand.ExteriorLight(if (on) ExteriorLightMode.POSITION else ExteriorLightMode.OFF)
            lowBeam.containsMatchIn(normalized) ->
                VoiceCommand.ExteriorLight(if (on) ExteriorLightMode.LOW_BEAM else ExteriorLightMode.OFF)
            exteriorAll.containsMatchIn(normalized) && off -> VoiceCommand.ExteriorLight(ExteriorLightMode.OFF)
            else -> return null
        }
        return ParsedVoiceCommand(originalText = text, normalizedText = normalized, command = command)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC)
            .lowercase(Locale("vi", "VN"))
            .replace(Regex("[^\\p{L}\\p{N}%]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
