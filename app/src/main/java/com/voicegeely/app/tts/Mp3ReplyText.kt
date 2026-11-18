package com.voicegeely.app.tts

import java.text.Normalizer
import java.util.Locale

internal object Mp3ReplyText {
    fun key(text: String): String = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        .trimEnd('.', '!', '?', '…').lowercase(Locale.ROOT)

    private val token = Regex("-?\\d+(?:[.,]\\d+)?|[\\p{L}\\p{M}]+(?:[-/][\\p{L}\\p{M}]+)*|[^\\s]")
    private val punctuation = setOf(".", ",", "!", "?", ":", ";", "…")
    private val numeric = Regex("-?(?:0|[1-9]\\d{0,8})(?:[.,]\\d{1,3})?")
    // "22.5" and "22,5" are the same number: full-sentence clips are written with a comma.
    private fun words(text: String): List<String> = token.findAll(key(text)
        .replace("°c", " độ").replace("%", " phần trăm"))
        .map { if (it.value.first().isDigit() || it.value.first() == '-') it.value.replace('.', ',') else it.value }
        .filter { it !in punctuation }.toList()

    /** No partial reading: every word and every number must be covered before audio starts. */
    fun select(text: String, clips: Map<String, String>): List<String>? {
        if (text.isBlank() || text.length > 2000) return null
        clips[key(text)]?.let { return listOf(it) }
        // IP addresses / dotted identifiers are not decimal measurements.
        if (Regex("\\d[.,]\\d+[.,]\\d").containsMatchIn(text)) return null
        val input = words(text)
        if (input.isEmpty() || input.size > 200) return null
        val phrases = clips.entries.map { words(it.key) to it.value }
            .filter { it.first.isNotEmpty() }.groupBy { it.first.first() }
        val knownIds = clips.values.toSet()
        val solutions = arrayOfNulls<List<String>>(input.size + 1)
        solutions[input.size] = emptyList()
        for (i in input.lastIndex downTo 0) {
            for ((phrase, id) in phrases[input[i]].orEmpty().sortedByDescending { it.first.size }) {
                val end = i + phrase.size
                if (end <= input.size && input.subList(i, end) == phrase) {
                    val tail = solutions[end] ?: continue
                    solutions[i] = listOf(id) + tail
                    break
                }
            }
            if (solutions[i] == null && solutions[i + 1] != null) {
                numberIds(input[i], clips)?.takeIf { knownIds.containsAll(it) }?.let {
                    solutions[i] = it + solutions[i + 1]!!
                }
            }
        }
        return solutions[0]?.takeIf { it.size <= 160 }
    }

    private fun numberIds(raw: String, clips: Map<String, String>): List<String>? {
        if (!numeric.matches(raw)) return null
        val parts = raw.removePrefix("-").split('.', ',')
        val value = parts[0].toIntOrNull() ?: return null
        val ids = mutableListOf<String>()
        fun phrase(text: String) { ids += clips[key(text)] ?: "missing/$text" }
        fun small(n: Int, padded: Boolean) {
            if (n == 0) return
            val hundreds = n / 100
            val rest = n % 100
            if (hundreds > 0) { ids += "number/$hundreds"; phrase("trăm") }
            else if (padded) phrase("không trăm")
            if (rest in 1..9 && (hundreds > 0 || padded)) phrase("linh")
            if (rest > 0) ids += "number/$rest"
        }
        if (raw.startsWith("-")) ids += "number/negative"
        if (value <= 100) ids += "number/$value"
        else {
            val million = value / 1_000_000
            val thousand = value / 1000 % 1000
            val unit = value % 1000
            if (million > 0) { small(million, false); phrase("triệu") }
            if (thousand > 0) { small(thousand, million > 0); phrase("nghìn") }
            if (unit > 0) small(unit, million > 0 || thousand > 0)
        }
        if (parts.size == 2) {
            ids += "number/decimal"
            parts[1].forEach { ids += "number/$it" }
        }
        return ids.takeIf { clips.values.containsAll(it) }
    }
}
