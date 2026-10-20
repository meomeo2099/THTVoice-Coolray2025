package com.voicegeely.app.voice

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the runtime parse chain over docs/reference/voice/VOICE_PHRASES.csv.
 * Guards two things: coverage of intents THT Voice already supports must not drop,
 * and parsed window / drive / regen phrases must match the dataset meaning.
 */
class VoicePhraseCoverageTest {
    private data class Row(val intent: String, val params: String, val phrase: String, val status: String)

    private val rows: List<Row> by lazy {
        val file = listOf("../docs", "docs")
            .map { File(it, "reference/voice/VOICE_PHRASES.csv") }
            .first { it.isFile }
        file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.map(::parseCsvLine)
    }

    @Test
    fun supportedIntentCoverageDoesNotRegress() {
        val have = rows.filter { it.status.startsWith("HAVE") }
        val parsed = have.count { VoiceParseChain.parse(it.phrase) != null }
        assertTrue("parsed $parsed of ${have.size} HAVE phrases; baseline $BASELINE", parsed >= BASELINE)
    }

    @Test
    fun parsedWindowPhrasesHitTheRightWindowsAndDirection() {
        val targets = mapOf(
            "FRONT_LEFT" to setOf(WindowTarget.FRONT_LEFT),
            "FRONT_RIGHT" to setOf(WindowTarget.FRONT_RIGHT),
            "REAR_LEFT" to setOf(WindowTarget.REAR_LEFT),
            "REAR_RIGHT" to setOf(WindowTarget.REAR_RIGHT),
            "FRONT_PAIR" to setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT),
            "REAR_PAIR" to setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT)
        )
        val mismatches = rows.filter { it.intent == "set_window" }.mapNotNull { row ->
            val p = params(row.params)
            val expected = targets[p["target"]] ?: return@mapNotNull null
            val command = VoiceParseChain.parse(row.phrase)?.command as? VoiceCommand.Window ?: return@mapNotNull null
            val expectedOp = if (p["percent"] == "100") WindowOperation.OPEN else WindowOperation.CLOSE
            if (command.targets.toSet() != expected || command.operation != expectedOp) "${row.phrase} -> $command" else null
        }
        assertEquals(mismatches.joinToString("\n"), 0, mismatches.size)
    }

    @Test
    fun parsedDriveAndRegenPhrasesMatchLevels() {
        val mismatches = rows.mapNotNull { row ->
            val level = params(row.params)["level"] ?: return@mapNotNull null
            when (val command = VoiceParseChain.parse(row.phrase)?.command) {
                is VoiceCommand.SetDriveMode -> if (row.intent == "set_driving_mode" && command.mode.name != level) "${row.phrase} -> $command" else null
                is VoiceCommand.SetRegenLevel -> if (row.intent == "set_regen_level" && command.level.name != level) "${row.phrase} -> $command" else null
                else -> null
            }
        }
        assertEquals(mismatches.joinToString("\n"), 0, mismatches.size)
    }

    @Test
    fun fixedRegressions() {
        assertEquals(
            setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT),
            (VoiceParseChain.parse("mở kính trước trái và phải")?.command as VoiceCommand.Window).targets.toSet()
        )
        assertEquals(VoiceCommand.AcPower(true), VoiceParseChain.parse("bật điều hòa lên")?.command)
        assertEquals(VoiceCommand.AcPower(true), VoiceParseChain.parse("bật đều hòa")?.command)
        assertEquals(VoiceCommand.SetDriveMode(DriveMode.SPORT), VoiceParseChain.parse("chế độ thể thao")?.command)
        assertEquals(VoiceCommand.SetDriveMode(DriveMode.ECO), VoiceParseChain.parse("chế độ tiết kiệm")?.command)
        assertEquals(VoiceCommand.HvacEco(true), VoiceParseChain.parse("chế độ tiết kiệm điều hòa")?.command)
        assertEquals(VoiceCommand.SetRegenLevel(RegenLevel.LOW), VoiceParseChain.parse("hồi năng thấp")?.command)
        // "hé" is a crack (10 %), never a full open.
        assertEquals(
            VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.SET_PERCENT, 10),
            VoiceParseChain.parse("hé kính bên lái")?.command
        )
        assertEquals(
            VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.SET_PERCENT, 10),
            VoiceParseChain.parse("hé kính")?.command
        )
        // ASR slips "kênh" / "kín" for "kính", including words ending in accented letters.
        assertEquals(setOf(WindowTarget.FRONT_LEFT), (VoiceParseChain.parse("mở kênh lái")?.command as VoiceCommand.Window).targets)
        assertEquals(setOf(WindowTarget.FRONT_RIGHT), (VoiceParseChain.parse("nâng kín phụ")?.command as VoiceCommand.Window).targets)
        assertEquals(setOf(WindowTarget.FRONT_RIGHT), (VoiceParseChain.parse("mở kênh ghế phụ")?.command as VoiceCommand.Window).targets)
        // Owner decision: a side without front/rear means the front window.
        assertEquals(setOf(WindowTarget.FRONT_LEFT), (VoiceParseChain.parse("mở kính bên trái")?.command as VoiceCommand.Window).targets)
        // Owner decision: "nhỏ nhất" = fan level 1; mute/unmute on the media stream.
        assertEquals(VoiceCommand.SetFanSpeed(1), VoiceParseChain.parse("quạt nhỏ nhất")?.command)
        assertEquals(VoiceCommand.MediaControl(MediaAction.MUTE), VoiceParseChain.parse("tắt âm thanh")?.command)
        assertEquals(VoiceCommand.MediaControl(MediaAction.UNMUTE), VoiceParseChain.parse("bật lại tiếng")?.command)
        // No door-lock aliases and no "cửa" for windows (owner decision).
        assertTrue(VoiceParseChain.parse("chốt cửa")?.command !is VoiceCommand.SetDoorLock)
        assertTrue(VoiceParseChain.parse("mở cửa bên tài")?.command !is VoiceCommand.Window)
        assertTrue(VoiceParseChain.parse("tiết kiệm") == null || VoiceParseChain.parse("tiết kiệm")?.command !is VoiceCommand.SetDriveMode)
    }

    private fun params(raw: String): Map<String, String> =
        raw.split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    private fun parseCsvLine(line: String): Row {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return Row(cells[0], cells[1], cells[2], cells.getOrElse(3) { "" })
    }

    private companion object {
        /** Parsed HAVE phrases on 29/09/2026 (window pass 3); raise it, never lower it. */
        const val BASELINE = 2697
    }
}
