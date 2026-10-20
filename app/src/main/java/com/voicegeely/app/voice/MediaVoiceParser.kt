package com.voicegeely.app.voice

/** Local-only parser for default/explicit music and video app commands. */
object MediaVoiceParser {
    private val providerTail = Regex(
        "\\s+(?:trên|qua|bằng)\\s+(youtube music|yt music|youtube vanced|vanced|revanced|re vanced|morphe|smart ?tube|youtube|you tube|zing mp3|zing|spotify)\\s*$"
    )
    private val unsupportedPipMutation = Regex(
        "^(?:mở|bật|chạy|khởi động|chia)\\s+.*(?:pip|picture in picture)(?: |$)"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        // Explicit Android settings/capability commands are checked before media
        // routing so they cannot degrade into app search/open commands.
        WifiSettingsVoiceParser.parse(text)?.let { return it }
        AppPipTargetQueryParser.parse(text)?.let { return it }

        // Never let an unsupported PiP mutation degrade into a media search/open
        // command such as "mở YouTube bằng PiP" -> SearchMedia("YouTube").
        if (unsupportedPipMutation.containsMatchIn(normalized)) return null

        parseOpenOnly(text, normalized)?.let { return it }
        parseTypedSearch(text, normalized)?.let { return it }
        parseProviderSearch(text, normalized)?.let { return it }
        return null
    }

    private fun parseOpenOnly(original: String, text: String): ParsedVoiceCommand? {
        val provider = detectProvider(text)

        val musicOnly = Regex(
            "^(?:mở|bật|chạy|khởi động|phát)\\s+(?:nhạc|âm nhạc|music)(?:\\s+(?:trên|qua|bằng)\\s+.+)?$"
        ).matches(text)
        if (musicOnly) {
            return parsed(original, text, VoiceCommand.OpenMedia(MediaContentType.MUSIC, provider ?: MediaProvider.DEFAULT))
        }

        val videoOnly = Regex(
            "^(?:mở|bật|chạy|khởi động|phát)\\s+(?:video|vi deo)(?:\\s+(?:trên|qua|bằng)\\s+.+)?$"
        ).matches(text)
        if (videoOnly) {
            return parsed(original, text, VoiceCommand.OpenMedia(MediaContentType.VIDEO, provider ?: MediaProvider.DEFAULT))
        }

        // App names that are themselves media targets.
        if (provider != null && Regex("^(?:mở|bật|chạy|khởi động)\\s+.+$").matches(text)) {
            val remainder = text.replaceFirst(Regex("^(?:mở|bật|chạy|khởi động)\\s+"), "").trim()
            if (isOnlyProviderName(remainder, provider)) {
                return parsed(original, text, VoiceCommand.OpenMedia(defaultKind(provider), provider))
            }
        }
        return null
    }

    private fun parseTypedSearch(original: String, text: String): ParsedVoiceCommand? {
        val match = Regex("^(?:mở|phát|tìm|tìm kiếm)\\s+(nhạc|bài hát|video|vi deo)\\s+(.+)$").find(text)
            ?: Regex("^(?:nghe)\\s+(.+)$").find(text)?.let { m ->
                val query = stripProvider(m.groupValues[1])
                if (query.isBlank()) return null
                val provider = detectProvider(text) ?: MediaProvider.DEFAULT
                return parsed(original, text, VoiceCommand.SearchMedia(provider, query, MediaContentType.MUSIC))
            }
            ?: return null

        val kind = if (match.groupValues[1].contains("video") || match.groupValues[1].contains("vi deo")) {
            MediaContentType.VIDEO
        } else {
            MediaContentType.MUSIC
        }
        val provider = detectProvider(text) ?: MediaProvider.DEFAULT
        val query = stripProvider(match.groupValues[2])
        if (query.isBlank() || isOnlyProviderName(query, provider)) return null
        return parsed(original, text, VoiceCommand.SearchMedia(provider, query, kind))
    }

    private fun parseProviderSearch(original: String, text: String): ParsedVoiceCommand? {
        val provider = detectProvider(text) ?: return null
        val match = Regex("^(?:tìm|tìm kiếm|phát|mở)\\s+(.+?)\\s+(?:trên|qua|bằng)\\s+.+$").find(text)
            ?: return null
        val query = match.groupValues[1].trim()
        if (query.isBlank()) return null
        return parsed(original, text, VoiceCommand.SearchMedia(provider, query, defaultKind(provider)))
    }

    private fun stripProvider(value: String): String = value.replace(providerTail, "").trim()

    private fun detectProvider(text: String): MediaProvider? = when {
        Regex("youtube music|yt music").containsMatchIn(text) -> MediaProvider.YOUTUBE_MUSIC
        Regex("youtube vanced|(?:^| )vanced(?: |$)").containsMatchIn(text) -> MediaProvider.VANCED
        Regex("revanced|re vanced").containsMatchIn(text) -> MediaProvider.REVANCED
        Regex("morphe").containsMatchIn(text) -> MediaProvider.MORPHE
        Regex("smart ?tube").containsMatchIn(text) -> MediaProvider.SMARTTUBE
        Regex("zing mp3|(?:^| )zing(?: |$)").containsMatchIn(text) -> MediaProvider.ZING_MP3
        Regex("spotify").containsMatchIn(text) -> MediaProvider.SPOTIFY
        Regex("youtube|you tube").containsMatchIn(text) -> MediaProvider.YOUTUBE
        else -> null
    }

    private fun defaultKind(provider: MediaProvider): MediaContentType = when (provider) {
        MediaProvider.YOUTUBE_MUSIC,
        MediaProvider.ZING_MP3,
        MediaProvider.SPOTIFY -> MediaContentType.MUSIC
        else -> MediaContentType.VIDEO
    }

    private fun isOnlyProviderName(text: String, provider: MediaProvider): Boolean = when (provider) {
        MediaProvider.DEFAULT -> false
        MediaProvider.YOUTUBE -> Regex("youtube|you tube").matches(text)
        MediaProvider.VANCED -> Regex("youtube vanced|vanced").matches(text)
        MediaProvider.REVANCED -> Regex("revanced|re vanced").matches(text)
        MediaProvider.MORPHE -> Regex("morphe").matches(text)
        MediaProvider.SMARTTUBE -> Regex("smart ?tube").matches(text)
        MediaProvider.YOUTUBE_MUSIC -> Regex("youtube music|yt music").matches(text)
        MediaProvider.ZING_MP3 -> Regex("zing mp3|zing").matches(text)
        MediaProvider.SPOTIFY -> Regex("spotify").matches(text)
    }

    private fun parsed(original: String, normalized: String, command: VoiceCommand) =
        ParsedVoiceCommand(originalText = original, normalizedText = normalized, command = command)
}
