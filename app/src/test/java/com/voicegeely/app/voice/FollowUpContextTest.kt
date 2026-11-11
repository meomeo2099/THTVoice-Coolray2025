package com.voicegeely.app.voice

import com.voicegeely.app.voice.FollowUpContext.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowUpContextTest {
    private val ok = CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK)
    private fun ctx(cmd: VoiceCommand, before: Snapshot? = null, result: CommandExecutionResult = ok, at: Long = 0L) =
        FollowUpContext().apply { record(cmd, before, result, at) }
    private fun FollowUpContext.cmd(text: String, at: Long = 1_000L) = resolve(text, at)?.command

    @Test fun fanMoreLessAndSameDirection() {
        val c = ctx(VoiceCommand.AdjustFanSpeed(1))
        for (t in listOf("tăng nữa", "Tăng nữa đi.", "mạnh nữa", "thêm nữa", "to thêm chút nữa", "lên nữa nha", "tăng lên nữa", "cho tôi tăng nữa")) {
            assertEquals(t, VoiceCommand.AdjustFanSpeed(1), c.cmd(t))
        }
        for (t in listOf("giảm nữa", "yếu nữa", "bớt nữa đi", "nhỏ nữa", "hạ xuống nữa", "giảm thêm tí nữa")) {
            assertEquals(t, VoiceCommand.AdjustFanSpeed(-1), c.cmd(t))
        }
        assertEquals(VoiceCommand.AdjustFanSpeed(1), c.cmd("nữa"))
        assertEquals(VoiceCommand.AdjustFanSpeed(-1), ctx(VoiceCommand.AdjustFanSpeed(-2)).cmd("thêm nữa").let { it as VoiceCommand.AdjustFanSpeed }.let { VoiceCommand.AdjustFanSpeed(it.delta / 2) })
    }

    @Test fun stepFollowsPreviousAdjustment() {
        assertEquals(VoiceCommand.AdjustTemperature(2f), ctx(VoiceCommand.AdjustTemperature(-2f)).cmd("tăng nữa"))
        assertEquals(VoiceCommand.AdjustTemperature(-0.5f), ctx(VoiceCommand.SetTemperature(22f)).cmd("giảm nữa"))
        assertEquals(VoiceCommand.AdjustFanSpeed(2), ctx(VoiceCommand.AdjustFanSpeed(2)).cmd("tăng nữa"))
    }

    @Test fun setValueHasNoDirectionForBareNua() {
        assertNull(ctx(VoiceCommand.SetFanSpeed(4)).cmd("nữa"))
        assertNull(ctx(VoiceCommand.SetTemperature(22f)).cmd("thêm nữa"))
    }

    @Test fun thermalWordsOnlyForTemperature() {
        val t = ctx(VoiceCommand.SetTemperature(24f))
        assertEquals(VoiceCommand.AdjustTemperature(-0.5f), t.cmd("mát nữa"))
        assertEquals(VoiceCommand.AdjustTemperature(-0.5f), t.cmd("vẫn còn nóng quá"))
        assertEquals(VoiceCommand.AdjustTemperature(0.5f), t.cmd("ấm hơn chút nữa"))
        assertEquals(VoiceCommand.AdjustTemperature(0.5f), t.cmd("lạnh quá"))
        val f = ctx(VoiceCommand.AdjustFanSpeed(1))
        assertNull(f.cmd("mát nữa"))
        assertNull(f.cmd("nóng quá"))
    }

    @Test fun backRestoresReadValueOnly() {
        assertEquals(VoiceCommand.SetFanSpeed(2), ctx(VoiceCommand.SetFanSpeed(6), Snapshot.Fan(2)).cmd("về như cũ"))
        assertEquals(VoiceCommand.SetTemperature(23.5f), ctx(VoiceCommand.TemperatureLow, Snapshot.Temperature(23.5f)).cmd("trả lại như cũ đi"))
        assertEquals(VoiceCommand.SetFanSpeed(2), ctx(VoiceCommand.SetFanSpeed(6), Snapshot.Fan(2)).cmd("hoàn tác"))
        assertNull("no reading → nothing", ctx(VoiceCommand.SetFanSpeed(6)).cmd("về như cũ"))
        assertNull("volume has no snapshot", ctx(VoiceCommand.MediaControl(MediaAction.VOLUME_UP)).cmd("về như cũ"))
    }

    @Test fun againRepeatsLastCommand() {
        assertEquals(VoiceCommand.AdjustTemperature(1f), ctx(VoiceCommand.AdjustTemperature(1f)).cmd("làm lại"))
        assertEquals(VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN), ctx(VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN)).cmd("thêm lần nữa"))
    }

    @Test fun volumeFollowUp() {
        val v = ctx(VoiceCommand.MediaControl(MediaAction.VOLUME_UP))
        assertEquals(VoiceCommand.MediaControl(MediaAction.VOLUME_UP), v.cmd("to nữa"))
        assertEquals(VoiceCommand.MediaControl(MediaAction.VOLUME_DOWN), v.cmd("nhỏ nữa"))
    }

    @Test fun onlyConfirmedOpensContext() {
        assertNull(ctx(VoiceCommand.AdjustFanSpeed(1), result = CommandExecutionResult.AcceptedUnverified()).cmd("tăng nữa"))
        assertNull(ctx(VoiceCommand.AdjustFanSpeed(1), result = CommandExecutionResult.Failed("x")).cmd("tăng nữa"))
        assertNull(ctx(VoiceCommand.AdjustFanSpeed(1), result = CommandExecutionResult.Rejected("x")).cmd("tăng nữa"))
    }

    @Test fun otherCommandOrFailureClearsContext() {
        val c = ctx(VoiceCommand.AdjustFanSpeed(1))
        c.record(VoiceCommand.OpenApp(AppTarget.YOUTUBE), null, ok, 500)
        assertNull(c.cmd("tăng nữa"))
        val d = ctx(VoiceCommand.AdjustFanSpeed(1))
        d.record(VoiceCommand.AdjustFanSpeed(1), null, CommandExecutionResult.Failed("x"), 500)
        assertNull(d.cmd("tăng nữa"))
    }

    @Test fun expiresAfterThreeMinutes() {
        val c = ctx(VoiceCommand.AdjustFanSpeed(1), at = 0)
        assertEquals(VoiceCommand.AdjustFanSpeed(1), c.cmd("tăng nữa", at = FollowUpContext.TTL_MS))
        assertNull(c.cmd("tăng nữa", at = FollowUpContext.TTL_MS + 1))
        assertNull("stays expired", c.cmd("tăng nữa", at = 10))
    }

    @Test fun fullCommandsAreNotFollowUps() {
        val c = ctx(VoiceCommand.AdjustFanSpeed(1))
        for (t in listOf("tăng nhiệt độ", "mở cửa sổ nữa", "tăng quạt lên mức 5", "giảm âm lượng", "mở nhạc", "nữa tiếng nữa", "đóng kính nữa", "tăng nữa rồi mở nhạc")) {
            assertNull(t, c.cmd(t))
        }
    }

    @Test fun noContextNoFollowUp() {
        assertNull(FollowUpContext().resolve("tăng nữa", 0))
    }

    @Test fun coordinatorRecordsSnapshotAndResult() = runBlocking {
        val follow = FollowUpContext()
        val exec = object : VoiceCommandExecutor { override suspend fun execute(command: VoiceCommand) = ok }
        val coordinator = VoiceCommandCoordinator(exec, VoiceSpeechSink { }, follow, { Snapshot.Fan(3) }, { 100L })
        coordinator.handle(ParsedVoiceCommand("quạt 6", "quạt 6", VoiceCommand.SetFanSpeed(6)), VoiceResponseMode.SILENT)
        assertEquals(VoiceCommand.SetFanSpeed(3), follow.resolve("về như cũ", 200L)?.command)
    }

    @Test fun executorExceptionClearsContext() = runBlocking {
        val follow = ctx(VoiceCommand.AdjustFanSpeed(1))
        val boom = object : VoiceCommandExecutor { override suspend fun execute(command: VoiceCommand): CommandExecutionResult = throw IllegalStateException("x") }
        VoiceCommandCoordinator(boom, VoiceSpeechSink { }, follow).handle(
            ParsedVoiceCommand("t", "t", VoiceCommand.AdjustFanSpeed(1)), VoiceResponseMode.SILENT)
        assertNull(follow.resolve("tăng nữa", 1_000L))
    }
}
