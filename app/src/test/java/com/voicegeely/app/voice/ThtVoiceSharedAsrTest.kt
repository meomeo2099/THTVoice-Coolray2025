package com.voicegeely.app.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Owner's shared language cases; no external vehicle property IDs or execution code. */
class ThtVoiceSharedAsrTest {
    @Test fun importedPhrasesHaveTheSameMeaningAsSupportedCanonicalCommands() {
        val file = listOf("docs", "../docs").map { File(it, "reference/voice/THTVOICE_SHARED_ASR_CASES.csv") }.first { it.isFile }
        val rows = file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
        assertEquals(20, rows.size)
        for (row in rows) {
            val cells = row.split(',')
            val heard = cells[1]
            val canonical = cells[2]
            val expected = VoiceParseChain.parse(canonical)?.command
            assertNotNull(canonical, expected)
            assertEquals(heard, expected, VoiceParseChain.parse(heard)?.command)
        }
    }

    @Test fun carWindowNounDoesNotConsumeRoofOrFreeformWindow() {
        assertEquals("mở cửa sổ trời", VoiceTextNormalizer.normalize("mở cửa sổ trời"))
        assertEquals("mở cửa sổ tự do", VoiceTextNormalizer.normalize("mở cửa sổ tự do"))
        assertEquals("đóng kính", VoiceTextNormalizer.normalize("đóng cửa sổ"))
        assertNotNull(WindowClarification().check("đóng cửa sổ", 1))
    }

    @Test fun importedSlipsLeaveUnrelatedNamesAndOrdinaryWordsAlone() {
        for (text in listOf("quảng cáo", "tăng quảng cáo", "xây nhà", "xây kinh doanh", "đi đến đường sơn", "đi đến khu cấm trại", "mở khe núi", "tôi đang nghĩ ngợi", "cấm đỗ xe", "kiểm tra hoạt động của xe")) {
            assertEquals(text, text, VoiceTextNormalizer.normalize(text))
        }
    }
}
