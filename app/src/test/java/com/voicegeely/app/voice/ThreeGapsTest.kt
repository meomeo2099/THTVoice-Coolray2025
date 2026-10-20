package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 13/10/2026: car features are not app names, defog aliases, "mở cốp" asks which trunk. */
class ThreeGapsTest {
    private fun cmd(t: String) = VoiceParseChain.parse(t)?.command

    @Test fun carFeaturesAreNotApps() {
        for (t in listOf("bật cruise", "bật cruise control", "bật ga tự động", "bật lọc không khí", "mở camera lùi",
            "bật sưởi ghế", "mở cửa sổ trời", "bật giữ làn", "gập gương")) {
            assertTrue("$t -> ${cmd(t)}", cmd(t) !is VoiceCommand.OpenInstalledApp)
        }
        assertEquals(VoiceCommand.OpenInstalledApp("zalo"), cmd("mở zalo"))
        assertEquals(VoiceCommand.OpenInstalledApp("camera 360"), cmd("mở camera 360"))
    }

    @Test fun defogAliases() {
        for (t in listOf("hút ẩm kính", "làm khô kính", "khử sương", "khử sương mù kính chắn gió", "bật khử sương"))
            assertEquals(t, VoiceCommand.Defrost(DefrostZone.FRONT, true), cmd(t))
    }

    @Test fun coolrayTrunkIsTheTailgate() {
        // Coolray has only a tailgate: "mở cốp" runs "mở cốp sau" instead of asking which one.
        val c = TrunkClarification()
        for (t in listOf("mở cốp", "mở cốp xe", "mở cái cốp", "mở cốt")) {
            assertEquals(t, TrunkClarification.Reply.Rewrite("mở cốp sau"), c.check(t, 0))
        }
        assertNull(c.resolve("sau", 1_000))
    }

    @Test fun trunkOnlyForAmbiguousSentences() {
        val c = TrunkClarification()
        assertNull(c.check("mở cốp trước", 0))
        assertNull(c.check("mở cốp sau", 0))
        assertNull(c.check("bật điều hòa", 0))
        assertNull("no question asked → nothing to resolve", c.resolve("sau", 0))
        assertTrue(c.check("đóng cốp", 0) is TrunkClarification.Reply.Tell)
        assertTrue(c.check("đóng cốp sau lại", 0) is TrunkClarification.Reply.Tell)
        assertEquals(VoiceCommand.OpenFrontTrunk, cmd("mở cốp trước"))
        assertEquals(VoiceCommand.UnlockRearTrunk, cmd("mở cốp sau"))
    }
}
