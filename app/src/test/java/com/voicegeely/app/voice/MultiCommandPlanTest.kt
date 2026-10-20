package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiCommandPlanTest {
    private fun steps(text: String) =
        (MultiCommandPlan.plan(text, VoiceParseChain::parse) as? MultiCommandPlan.Result.Steps)?.steps?.map { it.command }

    @Test fun twoCommandsWithConnectors() {
        for (c in listOf("rồi", "và", "sau đó", "xong", "xong rồi", "đồng thời", "với lại")) {
            val s = steps("tắt điều hòa $c mở nhạc")
            assertEquals(c, listOf(VoiceCommand.AcPower(false), VoiceCommand.OpenMedia(MediaContentType.MUSIC)), s)
        }
        assertEquals(2, steps("quạt mức 3, mở youtube")!!.size)
    }

    @Test fun verbCarriesToNextPart() {
        val s = steps("mở kính lái và kính phụ")!!
        assertEquals(2, s.size)
        assertTrue(s.all { it is VoiceCommand.Window && it.operation == WindowOperation.OPEN })
    }

    @Test fun anyUnparsedPartMeansNotMulti() {
        assertNull(steps("chỉ đường đến bến thành và chợ lớn"))
        assertNull(steps("tìm bài hát anh và em"))
        assertNull(steps("bật điều hòa và cái gì đó"))
        assertNull(steps("bật điều hòa"))
    }

    @Test fun moreThanFourRefused() {
        val r = MultiCommandPlan.plan("tắt điều hòa rồi mở nhạc rồi quạt mức 3 rồi mở youtube rồi 22 độ", VoiceParseChain::parse)
        assertTrue(r is MultiCommandPlan.Result.TooMany)
        assertEquals(4, steps("tắt điều hòa rồi mở nhạc rồi quạt mức 3 rồi 22 độ")!!.size)
    }

    @Test fun duplicateConsecutiveCollapsed() {
        assertNull("same command twice is one command", steps("mở nhạc rồi mở nhạc"))
    }

    private val ok = CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK)
    private fun p(c: VoiceCommand) = ParsedVoiceCommand("t", "t", c)

    @Test fun runnerRunsInOrderAndSummarises() {
        val ran = ArrayList<VoiceCommand>()
        val out = MultiStepRunner.run(listOf(p(VoiceCommand.AcPower(false)), p(VoiceCommand.SetFanSpeed(3))), { MultiStepRunner.Gate.Allow }) {
            ran += it.command; ok
        }
        assertEquals(listOf(VoiceCommand.AcPower(false), VoiceCommand.SetFanSpeed(3)), ran)
        assertNull(out.stoppedAt)
        assertEquals(2, out.results.size)
    }

    @Test fun runnerStopsOnFailureAndReportsLeft() {
        val ran = ArrayList<VoiceCommand>()
        val out = MultiStepRunner.run(listOf(p(VoiceCommand.AcPower(false)), p(VoiceCommand.SetFanSpeed(3)), p(VoiceCommand.OpenApp(AppTarget.YOUTUBE))),
            { MultiStepRunner.Gate.Allow }) {
            ran += it.command
            if (it.command is VoiceCommand.AcPower) CommandExecutionResult.Failed("Xe không chấp nhận.") else ok
        }
        assertEquals(1, ran.size)
        assertEquals(0, out.stoppedAt)
        assertTrue(out.summary, out.summary.contains("Chưa làm 2 lệnh còn lại."))
        assertTrue("failure must not sound like success", !out.summary.startsWith("Đã"))
    }

    @Test fun runnerUnverifiedContinuesExceptionBecomesFailed() {
        val out = MultiStepRunner.run(listOf(p(VoiceCommand.SetFanSpeed(3)), p(VoiceCommand.AcPower(true)), p(VoiceCommand.SetFanSpeed(5))),
            { MultiStepRunner.Gate.Allow }) {
            when (it.command) {
                is VoiceCommand.SetFanSpeed -> if ((it.command as VoiceCommand.SetFanSpeed).level == 3) CommandExecutionResult.AcceptedUnverified() else ok
                else -> throw IllegalStateException("boom")
            }
        }
        assertEquals(1, out.stoppedAt)
        assertTrue(out.results[1] is CommandExecutionResult.Failed)
    }

    @Test fun runnerStopsBeforeConfirmationOrBlock() {
        var ran = 0
        val out = MultiStepRunner.run(listOf(p(VoiceCommand.AcPower(false)), p(VoiceCommand.SetDoorLock(false))),
            { if (it is VoiceCommand.SetDoorLock) MultiStepRunner.Gate.NeedsConfirmation else MultiStepRunner.Gate.Allow }) { ran++; ok }
        assertEquals(1, ran)
        assertEquals(1, out.stoppedAt)
        assertTrue(out.summary.contains("cần xác nhận"))
        val blocked = MultiStepRunner.run(listOf(p(VoiceCommand.SetDoorLock(false)), p(VoiceCommand.AcPower(false))),
            { if (it is VoiceCommand.SetDoorLock) MultiStepRunner.Gate.Blocked("Xe đang chạy.") else MultiStepRunner.Gate.Allow }) { ran++; ok }
        assertEquals(1, ran)
        assertTrue(blocked.summary.contains("Xe đang chạy.") && blocked.summary.contains("Chưa làm 1 lệnh còn lại."))
    }
}
