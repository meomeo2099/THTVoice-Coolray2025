package com.voicegeely.app.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Drivers often say the wake phrase again at the start of a command ("Hey Geely, mở nhạc"),
 * and the Vietnamese ASR writes it as odd words (owner report 03/10/2026: "hegel", "hay galay",
 * "hay ghe la", "he ghe", "hay gher", "hây ghê", "hay ghel", "hay ghe lay", "hay dây lay",
 * "hay ghe đây", "hay gher đây"). Those words are removed from the start of the text so the
 * command parser only sees the command.
 */
object WakePhraseEcho {
    private const val E = "(?![\\p{L}\\p{N}])"
    private const val HEY = "(?:hey|hây|hay|hấy|hầy|he|hê|hi|hai|ê|ơi)"
    private const val HELLO = "(?:hello|hê lô|he lô|hê lo|he lo|hế lô|a lô|alo|hơ lâu)"
    private const val GEELY =
        "(?:geely|gili|gi li|ghi li|ghi lì|ghê li|ghe li|ghê lây|ghe lây|ghe lay|ghê lay|ghe la|ghe lơ|" +
            "ghe đây|ghê đây|gher đây|gher|ghel|ghê|ghe|galay|ga lây|gờ lây|dây lay|dây lây|di li|ji li|jelly|chi li)"
    private const val THT = "(?:tht|tê hát tê|ti ếch ti|ti ét ti|tê hắt tê)"

    private val leading = Regex(
        "^(?:(?:$HEY|$HELLO) (?:$GEELY|$THT)|hegel|hê ghen|he ghen|hây ghen)$E[\\s,.!?]*"
    )

    /** Text without a leading wake phrase; unchanged when it does not start with one. */
    fun strip(text: String): String {
        val lower = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale("vi", "VN")).trim()
        val match = leading.find(lower) ?: return text.trim()
        return text.trim().substring(match.range.last + 1).trim()
    }

    /** True when the whole utterance was only the wake phrase (nothing to execute). */
    fun isOnlyWakePhrase(text: String): Boolean = text.isNotBlank() && strip(text).isBlank()
}
