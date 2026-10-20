package com.voicegeely.app

/**
 * Settings → "Kiểm tra nút voice": which other apps on the head unit also react to the steering voice key.
 *
 * The ECARX voice key is a plain broadcast, so every registered receiver gets it; the phone-projection app
 * (Android Auto / CarPlay host) answering it is what shows "kết nối điện thoại bằng cáp USB". The system
 * default assistant also gets the key on some firmware. This class only analyses what [VoiceKeyProbe] read.
 */
object VoiceKeyDiagnostics {

    enum class Kind { VOICE_KEY_RECEIVER, ASSIST_ACTIVITY, VOICE_COMMAND_ACTIVITY }

    data class Listener(
        val packageName: String,
        val className: String,
        val label: String,
        val kind: Kind,
        val priority: Int = 0
    )

    data class Report(
        val selfPackage: String,
        val listeners: List<Listener>,
        /** Settings.Secure "assistant" (flattened component), null when unset or unreadable. */
        val defaultAssistant: String?,
        /** Settings.Secure "voice_interaction_service". */
        val voiceInteractionService: String?
    )

    private val PROJECTION_HINTS = listOf(
        "carplay", "androidauto", "android.auto", "projection", "carlink", "autolink", "phonelink",
        "mirror", "easyconnect", "hicar", "carlife", "zlink", "chiếu", "smartlink"
    )

    /** Package part of a flattened ComponentName ("pkg/.Cls" → "pkg"). */
    fun packageOf(flattened: String?): String? =
        flattened?.trim()?.takeIf { it.isNotEmpty() }?.substringBefore('/')?.takeIf { it.isNotEmpty() }

    fun looksLikeProjection(listener: Listener): Boolean {
        val text = "${listener.packageName} ${listener.className} ${listener.label}".lowercase()
        return PROJECTION_HINTS.any { it in text }
    }

    /** Other apps that take the voice key, projection-looking ones first. */
    fun suspects(report: Report): List<Listener> =
        report.listeners
            .filter { it.packageName != report.selfPackage }
            .distinctBy { it.packageName to it.kind }
            .sortedWith(compareByDescending<Listener> { looksLikeProjection(it) }
                .thenBy { it.kind.ordinal }
                .thenByDescending { it.priority })

    /** True when the system assistant points at another app (not unset, not THT Voice). */
    fun assistantIsOtherApp(report: Report): Boolean {
        val pkg = packageOf(report.defaultAssistant) ?: packageOf(report.voiceInteractionService) ?: return false
        return pkg != report.selfPackage
    }

    fun format(report: Report): String = buildString {
        val others = suspects(report)
        val assistantPkg = packageOf(report.defaultAssistant) ?: packageOf(report.voiceInteractionService)
        val assistantLabel = when {
            assistantPkg == null -> "chưa đặt"
            assistantPkg == report.selfPackage -> "THT Voice"
            else -> report.listeners.firstOrNull { it.packageName == assistantPkg }?.label
                ?.let { "$it ($assistantPkg)" } ?: assistantPkg
        }
        appendLine("Trợ lý mặc định: $assistantLabel")
        appendLine()
        if (others.isEmpty()) {
            appendLine("Không thấy app nào khác nhận nút voice.")
        } else {
            appendLine("App khác cũng nhận nút voice:")
            others.forEach { l ->
                val tag = if (looksLikeProjection(l)) " — có thể là app chiếu điện thoại" else ""
                appendLine("• ${l.label} (${l.packageName}) · ${kindText(l.kind)}$tag")
            }
        }
        appendLine()
        append(advice(report, others))
    }.trimEnd()

    private fun kindText(kind: Kind): String = when (kind) {
        Kind.VOICE_KEY_RECEIVER -> "nhận phím voice"
        Kind.ASSIST_ACTIVITY -> "trợ lý hệ thống"
        Kind.VOICE_COMMAND_ACTIVITY -> "lệnh giọng nói"
    }

    private fun advice(report: Report, others: List<Listener>): String = when {
        assistantIsOtherApp(report) ->
            "Trợ lý mặc định đang là app khác: bấm \"Mở cài đặt\" ở dòng \"Đặt THT Voice làm trợ lý mặc định\" và chọn THT Voice."
        others.any { it.kind == Kind.VOICE_KEY_RECEIVER } ->
            "App ở trên nhận cùng phím voice nên có thể hiện thông báo cắm cáp USB. Bấm \"Sao chép\" và gửi nội dung này cho người hỗ trợ."
        others.isNotEmpty() ->
            "Nếu vẫn hiện thông báo cắm cáp USB, bấm \"Sao chép\" và gửi nội dung này cho người hỗ trợ."
        else -> "Nếu vẫn hiện thông báo cắm cáp USB, app gây lỗi có thể tự bắt phím ở tầng hệ thống. Chụp ảnh thông báo và gửi cho người hỗ trợ."
    }
}
