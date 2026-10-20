package com.voicegeely.app.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sherpa (offline-tts-kokoro-impl / kokoro-multi-lang-lexicon) sends every non-CJK segment of a
 * version-2 Kokoro model to eSpeak (metadata voice "vi"), and only CJK segments (U+4E00..U+9FFF)
 * through the lexicon. These tests check that the model text is made only of lexicon aliases in
 * that range plus single punctuation marks Sherpa treats as punctuation.
 */
class KokoroSherpaLexiconTest {
    private fun frontend(vararg words: String): KokoroVietnameseTextFrontend {
        val file = File.createTempFile("tht-kokoro-lexicon", ".txt").apply { deleteOnExit() }
        file.writeText(words.withIndex().joinToString("\n") { (i, w) -> "$w p$i q$i" } + "\n")
        return KokoroVietnameseTextFrontend.fromLexicon(file)
    }

    private val sherpaPunct = setOf(',', '.', '!', '?')
    private fun isCjk(c: Char) = c.code in 0x4E00..0x9FFF

    /** Splits like the regex in ConvertTextToTokenIds: CJK runs vs everything else. */
    private fun segments(text: String): List<String> = Regex("[\\u4e00-\\u9fff]+|[^\\u4e00-\\u9fff]+").findAll(text).map { it.value }.toList()

    @Test
    fun modelTextIsOnlyAliasesAndSinglePunctuation() {
        val f = frontend("đã", "mở", "tiếng", "việt", "youtube", "22")
        val model = f.modelText(f.prepare("Đã mở YouTube,  tiếng Việt 22!"))
        assertEquals("一丁丄,丂七丅!", model)
        for (seg in segments(model)) {
            assertTrue("segment '$seg'", seg.all(::isCjk) || (seg.length == 1 && seg[0] in sherpaPunct))
        }
    }

    @Test
    fun lexiconEntriesMapAliasesToTheOriginalPhonemesPlusWordGap() {
        val f = frontend("đã", "bật")
        val lines = f.sherpaLexicon().lines().filter { it.isNotBlank() }
        assertEquals(listOf("一 p0 q0 ", "丁 p1 q1 "), lines)
    }

    @Test
    fun missingWordsAndOddSymbolsAreDroppedAndAFinalStopIsAdded() {
        val f = frontend("đã", "bật", "điều", "hòa")
        assertEquals("一丁丂七.", f.modelText(f.prepare("Đã bật điều hòa ultranewword")))
        assertEquals("一,丁.", f.modelText(f.prepare("Đã: - bật …")))
        assertEquals("", f.modelText(f.prepare("ultranewword")))
    }

    @Test
    fun tokensGetTheWordGapAliasForTheSpaceId() {
        val original = "\$ 0\n; 1\n  16\nɗ 57\n"
        val out = KokoroVietnameseTextFrontend.sherpaTokens(original)
        assertTrue(out.endsWith(" 16\n"))
        assertTrue(out.startsWith("\$ 0\n; 1\n  16\nɗ 57\n"))
    }
}
