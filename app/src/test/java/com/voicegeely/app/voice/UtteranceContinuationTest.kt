package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceContinuationTest {
    @Test fun danglingConnectorDropped() {
        assertEquals("mở youtube", UtteranceContinuation.stripDanglingConnector("mở youtube rồi"))
        assertEquals("Mở YouTube", UtteranceContinuation.stripDanglingConnector("Mở YouTube rồi."))
        assertEquals("tắt điều hòa", UtteranceContinuation.stripDanglingConnector("tắt điều hòa và"))
        assertEquals(VoiceCommand.AcPower(false), VoiceParseChain.parse(UtteranceContinuation.stripDanglingConnector("tắt điều hòa xong rồi"))?.command)
        assertEquals("mở youtube rồi tạm dừng nhạc", UtteranceContinuation.stripDanglingConnector("mở youtube rồi tạm dừng nhạc"))
        assertEquals("rồi", UtteranceContinuation.stripDanglingConnector("rồi"))
        // Complete commands that end in "rồi" keep it (and do not wait).
        for (t in listOf("mấy giờ rồi", "xe chạy bao nhiêu km rồi")) {
            assertEquals(t, UtteranceContinuation.stripDanglingConnector(t))
            assertTrue(t, UtteranceContinuation.stopAtEndpoint(t, null, 0, 0))
        }
    }

    @Test fun strippedDanglingNoLongerSearches() {
        assertEquals(VoiceCommand.OpenMedia(MediaContentType.VIDEO, MediaProvider.YOUTUBE),
            VoiceParseChain.parse(UtteranceContinuation.stripDanglingConnector("mở youtube rồi"))?.command)
    }

    @Test fun graceAtEndpoint() {
        assertTrue("plain sentence stops", UtteranceContinuation.stopAtEndpoint("mở youtube", null, 0, 0))
        assertFalse("first endpoint after rồi waits", UtteranceContinuation.stopAtEndpoint("mở youtube rồi", null, 0, 1_000))
        assertFalse("still waiting", UtteranceContinuation.stopAtEndpoint("mở youtube rồi", "mở youtube rồi", 1_000, 2_000))
        assertTrue("grace over", UtteranceContinuation.stopAtEndpoint("mở youtube rồi", "mở youtube rồi", 1_000, 1_000 + UtteranceContinuation.GRACE_MS))
        assertTrue("continued sentence stops normally", UtteranceContinuation.stopAtEndpoint("mở youtube rồi tạm dừng nhạc", "mở youtube rồi", 1_000, 1_500))
    }
}
