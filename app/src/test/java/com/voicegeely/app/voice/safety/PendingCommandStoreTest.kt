package com.voicegeely.app.voice.safety

import com.voicegeely.app.voice.DriveMode
import com.voicegeely.app.voice.ParsedVoiceCommand
import com.voicegeely.app.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PendingCommandStoreTest {
    @Test fun expiresAfterEightSeconds() {
        var now = 1_000L
        val store = PendingCommandStore(clockMs = { now })
        val parsed = ParsedVoiceCommand("chuyển sport", "chuyển sport", VoiceCommand.SetDriveMode(DriveMode.SPORT))
        store.put(parsed, VehicleMotionState(40f, 8))
        assertNotNull(store.peek())
        now += 7_999L
        assertNotNull(store.peek())
        now += 1L
        assertNull(store.peek())
    }

    @Test fun newerPendingCommandReplacesOlderOne() {
        var now = 5_000L
        val store = PendingCommandStore(clockMs = { now })
        val first = store.put(
            ParsedVoiceCommand("eco", "eco", VoiceCommand.SetDriveMode(DriveMode.ECO)),
            VehicleMotionState(30f, 8)
        )
        now += 100L
        val second = store.put(
            ParsedVoiceCommand("sport", "sport", VoiceCommand.SetDriveMode(DriveMode.SPORT)),
            VehicleMotionState(30f, 8)
        )
        assertNotEquals(first.commandHash, second.commandHash)
        assertEquals(second.commandHash, store.peek()?.commandHash)
    }

    @Test fun takeConsumesPendingCommand() {
        val store = PendingCommandStore(clockMs = { 10L })
        store.put(
            ParsedVoiceCommand("sport", "sport", VoiceCommand.SetDriveMode(DriveMode.SPORT)),
            VehicleMotionState(20f, 8)
        )
        assertNotNull(store.take())
        assertNull(store.peek())
    }
}
