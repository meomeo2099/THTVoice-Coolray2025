package com.voicegeely.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class VinReportPolicyTest {
    @Test
    fun availableVinIsReportedOk() {
        assertEquals("LB37622Z3NX000123" to "ok", VinReportPolicy.report(VinReadResult.Available("LB37622Z3NX000123")))
    }

    @Test
    fun missingVinCarriesTheReason() {
        assertEquals(
            null to "none:CAR_SERVICE_UNAVAILABLE",
            VinReportPolicy.report(VinReadResult.Unavailable(VinUnavailableReason.CAR_SERVICE_UNAVAILABLE))
        )
    }
}
