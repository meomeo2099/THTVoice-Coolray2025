package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner 16/10/2026: "kịch bản" — one taught phrase runs several commands. */
class UserScenarioTest {
    private fun cmd(t: String) = VoiceParseChain.parse(t)!!.command

    @Test fun teachByVoiceAndResolveSteps() {
        val req = UserAliasCommands.parse("khi tôi nói đi làm thì bật điều hòa 24 độ rồi mở nhạc rồi chỉ đường tới công ty")
        assertTrue(req is UserAliasCommands.Request.Teach)
        val b = UserAliasBook()
        val r = b.teach((req as UserAliasCommands.Request.Teach).phrase, req.target) as UserAliasBook.TeachResult.Saved
        assertEquals(3, r.steps)
        val steps = b.resolveScenario("Đi làm")!!.map { it.command }
        assertEquals(listOf(cmd("bật điều hòa 24 độ"), cmd("mở nhạc"), cmd("chỉ đường tới công ty")), steps)
        assertNull("a scenario is not a single alias", b.resolve("đi làm"))
    }

    @Test fun singleAliasIsNotAScenario() {
        val b = UserAliasBook()
        b.teach("gió bão", "quạt mức 6")
        assertNull(b.resolveScenario("gió bão"))
        assertNotNull(b.resolve("gió bão"))
    }

    @Test fun limitsAndRoundTrip() {
        val b = UserAliasBook()
        assertTrue(b.teach("về nhà", "tắt điều hòa rồi mở nhạc rồi bài tiếp theo rồi tăng âm lượng rồi đóng tất cả kính") is UserAliasBook.TeachResult.Refused)
        assertTrue(b.teach("về nhà", "tắt điều hòa rồi làm cái gì đó lạ") is UserAliasBook.TeachResult.Refused)
        assertTrue(b.teach("về nhà", "tắt điều hòa, đóng tất cả kính") is UserAliasBook.TeachResult.Saved)
        assertTrue("one destination with 'và' stays a single alias",
            b.teach("đi chợ", "chỉ đường tới bến thành và chợ lớn") is UserAliasBook.TeachResult.Saved)
        val copy = UserAliasBook().apply { load(b.encode()) }
        assertEquals(2, copy.resolveScenario("về nhà")!!.size)
    }
}
