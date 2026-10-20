package com.voicegeely.app.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroVietnameseTextFrontendTest {
    private fun frontend(vararg words: String): KokoroVietnameseTextFrontend {
        val file = File.createTempFile("tht-kokoro-lexicon", ".txt")
        file.deleteOnExit()
        file.writeText(words.joinToString("\n") { "$it a" } + "\n")
        return KokoroVietnameseTextFrontend.fromLexicon(file)
    }

    @Test fun normalizesTechnicalTermsBeforeCoverageCheck() {
        val f = frontend("mở", "wifi", "và", "đọc", "ip", "follow", "me", "home")
        val prepared = f.prepare("Mở Wi-Fi và đọc IP, Follow-Me-Home")
        assertTrue(prepared.covered)
        assertEquals("mở wifi và đọc ip, follow me home", prepared.text)
    }

    @Test fun knownVietnameseResponseIsAccepted() {
        val f = frontend("đã", "bật", "điều", "hòa")
        val prepared = f.prepare("Đã bật điều hòa.")
        assertTrue(prepared.covered)
        assertTrue(prepared.missingWords.isEmpty())
    }

    @Test fun unknownWordIsRejectedInsteadOfFallingBackToEspeakVietnamese() {
        val f = frontend("đã", "bật", "điều", "hòa")
        val prepared = f.prepare("Đã bật điều hòa ultranewword")
        assertFalse(prepared.covered)
        assertEquals(setOf("ultranewword"), prepared.missingWords)
    }

    @Test fun punctuationDoesNotBecomeLexiconTokens() {
        val f = frontend("nhiệt", "độ", "22", "5")
        val prepared = f.prepare("Nhiệt độ 22.5!")
        assertTrue(prepared.covered)
    }

    @Test
    fun missingWordsAreSkippedInsteadOfSilencingTheReply() {
        val f = frontend("đã", "bật", "điều", "hòa")
        val prepared = f.prepare("Đã bật điều hòa ultranewword.")
        assertEquals("đã bật điều hòa.", prepared.speakableText)
        assertEquals("", f.prepare("ultranewword").speakableText)
    }
}
