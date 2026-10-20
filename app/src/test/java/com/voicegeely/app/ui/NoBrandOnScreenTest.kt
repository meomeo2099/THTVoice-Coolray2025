package com.voicegeely.app.ui

import com.voicegeely.app.kws.WakePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Owner decision 15/10/2026: the car-maker name is never shown on screen; wake phrases read "Ge-ly". */
class NoBrandOnScreenTest {
    @Test fun wakePhraseLabelsUseGeLy() {
        assertEquals(listOf("Hey THT", "Hello Ge-ly", "Hey Ge-ly"), WakePhrase.values().map { it.displayLabel })
        WakePhrase.values().forEach { assertFalse(it.displayLabel, it.displayLabel.contains("geely", ignoreCase = true)) }
    }

    @Test fun publicTextReplacesTheName() {
        assertEquals("Đã nghe Hey Ge-ly", PublicText.scrub("Đã nghe Hey Geely"))
        assertEquals("Hello Ge-ly đang chờ lệnh", PublicText.scrub("Hello Geely đang chờ lệnh"))
    }
}
