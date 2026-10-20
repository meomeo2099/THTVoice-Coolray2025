package com.voicegeely.app.voice

/**
 * Coolray version of THTVoice's TrunkClarification: the car has only a tailgate, so "mở cốp" is
 * rewritten to "mở cốp sau" instead of asking which trunk. "đóng cốp": the assistant cannot close
 * it, so it says so. [resolve] never has a pending question here.
 */
class TrunkClarification(private val windowMs: Long = WINDOW_MS) {
    sealed class Reply {
        data class Ask(val question: String) : Reply()
        data class Tell(val message: String) : Reply()
        /** Run this sentence instead (the only trunk is the tailgate). */
        data class Rewrite(val text: String) : Reply()
    }

    @Synchronized
    fun check(text: String, @Suppress("UNUSED_PARAMETER") nowMs: Long): Reply? {
        val t = VoiceTextNormalizer.normalize(text)
        return when {
            OPEN_ANY.matches(t) -> Reply.Rewrite(TAILGATE)
            CLOSE_ANY.matches(t) -> Reply.Tell(CANNOT_CLOSE)
            else -> null
        }
    }

    @Synchronized
    fun resolve(@Suppress("UNUSED_PARAMETER") text: String, @Suppress("UNUSED_PARAMETER") nowMs: Long): String? = null

    companion object {
        const val WINDOW_MS = 20_000L
        const val TAILGATE = "mở cốp sau"
        const val CANNOT_CLOSE = "Trợ lý chưa đóng được cốp. Hãy đóng cốp bằng tay."
        private val OPEN_ANY = Regex("^(?:mở|bật|mở khóa|mở khoá)(?: cái)? cốp(?: xe)?$")
        private val CLOSE_ANY = Regex("^(?:đóng|khóa|khoá|sập)(?: cái)? cốp(?: xe)?(?: trước| sau)?(?: lại)?$")
    }
}
