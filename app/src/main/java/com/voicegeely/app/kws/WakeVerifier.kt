package com.voicegeely.app.kws

import java.text.Normalizer
import java.util.Locale

/**
 * Second stage after the small keyword spotter: the Vietnamese ASR transcript of the audio held
 * just before the trigger must sound like "<greeting> Ge-ly". Lenient on purpose (the ASR writes
 * the brand many ways: "gi ly", "ghê ly", "di li", "hegel", "geely"…); it only has to reject music,
 * radio and conversation that the loosened spotter lets through.
 */
object WakeVerifier {
    enum class Verdict { ACCEPT, REJECT }

    private fun plain(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd').replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    // g/gh/gi/d/j/z/r + vowel, then l + vowel: gi ly, ghe ly, ghe lay, di li, je li, ri li…
    private val brandPair = Regex("\\b(gh?i?|gi|d|dz|j|z|r)[aeiouy]{1,2}[a-z]? ?l[aeiouy]{1,2}\\b")
    private val brandWord = Regex("\\b(geely|gely|jely|giely|hegel|hegely|gili|jili|dili)\\b")
    // Greeting directly followed by a g/gh/d/j syllable: "hay ghe", "hay day lay", "hay galay".
    private val greetingThenBrand = Regex("\\b(hey|hay|hai|hi|he|e|alo|lo)\\s+(gh?|gi|d|j|z)[aeiouy]")

    fun verify(profile: WakePhrase, transcript: String, sensitivity: WakeSensitivity): Verdict =
        verify(profile, transcript, sensitivity.acceptBlankConfirmation)

    /** [acceptBlank]: wake when the check heard nothing intelligible (never while media plays, R03). */
    fun verify(profile: WakePhrase, transcript: String, acceptBlank: Boolean): Verdict {
        val text = plain(transcript)
        if (text.isBlank()) return if (acceptBlank) Verdict.ACCEPT else Verdict.REJECT
        val ok = when (profile) {
            // "Hey THT" is English letters; the Vietnamese ASR has no stable spelling for it.
            WakePhrase.HEY_THT -> Regex("\\b(hey|hay|hai|he) (tht|t h t|ti eich ti|ti at ti)\\b").containsMatchIn(text)
            WakePhrase.HEY_GEELY, WakePhrase.HELLO_GEELY ->
                brandWord.containsMatchIn(text) || brandPair.containsMatchIn(text) || greetingThenBrand.containsMatchIn(text)
        }
        return if (ok) Verdict.ACCEPT else Verdict.REJECT
    }
}
