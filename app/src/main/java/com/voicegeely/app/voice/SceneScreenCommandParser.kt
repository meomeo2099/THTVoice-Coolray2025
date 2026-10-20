package com.voicegeely.app.voice

/**
 * Parser for opening the OEM rest / camping scene screen.
 *
 * The command only opens the platform screen; it never claims the scene itself was
 * activated. Capability questions ("kiểm tra chế độ nghỉ", "xe có chế độ cắm trại
 * không") stay with [VoiceCommand.QuerySceneCapability].
 */
object SceneScreenCommandParser {
    private const val VERB = "(?:(?:bật|mở|vào|chuyển sang|chuyển) )?"
    private val rest = Regex(
        "^$VERB(?:chế độ )?(?:nghỉ ngơi|thư giãn|nghỉ trưa|ngủ trưa|chợp mắt|nap)(?: trong xe)?$"
    )
    private val camping = Regex("^$VERB(?:chế độ )?(?:đi )?cắm trại$")

    fun parse(text: String): ParsedVoiceCommand? {
        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null
        val scene = when {
            rest.matches(normalized) -> SceneType.REST
            camping.matches(normalized) -> SceneType.CAMPING
            else -> return null
        }
        return ParsedVoiceCommand(text, normalized, VoiceCommand.OpenSceneScreen(scene))
    }
}
