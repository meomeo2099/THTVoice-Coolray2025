package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** App names as the offline ASR hears them (owner report 09/10/2026 and common apps). */
class AppNameSlipRegressionTest {
    private fun cmd(text: String) = VoiceParseChain.parse(text)?.command

    @Test
    fun googleMapsVariants() {
        listOf("mở gu mát", "mở gú gồ mát", "mơ goo gồ mát", "mở gu gồ máp", "mở gút gồ mát", "mờ gu gồ mát", "mở google map",
            "mở go gồ ma", "mở go gồ mát", "mở go gồ mép", "mở gâu gồ mát", "mở gâu gồ mép", "mở gâu gồ ma")
            .forEach { assertEquals(it, VoiceCommand.OpenApp(AppTarget.MAPS), cmd(it)) }
    }

    @Test
    fun catalogAppsParseLikeTheCorrectSpelling() {
        mapOf(
            "mở diu túp" to "mở youtube", "mở giu túp" to "mở youtube", "mơ du túp" to "mở youtube",
            "mở du túp miu dích" to "mở youtube music",
            "mở việt máp" to "mở vietmap", "mở viết map" to "mở vietmap",
            "mở quây dơ" to "mở waze",
            "mở sờ pô ti phai" to "mở spotify", "mở pô ti phai" to "mở spotify",
            "mở din em pê ba" to "mở zing mp3",
            "mở tít tốc" to "mở tiktok", "mở tích tắc" to "mở tiktok",
            "mở nét phờ lích" to "mở netflix",
            "mở crôm" to "mở chrome",
            "mở vê lê xê" to "mở vlc",
            "mở kì ki" to "mở kiki"
        ).forEach { (spoken, correct) ->
            val expected = cmd(correct)
            assertEquals(spoken, true, expected != null && expected !is VoiceCommand.OpenInstalledApp)
            assertEquals(spoken, expected, cmd(spoken))
        }
    }

    @Test
    fun installedAppNamesAreCanonical() {
        mapOf(
            "za lô" to "zalo", "phây búc" to "facebook", "mét xen giơ" to "messenger", "gờ meo" to "gmail",
            "sóp pi" to "shopee", "gờ ráp" to "grab", "chợ play" to "play store"
        ).forEach { (spoken, label) -> assertEquals(spoken, label, InstalledAppMatcher.canonicalName(spoken)) }
    }
}
