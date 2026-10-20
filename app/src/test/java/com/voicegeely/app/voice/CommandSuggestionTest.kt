package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Owner 16/10/2026: "Ý bạn là … phải không?" for a sentence the parser does not understand. */
class CommandSuggestionTest {
    @Test fun everyCatalogPhraseParses() {
        val bad = CommandCatalog.PHRASES.filter { VoiceParseChain.parse(it) == null }
        assertEquals(bad.joinToString(), 0, bad.size)
    }

    @Test fun suggestsTheClosePhrase() {
        val s = CommandSuggestion()
        for ((heard, want) in listOf(
            "bật điêu hoa lên" to "bật điều hòa",
            "đóng tất ca kín" to "đóng tất cả kính",
            "tam dung nhac" to "tạm dừng nhạc",
            "mở dút túp" to "mở youtube",
        )) {
            if (VoiceParseChain.parse(heard) != null) continue  // already understood: no suggestion needed
            assertEquals(heard, want, s.best(heard, CommandCatalog.PHRASES))
        }
    }

    @Test fun farOrLongSentencesGetNothing() {
        val s = CommandSuggestion()
        for (t in listOf("hôm nay trời đẹp quá", "kể chuyện cười đi", "ok", "anh ơi em đói bụng quá muốn ăn phở bò tái chín")) {
            assertNull(t, s.best(t, CommandCatalog.PHRASES))
        }
    }

    @Test fun yesNoAndWindow() {
        val s = CommandSuggestion()
        val q = s.suggest("bật điêu hoa lên", emptyList(), 0)
        if (q == null) return
        assertEquals(CommandSuggestion.Answer.Accept("bật điều hòa"), s.resolve("đúng rồi", 1_000))
        assertNull("one answer only", s.resolve("đúng", 2_000))
        s.suggest("bật điêu hoa lên", emptyList(), 0)
        assertEquals(CommandSuggestion.Answer.Decline, s.resolve("không phải", 1_000))
        s.suggest("bật điêu hoa lên", emptyList(), 0)
        assertNull("too late", s.resolve("ừ", CommandSuggestion.WINDOW_MS + 1))
        s.suggest("bật điêu hoa lên", emptyList(), 0)
        assertNull("another command falls through", s.resolve("mở nhạc", 1_000))
    }

    @Test fun userPhrasesCanBeSuggested() {
        val s = CommandSuggestion(parse = { t -> VoiceParseChain.parse(t) ?: if (t == "chế độ đi làm") ParsedVoiceCommand(t, t, VoiceCommand.AcPower(true)) else null })
        assertNotNull(s.best("chế độ đi lam", listOf("chế độ đi làm")))
    }
}
