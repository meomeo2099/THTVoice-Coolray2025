package com.voicegeely.app.voice

/**
 * Local Android commands that never touch the vehicle: go to the home screen
 * ("đóng tất cả ứng dụng"), black screen on/off, and time/date/assistant status.
 * Every pattern is a full match so broader app/media/brightness phrases are unaffected.
 */
object AndroidLocalCommandParser {
    private const val APPS = "(?:ứng dụng|app|apps)"
    private val closeAll = Regex(
        "^(?:(?:đóng|tắt|tắc|thoát|dừng|dọn dẹp) (?:(?:tất cả|hết|toàn bộ|các) )?$APPS(?: đang chạy| chạy ngầm)?" +
            "|(?:xóa|xoá) $APPS chạy ngầm|kill(?: all)? $APPS|(?:về|mở) (?:trang chủ|màn hình chính|home))$"
    )
    private val blackoutOn = Regex("^(?:tắt màn hình(?: xe)?|màn hình đen|tối màn hình)$")
    private val blackoutOff = Regex("^(?:bật màn hình|mở màn hình|hết màn hình đen|sáng màn hình)$")
    private val time = Regex("^(?:bây giờ (?:là )?mấy giờ|mấy giờ(?: rồi)?(?: xe)?|xem giờ|giờ là bao nhiêu)$")
    private val date = Regex("^(?:hôm nay (?:là )?ngày (?:bao nhiêu|gì|mấy)|ngày mấy|hôm nay thứ mấy)$")
    private val assistant = Regex(
        // The normalizer may turn "không" into "0".
        "^(?:(?:được|ổn) (?:không|0) (?:em|xe)|sẵn sàng chưa em|(?:trợ lý|bạn) (?:có )?(?:khỏe|sẵn sàng|nghe rõ)(?: không| 0| chưa)?)$"
    )

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val command = when {
            closeAll.matches(normalized) -> VoiceCommand.CloseAllApps
            blackoutOn.matches(normalized) -> VoiceCommand.ScreenBlackout(on = true)
            blackoutOff.matches(normalized) -> VoiceCommand.ScreenBlackout(on = false)
            time.matches(normalized) -> VoiceCommand.QueryLocalInfo(LocalInfoKind.TIME)
            date.matches(normalized) -> VoiceCommand.QueryLocalInfo(LocalInfoKind.DATE)
            assistant.matches(normalized) -> VoiceCommand.QueryLocalInfo(LocalInfoKind.ASSISTANT_STATUS)
            else -> return null
        }
        return ParsedVoiceCommand(text, normalized, command)
    }
}
