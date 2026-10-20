package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner decision 14/10/2026: "mở kính"/"đóng kính" without a window → ask which one. */
class WindowClarificationTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    private fun answer(question: String, reply: String): VoiceCommand? {
        val c = WindowClarification()
        assertEquals(question, WindowClarification.question(if (question.startsWith("đóng") || question.endsWith("lên") || question.startsWith("nâng")) "đóng" else "mở"), c.check(question, 1_000))
        return c.resolve(reply, 3_000)?.command
    }

    @Test fun ambiguousSentencesAsk() {
        val c = WindowClarification()
        for (t in listOf("mở kính", "đóng kính", "hạ kính", "nâng kính", "mở cửa kính", "đóng cửa kính",
            "kéo kính xuống", "kéo kính lên", "mở kính ra", "đóng kính lại", "mở kính 50 phần trăm")) {
            assertNull("$t must stay unparsed", cmd(t))
            assertNotNull(t, c.check(t, 0))
        }
    }

    @Test fun shortQuestionByVerb() {
        val c = WindowClarification()
        assertEquals("Bạn cần mở kính nào?", c.check("mở kính", 0))
        assertEquals("Bạn cần đóng kính nào?", c.check("đóng kính", 0))
        assertEquals("Bạn cần đóng kính nào?", c.check("kéo kính lên", 0))
        assertEquals("Bạn cần mở kính nào?", c.check("hạ kính", 0))
    }

    @Test fun misheardAnswers() {
        assertEquals(cmd("mở kính lái"), answer("mở kính", "nái"))
        assertEquals(cmd("mở kính lái"), answer("mở kính", "kính nái"))
        assertEquals(cmd("đóng kính phụ"), answer("đóng kính", "phủ"))
        assertEquals(cmd("đóng kính phụ"), answer("đóng kính", "kiếng phụ"))
        assertEquals(cmd("mở kính sau"), answer("mở kính", "xau"))
        assertEquals(cmd("mở kính sau trái"), answer("mở kính", "sau chái"))
        assertEquals(cmd("đóng kính sau phải"), answer("đóng kính", "xau phãi"))
        assertEquals(cmd("mở kính trước"), answer("mở kính", "chước"))
        for (all in listOf("tấc cả", "tắc cả", "hét", "cả bố")) assertEquals(all, cmd("mở tất cả kính"), answer("mở kính", all))
    }

    @Test fun namedOrAllWindowsDoNotAsk() {
        val c = WindowClarification()
        for (t in listOf("mở kính lái", "đóng tất cả kính", "mở hết kính", "mở nhạc", "mở cốp")) assertNull(t, c.check(t, 0))
        for (t in listOf("mở tất cả kính", "đóng hết kính", "mở cả 4 kính", "mở kính hết", "đóng kính hết")) assertNotNull(t, cmd(t))
        assertEquals(cmd("đóng hết kính"), cmd("đóng kính hết"))
    }

    @Test fun answersBecomeTheFullCommand() {
        assertEquals(cmd("mở kính lái"), answer("mở kính", "kính lái"))
        assertEquals(cmd("mở kính lái"), answer("mở kính", "lái"))
        assertEquals(cmd("đóng kính phụ"), answer("đóng kính", "kính phụ"))
        assertEquals(cmd("đóng kính sau"), answer("đóng kính lại", "sau"))
        assertEquals(cmd("mở kính sau trái"), answer("hạ kính", "kính sau trái"))
        assertEquals(cmd("đóng kính lái"), answer("kéo kính lên", "kính lái"))
        assertEquals(cmd("mở kính lái"), answer("kéo kính xuống", "lái"))
        for (all in listOf("tất cả", "hết", "cả 4", "toàn bộ", "tất cả kính")) {
            assertEquals(all, cmd("mở tất cả kính"), answer("mở kính", all))
            assertEquals(all, cmd("đóng tất cả kính"), answer("đóng kính", all))
        }
        assertEquals(cmd("mở kính phụ 50 phần trăm"), answer("mở kính 50 phần trăm", "kính phụ"))
    }

    @Test fun otherReplyTimeoutAndOneShot() {
        val c = WindowClarification()
        c.check("mở kính", 0)
        assertNull("not a window answer", c.resolve("mở nhạc", 1_000))
        assertNull("question cleared", c.resolve("kính lái", 2_000))
        c.check("mở kính", 0)
        assertNull("too late", c.resolve("kính lái", WindowClarification.WINDOW_MS + 1))
        assertNull("no question asked", WindowClarification().resolve("kính lái", 0))
    }
}
