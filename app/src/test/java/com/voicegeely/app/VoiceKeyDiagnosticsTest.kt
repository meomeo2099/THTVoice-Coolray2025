package com.voicegeely.app

import com.voicegeely.app.VoiceKeyDiagnostics.Kind
import com.voicegeely.app.VoiceKeyDiagnostics.Listener
import com.voicegeely.app.VoiceKeyDiagnostics.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceKeyDiagnosticsTest {
    private val self = "com.tht.voice"
    private val tht = Listener(self, "com.voicegeely.app.VoiceKeyReceiver", "THT Voice", Kind.VOICE_KEY_RECEIVER, 999)
    private val projection = Listener("com.ecarx.carplay", "com.ecarx.carplay.KeyReceiver", "Kết nối điện thoại", Kind.VOICE_KEY_RECEIVER)
    private val radio = Listener("com.ecarx.radio", "com.ecarx.radio.KeyReceiver", "Radio", Kind.VOICE_KEY_RECEIVER)

    private fun report(vararg l: Listener, assistant: String? = null, vis: String? = null) =
        Report(self, l.toList(), assistant, vis)

    @Test fun packageOfFlattenedComponent() {
        assertEquals("com.tht.voice", VoiceKeyDiagnostics.packageOf("com.tht.voice/com.voicegeely.app.VoiceAssistantService"))
        assertEquals("a.b", VoiceKeyDiagnostics.packageOf("a.b"))
        assertNull(VoiceKeyDiagnostics.packageOf(""))
        assertNull(VoiceKeyDiagnostics.packageOf(null))
    }

    @Test fun selfIsNeverASuspect() {
        val s = VoiceKeyDiagnostics.suspects(report(tht, radio, projection))
        assertFalse(s.any { it.packageName == self })
        assertEquals("projection-looking app listed first", "com.ecarx.carplay", s.first().packageName)
    }

    @Test fun onlyThtMeansNoOtherApps() {
        val text = VoiceKeyDiagnostics.format(report(tht, assistant = "$self/x"))
        assertTrue(text.contains("Trợ lý mặc định: THT Voice"))
        assertTrue(text.contains("Không thấy app nào khác"))
    }

    @Test fun projectionReceiverIsFlagged() {
        val text = VoiceKeyDiagnostics.format(report(tht, projection))
        assertTrue(text.contains("com.ecarx.carplay"))
        assertTrue(text.contains("có thể là app chiếu điện thoại"))
        assertTrue(text.contains("Sao chép"))
        assertTrue(text.contains("Trợ lý mặc định: chưa đặt"))
    }

    @Test fun otherAssistantGetsSettingsAdvice() {
        val r = report(tht, assistant = "com.google.android.googlequicksearchbox/x")
        assertTrue(VoiceKeyDiagnostics.assistantIsOtherApp(r))
        assertTrue(VoiceKeyDiagnostics.format(r).contains("Mở cài đặt"))
    }

    @Test fun voiceInteractionServiceUsedWhenAssistantUnset() {
        assertFalse(VoiceKeyDiagnostics.assistantIsOtherApp(report(vis = "$self/x")))
        assertTrue(VoiceKeyDiagnostics.assistantIsOtherApp(report(vis = "other.app/x")))
        assertFalse(VoiceKeyDiagnostics.assistantIsOtherApp(report()))
    }

    @Test fun duplicateEntriesCollapsed() {
        val s = VoiceKeyDiagnostics.suspects(report(projection, projection.copy(className = "Other")))
        assertEquals(1, s.size)
    }
}
