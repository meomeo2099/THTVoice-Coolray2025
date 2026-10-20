package com.voicegeely.app.voice

import com.voicegeely.app.voice.FollowUpContext.Snapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 12/10/2026: Northern / Central / Southern wording and ASR slips for the new and existing
 * commands, plus guards that the new rules do not change anything else.
 */
class RegionalCommandSpeechTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command
    private fun same(variant: String, standard: String) {
        val expected = cmd(standard)
        assertNotNull(standard, expected)
        assertEquals(variant, expected, cmd(variant))
    }

    @Test fun spokenTwoDigitTemperatures() {
        same("hăm hai độ", "22 độ")
        same("hăm lăm độ", "25 độ")
        same("nhiệt độ hăm bốn", "nhiệt độ 24")
        same("hai lăm độ", "25 độ")
        same("nhiệt độ hai lăm", "nhiệt độ 25")
        same("hai mốt độ", "21 độ")
        same("hai mươi mốt độ", "21 độ")
        same("hai tư độ", "24 độ")
        same("hai hai độ", "22 độ")
        same("ba hai độ", "32 độ")
        same("một tám độ", "18 độ")
        same("chỉnh hăm ba độ", "23 độ")
    }

    @Test fun temperatureWithVerbOnly() {
        same("để 24 độ", "nhiệt độ 24")
        same("cho 24 độ", "nhiệt độ 24")
        same("chỉnh xuống 20 độ", "nhiệt độ 20")
        same("hạ xuống 20 độ", "nhiệt độ 20")
        same("lên 26 độ", "nhiệt độ 26")
        same("giảm 2 độ", "giảm nhiệt độ 2 độ")
        same("tăng lên 1 độ", "tăng nhiệt độ 1 độ")
        same("hạ bớt 3 độ", "giảm nhiệt độ 3 độ")
        assertEquals("by two or three → the smaller step", VoiceCommand.AdjustTemperature(-2f), cmd("giảm hai ba độ"))
        assertNull("could mean 'by 20': not guessed", cmd("giảm 20 độ"))
    }

    @Test fun feelingPhrases() {
        for (t in listOf("lạnh quá", "rét quá", "lạnh quá à", "trời lạnh quá", "lạnh quá trời", "thấy lạnh ghê")) same(t, "tăng nhiệt độ")
        for (t in listOf("nóng quá", "nực quá", "oi quá", "bức quá", "nóng quá trời", "trời nóng quá", "nóng ghê")) same(t, "giảm nhiệt độ")
        assertNull("not a whole feeling sentence", cmd("nước lạnh quá"))
    }

    @Test fun fanAndWindowWording() {
        for (t in listOf("quạt yếu lại", "quạt yếu đi", "gió yếu lại", "gió nhẹ đi", "quạt nhỏ bớt")) same(t, "giảm quạt")
        same("gió cấp 3", "quạt mức 3")
        same("gió nấc 4", "quạt mức 4")
        same("quay kính tài xuống", "mở kính bên lái")
        same("quay kiếng sau lên", "đóng kính sau")
    }

    @Test fun mediaWording() {
        for (t in listOf("mở bài hát", "mở nhạc lên", "bật nhạc lên coi", "bật nhạc coi", "mở nhạc đi", "phát ca nhạc")) same(t, "mở nhạc")
        for (t in listOf("bài kế", "qua bài", "đổi bài", "sang bài khác")) same(t, "bài tiếp theo")
        for (t in listOf("lùi bài", "lui bài", "bài hồi nãy")) same(t, "bài trước")
        same("tạm ngưng nhạc", "tạm dừng nhạc")
        for (t in listOf("vặn nhỏ", "nhỏ tiếng lại", "tiếng nhỏ lại", "âm lượng nhỏ xuống", "loa nhỏ lại")) same(t, "giảm âm lượng")
        for (t in listOf("vặn to", "vặn lớn", "âm lượng lớn lên", "loa to lên")) same(t, "tăng âm lượng")
        same("mở youtube lẹ lên", "mở youtube")
        same("dẫn đường tới bến xe đi", "dẫn đường tới bến xe")
    }

    @Test fun questions() {
        same("nay thứ mấy", "hôm nay thứ mấy")
        same("thứ mấy", "hôm nay thứ mấy")
        same("đi được bao xa nữa", "quãng đường còn lại")
        same("phạm vi còn lại", "quãng đường còn lại")
        same("xe chạy bao nhiêu cây số rồi", "tổng quãng đường")
        same("xe chạy bao nhiêu km rồi", "tổng quãng đường")
    }

    @Test fun guardsUnchanged() {
        // HVAC contract (AGENTS §4): 16..32 + HI + LO, nothing near "2" becomes HI.
        for (n in 16..32) assertEquals("$n", VoiceCommand.SetTemperature(n.toFloat()), cmd("nhiệt độ $n"))
        assertEquals(VoiceCommand.TemperatureHigh, cmd("nhiệt độ cao nhất"))
        assertEquals(VoiceCommand.TemperatureLow, cmd("nhiệt độ thấp nhất"))
        assertEquals(VoiceCommand.TemperatureHigh, cmd("hi"))
        assertEquals(VoiceCommand.TemperatureLow, cmd("lo"))
        for (t in listOf("hai", "hai hai", "hai mươi hai", "hai lăm", "hăm hai")) {
            val c = cmd(t)
            assertTrue("$t -> $c", c != VoiceCommand.TemperatureHigh)
        }
        // Charge "ngay" keeps its meaning; bare window / volume words stay unparsed (owner decision).
        assertNotNull(VehicleStatusQueryParser.parse("bắt đầu sạc xe ngay"))
        assertNull(cmd("đóng kiếng lại"))
        assertNull(cmd("to lên"))
        assertNull(cmd("hai ba"))
        // Numbers outside a climate sentence stay as they were.
        assertEquals(VoiceCommand.SetFanSpeed(2), cmd("quạt mức hai"))
        assertEquals("mở nhạc nhẹ", VoiceTextNormalizer.normalize("mở nhạc nhẹ"))
    }

    @Test fun followUpRegionalAndSlips() {
        val ok = CommandExecutionResult.Confirmed(VerificationSource.VEHICLE_READ_BACK)
        fun ctx(c: VoiceCommand, before: Snapshot? = null) = FollowUpContext().apply { record(c, before, ok, 0) }
        val fan = ctx(VoiceCommand.AdjustFanSpeed(1), Snapshot.Fan(2))
        for (t in listOf("tang nữa", "tăng nựa", "tăng nên nữa", "mãnh nữa", "bự thêm chút xíu nữa", "tăng thêm tí nữa nghen", "còn tăng nữa", "tăng nữa hỉ"))
            assertEquals(t, VoiceCommand.AdjustFanSpeed(1), fan.resolve(t, 10)?.command)
        for (t in listOf("dảm nữa", "giám nữa", "bợt nữa", "yêu nữa", "giảm nữa rứa"))
            assertEquals(t, VoiceCommand.AdjustFanSpeed(-1), fan.resolve(t, 10)?.command)
        for (t in listOf("dìa như cũ", "về như củ", "trả dề như cũ", "hoàn tát", "như hồi nãy", "để như cũ đi"))
            assertEquals(t, VoiceCommand.SetFanSpeed(2), fan.resolve(t, 10)?.command)
        for (t in listOf("nàm nại", "làm lại", "thêm nần nữa"))
            assertEquals(t, VoiceCommand.AdjustFanSpeed(1), fan.resolve(t, 10)?.command)
        val temp = ctx(VoiceCommand.SetTemperature(24f))
        assertEquals(VoiceCommand.AdjustTemperature(1f), temp.resolve("vẫn còn nạnh quá", 10)?.command)
        assertEquals(VoiceCommand.AdjustTemperature(-1f), temp.resolve("lóng quá", 10)?.command)
    }

    @Test fun teachAndMultiRegional() {
        assertEquals(UserAliasCommands.Request.Teach("gió bão", "quạt mức 6"), UserAliasCommands.parse("hễ tui nói gió bão thì quạt mức 6"))
        assertEquals(UserAliasCommands.Request.Teach("đi làm", "mở google map"), UserAliasCommands.parse("dậy cho bạn đi làm nghĩa là mở google map"))
        assertEquals(UserAliasCommands.Request.Forget("gió bão"), UserAliasCommands.parse("quen tên gọi gió bão"))
        for (c in listOf("dồi", "rùi", "xong dồi", "dà", "dới lại"))
            assertEquals(c, 2, (MultiCommandPlan.plan("tắt điều hòa $c mở nhạc", VoiceParseChain::parse) as MultiCommandPlan.Result.Steps).steps.size)
        assertNull("'với' alone is not a connector", MultiCommandPlan.plan("mở nhạc với youtube", VoiceParseChain::parse))
    }

    @Test fun phraseCorpusMeaningUnchanged() {
        // Every IHU629G sample phrase that parsed before still parses (coverage test keeps the
        // count); here: none of them turns into a several-command plan.
        val file = listOf("../docs", "docs").map { File(it, "reference/voice/VOICE_PHRASES.csv") }.first { it.isFile }
        val multi = file.readLines(Charsets.UTF_8).drop(1).mapNotNull { line ->
            val phrase = line.split(',').getOrNull(2)?.trim('"') ?: return@mapNotNull null
            phrase.takeIf { MultiCommandPlan.plan(it, VoiceParseChain::parse) != null }
        }
        assertEquals(multi.joinToString("\n"), 0, multi.size)
    }
}
