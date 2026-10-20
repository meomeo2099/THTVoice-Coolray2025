package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlightHeightCommandParserTest {
    @Test
    fun parsesSupportedLevelsWithDigitsAndVietnameseWords() {
        val cases = mapOf(
            "chỉnh độ cao đèn trước mức 0" to 0,
            "chỉnh độ cao đèn trước mức không" to 0,
            "đặt chiều cao đèn pha cấp một" to 1,
            "chỉnh đèn trước độ cao mức hai" to 2,
            "đặt đèn pha chiều cao về cấp ba" to 3,
            "chỉnh độ cao đèn trước mức bốn" to 4,
            "đặt chiều cao đèn pha mức năm" to 5
        )

        cases.forEach { (text, level) ->
            assertEquals(
                VoiceCommand.SetHeadlightHeight(level),
                HeadlightHeightCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun rejectsOutOfRangeOrIncompleteHeightRequests() {
        listOf(
            "chỉnh độ cao đèn trước mức 6",
            "chỉnh độ cao đèn trước",
            "đèn trước mức 3",
            "chỉnh đèn pha mức 3"
        ).forEach { text ->
            assertNull(text, HeadlightHeightCommandParser.parse(text))
        }
    }

    @Test
    fun doesNotStealOtherLightingCommands() {
        listOf(
            "bật đèn pha",
            "tắt đèn pha",
            "bật pha tự động",
            "tắt pha tự động",
            "đèn viền mức 3",
            "chỉnh độ sáng đèn viền mức 3"
        ).forEach { text ->
            assertNull(text, HeadlightHeightCommandParser.parse(text))
        }
    }
}
