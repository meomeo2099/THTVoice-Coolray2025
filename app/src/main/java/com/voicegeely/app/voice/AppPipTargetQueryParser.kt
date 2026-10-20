package com.voicegeely.app.voice

/**
 * High-confidence, read-only parser for questions about one target app and PiP.
 * It deliberately does not recognize mutation verbs such as "mở/bật PiP".
 */
object AppPipTargetQueryParser {
    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val appText = sequenceOf(
            Regex("^(?:kiểm tra|xem|cho biết)\\s+(?:khả năng\\s+)?(?:pip|picture in picture)\\s+(?:của\\s+)?(.+)$")
                .matchEntire(normalized)?.groupValues?.getOrNull(1),
            Regex("^(.+?)\\s+(?:có\\s+)?(?:hỗ trợ|dùng được|chạy được)\\s+(?:pip|picture in picture)(?:\\s+(?:không|0))?$")
                .matchEntire(normalized)?.groupValues?.getOrNull(1),
            Regex("^(.+?)\\s+(?:có thể\\s+)?(?:dùng|chạy)\\s+(?:pip|picture in picture)(?:\\s+(?:được\\s+)?(?:không|0))?$")
                .matchEntire(normalized)?.groupValues?.getOrNull(1)
        ).filterNotNull().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null

        val target = detectTarget(appText) ?: return null
        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.QueryWindowingCapability(WindowingMode.PIP, target)
        )
    }

    private fun detectTarget(text: String): AppTarget? = when {
        Regex("^(?:youtube music|yt music)$").matches(text) -> AppTarget.YOUTUBE_MUSIC
        Regex("^(?:youtube vanced|vanced)$").matches(text) -> AppTarget.VANCED
        Regex("^(?:revanced|re vanced)$").matches(text) -> AppTarget.REVANCED
        text == "morphe" -> AppTarget.MORPHE
        Regex("^smart ?tube$").matches(text) -> AppTarget.SMARTTUBE
        Regex("^(?:youtube|you tube)$").matches(text) -> AppTarget.YOUTUBE
        Regex("^(?:zing mp3|zing)$").matches(text) -> AppTarget.ZING_MP3
        text == "spotify" -> AppTarget.SPOTIFY
        Regex("^(?:google maps|maps|bản đồ)$").matches(text) -> AppTarget.MAPS
        text == "waze" -> AppTarget.WAZE
        Regex("^vietmap(?: live)?$").matches(text) -> AppTarget.VIETMAP
        Regex("^(?:chrome|trình duyệt)$").matches(text) -> AppTarget.CHROME
        text == "netflix" -> AppTarget.NETFLIX
        Regex("^(?:tiktok|tik tok)$").matches(text) -> AppTarget.TIKTOK
        text == "vlc" -> AppTarget.VLC
        text == "kiki" -> AppTarget.KIKI
        else -> null
    }
}
