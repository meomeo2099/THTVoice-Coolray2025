package com.voicegeely.app.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class UpstreamCommandSupplementTest {
    @Test fun sourceUtterancesMapToExistingTypedCommands() {
        val file=listOf("docs", "../docs").map { File(it,"reference/voice/THTVOICE_COMMAND_SUPPLEMENT.tsv") }.first { it.isFile }
        val rows=file.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
        assertTrue(rows.size >= 90)
        val failures=rows.mapNotNull { row ->
            val cells=row.split('\t');val actual=VoiceParseChain.parse(cells[1])?.command;val expected=expected(cells[0])
            if(actual==expected) null else "${cells[1]} expected=$expected actual=$actual"
        }
        assertEquals(failures.joinToString("\n"),emptyList<String>(),failures)
    }
    @Test fun remainingWindowsAskRatherThanGuess() {
        for(text in listOf("đóng luôn kính còn lại","đóng kính còn lại","đóng luôn kiếng còn lại","đóng kiếng còn lại")) {
            assertNull(text,VoiceParseChain.parse(text))
            val c=WindowClarification();assertNotNull(text,c.check(text,0))
            assertEquals(VoiceCommand.Window(setOf(WindowTarget.REAR_LEFT),WindowOperation.CLOSE,0),c.resolve("sau bên lái",1000)?.command)
        }
    }
    @Test fun sourcePlatformAndAmbiguousSlipsAreNotEnabled() {
        for(text in listOf("anh nhiệt độ","cảm giác độ","tắt âm hơn")) {
            val c=VoiceParseChain.parse(text)?.command
            assertFalse(text,c is VoiceCommand.AdjustTemperature || c is VoiceCommand.Recirculation)
        }
        for(text in listOf("mở cửa bên lái","đóng cửa bên phụ")) assertTrue(VoiceParseChain.parse(text)?.command !is VoiceCommand.Window)
        assertTrue(VoiceParseChain.parse("bật wifi")?.command !is VoiceCommand.OpenWifiSettings)
        assertFalse(CoolrayCapabilities.isEnabled(CoolrayCapabilities.Family.CHARGING))
        assertFalse(CoolrayCapabilities.isEnabled(CoolrayCapabilities.Family.DOOR_LOCK))
    }
    @Test fun appSlotsDoNotRewriteDestinationsOrAppPackages() {
        for(text in listOf("đi đến đường viên map li","tìm bài hát nóng thế","mở ứng dụng xe bí quá")) assertEquals(text,VoiceTextNormalizer.normalize(text))
        for(text in listOf("đi đến đường viên map li","tìm bài hát nóng thế","mở ứng dụng xe bí quá")) {
            val c=VoiceParseChain.parse(text)?.command
            assertFalse(text,c is VoiceCommand.AcPower || c is VoiceCommand.Recirculation || c is VoiceCommand.Window)
        }
        assertEquals(VoiceCommand.OpenApp(AppTarget.VIETMAP),VoiceParseChain.parse("mở ứng dụng viên map li")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.WAZE),VoiceParseChain.parse("mở bản đồ waze")?.command)
        assertEquals("vietmap live",InstalledAppMatcher.canonicalName("viên map li"))
        assertEquals(listOf("vn.vietmap.live"),InstalledAppMatcher.match("viên map li",listOf(InstalledAppMatcher.InstalledApp("Vietmap Live","vn.vietmap.live"))))
        assertEquals("Đã mở việt mép lai.",SpokenTerms.forSpeech("Đã mở Vietmap Live."))
    }
    private fun expected(category:String):VoiceCommand = when(category) {
        "ac_on" -> VoiceCommand.AcPower(true)
        "ac_off" -> VoiceCommand.AcPower(false)
        "warmer" -> VoiceCommand.AdjustTemperature(0.5f)
        "cooler" -> VoiceCommand.AdjustTemperature(-0.5f)
        "outside_air" -> VoiceCommand.Recirculation(false)
        "fan_max" -> VoiceCommand.SetFanSpeed(7)
        "fan_min" -> VoiceCommand.SetFanSpeed(1)
        "fan_three" -> VoiceCommand.SetFanSpeed(3)
        "quick_ac" -> VoiceCommand.MaxAc(true)
        "demist_front" -> VoiceCommand.Defrost(DefrostZone.FRONT,true)
        "odometer" -> VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.ODOMETER)
        "fan_query" -> VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.FAN_SPEED)
        "climate_query" -> VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CLIMATE_TEMPERATURE)
        "crack_windows" -> VoiceCommand.Window(setOf(WindowTarget.ALL),WindowOperation.SET_PERCENT,10)
        "music" -> VoiceCommand.OpenMedia(MediaContentType.MUSIC)
        "video" -> VoiceCommand.OpenMedia(MediaContentType.VIDEO)
        "youtube" -> VoiceCommand.OpenMedia(MediaContentType.VIDEO,MediaProvider.YOUTUBE)
        "smarttube" -> VoiceCommand.OpenMedia(MediaContentType.VIDEO,MediaProvider.SMARTTUBE)
        "youtube_music" -> VoiceCommand.OpenMedia(MediaContentType.MUSIC,MediaProvider.YOUTUBE_MUSIC)
        "waze" -> VoiceCommand.OpenApp(AppTarget.WAZE)
        "google_maps" -> VoiceCommand.OpenApp(AppTarget.MAPS)
        "default_maps" -> VoiceCommand.OpenApp(AppTarget.DEFAULT_MAPS)
        "vtv" -> VoiceCommand.OpenInstalledApp("vtv go")
        "fpt" -> VoiceCommand.OpenInstalledApp("fpt play")
        "vieon" -> VoiceCommand.OpenInstalledApp("vieon")
        "tv360" -> VoiceCommand.OpenInstalledApp("tv360")
        "gofa" -> VoiceCommand.OpenInstalledApp("gofa")
        "file_manager" -> VoiceCommand.OpenInstalledApp("file manager")
        "kiki" -> VoiceCommand.OpenApp(AppTarget.KIKI)
        "browser" -> VoiceCommand.OpenApp(AppTarget.CHROME)
        "vietmap" -> VoiceCommand.OpenApp(AppTarget.VIETMAP)
        else -> error(category)
    }
}
