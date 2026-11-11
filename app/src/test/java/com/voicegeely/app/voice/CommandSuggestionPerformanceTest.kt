package com.voicegeely.app.voice

import org.junit.Assert.*
import org.junit.Test

class CommandSuggestionPerformanceTest {
    private fun parsed(text: String, on: Boolean = true) = ParsedVoiceCommand(text, text, VoiceCommand.AcPower(on))

    @Test fun doesNotParseDistantCatalogEntries() {
        val calls = mutableListOf<String>()
        val suggestion = CommandSuggestion(parse = { calls += it; parsed(it) })
        val phrases = listOf("bật điều hòa") + (1..200).map { "chức năng hoàn toàn khác $it" }
        assertEquals("bật điều hòa", suggestion.best("bật điều hòa", phrases))
        assertEquals(listOf("bật điều hòa"), calls)
    }
    @Test fun unparseableBestPhraseCanFallThroughToNextClosePhrase() {
        val suggestion = CommandSuggestion(parse = { if (it == "mở kính lái") null else parsed(it) })
        assertEquals("mở kính láii", suggestion.best("mở kính lái", listOf("mở kính lái", "mở kính láii")))
    }
    @Test fun closeDifferentCommandsRemainAmbiguous() {
        val suggestion = CommandSuggestion(parse = { parsed(it, it.endsWith("a")) })
        assertNull(suggestion.best("điều hòa c", listOf("điều hòa a", "điều hòa b")))
    }
    @Test fun interruptedSearchCannotLeaveAPendingConfirmation() {
        val suggestion = CommandSuggestion(parse = { Thread.currentThread().interrupt(); parsed(it) })
        try {
            assertNull(suggestion.suggest("bật điều hòa", emptyList(), 0))
            assertNull(suggestion.resolve("đúng", 100))
        } finally { Thread.interrupted() }
    }
}
