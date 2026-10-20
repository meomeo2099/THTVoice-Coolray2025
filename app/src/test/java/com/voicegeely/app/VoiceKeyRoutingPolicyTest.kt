package com.voicegeely.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceKeyRoutingPolicyTest {
    private val expectedAction = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"

    @Test
    fun unrelatedBroadcastIsIgnoredWithoutLicenseLookup() {
        var licenseChecks = 0
        val decision = VoiceKeyRoutingPolicy.decide(
            incomingAction = "other.action",
            expectedAction = expectedAction
        ) {
            licenseChecks += 1
            true
        }

        assertEquals(VoiceKeyRouteDecision.IGNORE, decision)
        assertEquals(0, licenseChecks)
    }

    @Test
    fun nullActionIsIgnoredWithoutLicenseLookup() {
        var licenseChecks = 0
        val decision = VoiceKeyRoutingPolicy.decide(null, expectedAction) {
            licenseChecks += 1
            true
        }

        assertEquals(VoiceKeyRouteDecision.IGNORE, decision)
        assertEquals(0, licenseChecks)
    }

    @Test
    fun acceptedKeyWithInactiveLicenseCanOnlyRouteToActivation() {
        var licenseChecks = 0
        val decision = VoiceKeyRoutingPolicy.decide(expectedAction, expectedAction) {
            licenseChecks += 1
            false
        }

        assertEquals(VoiceKeyRouteDecision.ACTIVATION, decision)
        assertEquals(1, licenseChecks)
    }

    @Test
    fun acceptedKeyWithActiveLicenseRoutesToVoiceService() {
        var licenseChecks = 0
        val decision = VoiceKeyRoutingPolicy.decide(expectedAction, expectedAction) {
            licenseChecks += 1
            true
        }

        assertEquals(VoiceKeyRouteDecision.VOICE_SERVICE, decision)
        assertEquals(1, licenseChecks)
    }
}
