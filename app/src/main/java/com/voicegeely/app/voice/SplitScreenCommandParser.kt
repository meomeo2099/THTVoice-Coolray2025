package com.voicegeely.app.voice

/**
 * Split-screen phrases handled by the separate THT Split app (API v2, docs/SPLIT_SCREEN.md).
 *
 * Every pattern needs "chia", "bên trái/phải", "đổi bên" or "thanh chia", so ordinary
 * "mở YouTube" / "tắt màn hình" never land here. App names resolve only to catalog
 * [AppTarget]s; an unknown name returns null instead of guessing an app.
 */
object SplitScreenCommandParser {
    private const val SPLIT = "(?:chia|tách)(?: đôi| hai| 2)?(?: màn hình)?"
    private const val SIDE = "bên (trái|phải)"

    private val stop = Regex("^(?:thoát|tắt|hủy|huỷ|dừng|đóng|bỏ|ngừng|ra khỏi|thoát khỏi)\\s+(?:chế độ\\s+)?$SPLIT$")
    private val startLastPair = Regex(
        "^(?:(?:mở|bật)(?: lại)?\\s+)?(?:chế độ\\s+)?$SPLIT(?: lại)?$|^mở lại $SPLIT$"
    )
    private val startPair = Regex("^(?:$SPLIT|ghép(?: màn hình)?)\\s+(.+?)\\s+(?:với|và|cùng|bên cạnh)\\s+(.+)$")
    private val swap = Regex("^(?:đổi|đảo|hoán đổi)\\s+(?:bên|vị trí|hai bên|2 bên|trái phải)(?: (?:màn hình|$SPLIT))?$")
    private val equal = Regex("^$SPLIT\\s+(?:đều|bằng nhau|50 50|năm mươi năm mươi)$|^chia đều(?: hai bên| màn hình)?$")
    private val ratio = Regex("^(?:(?:chỉnh|đặt|cho|để)\\s+)?$SIDE\\s+(\\d{1,3})\\s*(?:phần trăm|%)$")
    private val replace = Regex("^(?:đổi|thay|chuyển)\\s+$SIDE\\s+(?:sang|thành|bằng|qua)\\s+(.+)$")
    private val fullscreenSide = Regex("^(?:(?:mở|cho|phóng|để)\\s+)?$SIDE\\s+(?:ra\\s+)?(?:toàn|full) màn hình$")
    private val fullscreenApp = Regex("^(?:(?:mở|cho|phóng|để)\\s+)?(.+?)\\s+(?:ra\\s+)?(?:toàn|full) màn hình$")
    private val restore = Regex("^(?:khôi phục|trở lại|quay lại|quay về)\\s+$SPLIT$")
    private val hide = Regex("^(?:ẩn|giấu|tắt)\\s+thanh $SPLIT$")
    /** "gỡ kẹt màn hình" (also "gở kẹt", "gỡ kẹp"): status bar / toolbar gone after a ROM divider crash. */
    private val recover = Regex(
        "^(?:gỡ|gở|gợ|sửa|chữa)\\s+(?:kẹt|kẹp|kẹc|treo)(?:\\s+(?:màn hình|giao diện|chia màn hình|thanh trạng thái))?(?:\\s+xe)?$" +
            "|^khôi phục\\s+(?:giao diện|thanh trạng thái)(?:\\s+xe)?$|^(?:bị\\s+)?mất\\s+thanh trạng thái$"
    )
    private val show = Regex("^(?:hiện|hiển thị|mở|bật)\\s+thanh $SPLIT$")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        return command(regional(normalized))?.let { ParsedVoiceCommand(text, normalized, it) }
    }

    /**
     * Regional / casual speech the shared normalizer leaves alone (09/10/2026):
     * "màng hình", "mà hình", "chí đôi", "bốn chục phần trăm", and trailing "đi / cái / giùm cái / lẹ".
     */
    private fun regional(input: String): String {
        var value = input
            .replace(Regex("(?<![\\p{L}])(?:màng|mà|mằn|màn) hình(?![\\p{L}])"), "màn hình")
            .replace(Regex("^((?:\\p{L}+ )*)(?:chí|chi|tria|tria) (?=đôi|hai|2|màn hình)"), "$1chia ")
            .replace(Regex("(?<![\\p{L}\\d])(\\d) chục(?! \\d)")) { "${it.groupValues[1].toInt() * 10}" }
            .replace(Regex("(?<![\\p{L}\\d])(\\d) chục (\\d)(?!\\d)")) { "${it.groupValues[1].toInt() * 10 + it.groupValues[2].toInt()}" }
        repeat(2) {
            value = value.replace(Regex(" (?:đi|cái|lẹ|liền|luôn|coi|thử|giùm|dùm|giúp|giúp tôi|giùm tui|dùm tui|hộ|nhé|nha|nghen|nhen|với)$"), "")
        }
        return value.trim()
    }

    private fun command(text: String): VoiceCommand.SplitScreen? {
        if (stop.matches(text)) return VoiceCommand.SplitScreen(SplitAction.STOP)
        if (recover.matches(text)) return VoiceCommand.SplitScreen(SplitAction.RECOVER)
        if (hide.matches(text)) return VoiceCommand.SplitScreen(SplitAction.HIDE)
        if (show.matches(text)) return VoiceCommand.SplitScreen(SplitAction.SHOW)
        if (restore.matches(text)) return VoiceCommand.SplitScreen(SplitAction.RESTORE)
        if (swap.matches(text)) return VoiceCommand.SplitScreen(SplitAction.SWAP)
        if (equal.matches(text)) return VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = 50)
        if (startLastPair.matches(text)) return VoiceCommand.SplitScreen(SplitAction.START)
        ratio.matchEntire(text)?.let { m ->
            val value = m.groupValues[2].toInt()
            val left = if (m.groupValues[1] == "trái") value else 100 - value
            return VoiceCommand.SplitScreen(SplitAction.RATIO, ratio = left)
        }
        replace.matchEntire(text)?.let { m ->
            val app = app(m.groupValues[2]) ?: return null
            return VoiceCommand.SplitScreen(SplitAction.REPLACE, side = side(m.groupValues[1]), app = app)
        }
        fullscreenSide.matchEntire(text)?.let { m ->
            return VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, side = side(m.groupValues[1]))
        }
        startPair.matchEntire(text)?.let { m ->
            val left = app(m.groupValues[1]) ?: return null
            val right = app(m.groupValues[2]) ?: return null
            if (left == right) return null
            return VoiceCommand.SplitScreen(SplitAction.START, left = left, right = right)
        }
        fullscreenApp.matchEntire(text)?.let { m ->
            val app = app(m.groupValues[1]) ?: return null
            return VoiceCommand.SplitScreen(SplitAction.FULLSCREEN, app = app)
        }
        return null
    }

    private fun side(word: String): SplitSide = if (word == "trái") SplitSide.LEFT else SplitSide.RIGHT

    /** Only a whole catalog name ("youtube", "google maps", "bản đồ"), never part of a longer sentence. */
    private fun app(raw: String): AppTarget? {
        val name = InstalledAppMatcher.canonicalName(raw.removePrefix("ứng dụng ").removePrefix("app ").trim())
        if (name.isBlank() || name.split(' ').size > 3) return null
        return VoiceCommandParser.detectAppTarget(name)
    }
}
