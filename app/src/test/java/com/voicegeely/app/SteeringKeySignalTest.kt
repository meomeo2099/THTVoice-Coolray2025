package com.voicegeely.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringKeySignalTest {
    @Test fun bothActionSpellingsAreVoiceKey() {
        assertTrue(VoiceKeyActions.isVoiceKey("ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"))
        assertTrue(VoiceKeyActions.isVoiceKey("ecarx.intent.actiOn.ECARX_KEY_RVOICEASSIST_EVENT"))
        assertFalse(VoiceKeyActions.isVoiceKey("ecarx.intent.action.ECARX_KEY_OTHER"))
        assertFalse(VoiceKeyActions.isVoiceKey(null))
    }

    @Test fun routingAcceptsVariantAndStillChecksLicense() {
        val expected = VoiceKeyActions.ECARX
        assertEquals(VoiceKeyRouteDecision.VOICE_SERVICE,
            VoiceKeyRoutingPolicy.decide(VoiceKeyActions.ECARX_CASE_VARIANT, expected) { true })
        assertEquals(VoiceKeyRouteDecision.ACTIVATION,
            VoiceKeyRoutingPolicy.decide(VoiceKeyActions.ECARX_CASE_VARIANT, expected) { false })
        assertEquals(VoiceKeyRouteDecision.IGNORE,
            VoiceKeyRoutingPolicy.decide("android.intent.action.BOOT_COMPLETED", expected) { true })
    }
}
