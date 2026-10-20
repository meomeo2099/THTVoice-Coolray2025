package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAliasBookTest {
    private fun saved(r: UserAliasBook.TeachResult) = r as? UserAliasBook.TeachResult.Saved
    private fun refused(r: UserAliasBook.TeachResult) = r is UserAliasBook.TeachResult.Refused

    @Test fun teachAndResolveToTypedCommand() {
        val b = UserAliasBook()
        val r = saved(b.teach("chế độ mát mẻ", "bật điều hòa"))!!
        assertEquals(VoiceCommand.AcPower(true), r.command)
        assertEquals(VoiceCommand.AcPower(true), b.resolve("Chế độ mát mẻ!")?.command)
        assertNull(b.resolve("chế độ mát"))
    }

    @Test fun builtInCommandsCannotBeOverridden() {
        val b = UserAliasBook()
        assertTrue(refused(b.teach("bật điều hòa", "tắt điều hòa")))
        assertTrue(refused(b.teach("mở youtube", "mở nhạc")))
        assertTrue(refused(b.teach("nhiệt độ 22", "nhiệt độ 30")))
    }

    @Test fun reservedPhrasesRefused() {
        val b = UserAliasBook()
        for (p in listOf("hủy", "dừng lại", "đồng ý", "thôi", "tăng nữa", "về như cũ", "làm lại")) {
            assertTrue(p, refused(b.teach(p, "bật điều hòa")))
        }
    }

    @Test fun targetMustBeUnderstood() {
        val b = UserAliasBook()
        assertTrue(refused(b.teach("chế độ ngủ", "làm gì đó không có")))
        assertTrue(b.all().isEmpty())
    }

    @Test fun lengthAndLimit() {
        val b = UserAliasBook()
        assertTrue(refused(b.teach("x", "bật điều hòa")))
        assertTrue(refused(b.teach("a".repeat(41), "bật điều hòa")))
        for (i in 1..UserAliasBook.MAX_ALIASES) assertTrue(saved(b.teach("kiểu số $i", "bật điều hòa")) != null)
        assertTrue(refused(b.teach("kiểu số 99", "bật điều hòa")))
        assertTrue("replace keeps count", saved(b.teach("kiểu số 1", "tắt điều hòa"))!!.replaced)
    }

    @Test fun forgetAndRoundTrip() {
        val b = UserAliasBook()
        b.teach("chế độ mát mẻ", "bật điều hòa")
        b.teach("gió bão", "quạt mức 6")
        val copy = UserAliasBook().apply { load(b.encode()) }
        assertEquals(b.all(), copy.all())
        assertEquals(VoiceCommand.SetFanSpeed(6), copy.resolve("gió bão")?.command)
        assertTrue(copy.forget("Gió bão"))
        assertFalse(copy.forget("gió bão"))
        assertNull(copy.resolve("gió bão"))
        UserAliasBook().apply { load("hỏng\nx\tbật điều hòa\n\t\n") }.let { assertTrue(it.all().isEmpty()) }
    }

    @Test fun voiceTeachForgetPhrases() {
        assertEquals(UserAliasCommands.Request.Teach("chế độ mát mẻ", "bật điều hòa"),
            UserAliasCommands.parse("Khi tôi nói chế độ mát mẻ thì bật điều hòa"))
        assertEquals(UserAliasCommands.Request.Teach("gió bão", "quạt mức 6"),
            UserAliasCommands.parse("khi mình nói gió bão nghĩa là quạt mức 6"))
        assertEquals(UserAliasCommands.Request.Teach("đi làm", "mở google map"),
            UserAliasCommands.parse("dạy cho bạn đi làm nghĩa là mở google map"))
        assertEquals(UserAliasCommands.Request.Forget("gió bão"), UserAliasCommands.parse("quên tên gọi gió bão"))
        assertEquals(UserAliasCommands.Request.Forget("gió bão"), UserAliasCommands.parse("xóa câu gió bão"))
        assertNull(UserAliasCommands.parse("bật điều hòa"))
        assertNull(UserAliasCommands.parse("nói lớn lên"))
    }

    @Test fun aliasCannotTargetTeachCommand() {
        assertTrue(refused(UserAliasBook().teach("học bài", "khi tôi nói a thì bật điều hòa")))
    }
}
