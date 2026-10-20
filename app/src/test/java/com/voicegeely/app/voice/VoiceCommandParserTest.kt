package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    @Test fun allNumericClimateSetpointsStayNumeric() {
        for (value in 16..32) assertEquals(VoiceCommand.SetTemperature(value.toFloat()), VoiceCommandParser.parse("đặt nhiệt độ $value độ")?.command)
    }

    @Test fun spokenTwentyTwoAndWhisperHiMoiHiBecome22() {
        assertEquals(VoiceCommand.SetTemperature(22f), VoiceCommandParser.parse("đặt nhiệt độ hai mươi hai độ")?.command)
        assertEquals(VoiceCommand.SetTemperature(22f), VoiceCommandParser.parse("đặt nhiệt độ hi mươi hi độ")?.command)
    }

    @Test fun shorthandHaiHaiInClimateContextMeans22() {
        assertEquals(VoiceCommand.SetTemperature(22f), VoiceCommandParser.parse("đặt nhiệt độ hai hai độ")?.command)
        assertEquals(VoiceCommand.SetTemperature(22f), VoiceCommandParser.parse("chỉnh nhiệt độ hai hai")?.command)
    }

    @Test fun explicitHiAndLoAreEndpointsOnly() {
        assertTrue(VoiceCommandParser.parse("đặt nhiệt độ hi")?.command is VoiceCommand.TemperatureHigh)
        assertTrue(VoiceCommandParser.parse("đặt nhiệt độ lo")?.command is VoiceCommand.TemperatureLow)
        assertEquals(VoiceCommand.SetTemperature(20f), VoiceCommandParser.parse("đặt nhiệt độ 20")?.command)
    }

    @Test fun parsesAcPowerCommands() {
        assertEquals(VoiceCommand.AcPower(true), VoiceCommandParser.parse("bật điều hòa")?.command)
        assertEquals(VoiceCommand.AcPower(false), VoiceCommandParser.parse("tắt máy lạnh")?.command)
    }

    @Test fun parsesFanLevelAndAdjustments() {
        assertEquals(VoiceCommand.SetFanSpeed(3), VoiceCommandParser.parse("quạt mức 3")?.command)
        assertEquals(VoiceCommand.SetFanSpeed(8), VoiceCommandParser.parse("quạt mạnh nhất")?.command)
        assertEquals(VoiceCommand.SetFanSpeed(0), VoiceCommandParser.parse("tắt quạt")?.command)
        assertEquals(VoiceCommand.AdjustFanSpeed(1), VoiceCommandParser.parse("tăng quạt")?.command)
        assertEquals(VoiceCommand.AdjustFanSpeed(-2), VoiceCommandParser.parse("giảm quạt 2 mức")?.command)
    }

    @Test fun parsesRecirculationAndDefrost() {
        assertEquals(VoiceCommand.Recirculation(true), VoiceCommandParser.parse("lấy gió trong")?.command)
        assertEquals(VoiceCommand.Recirculation(false), VoiceCommandParser.parse("lấy gió ngoài")?.command)
        assertEquals(VoiceCommand.Defrost(DefrostZone.FRONT, true), VoiceCommandParser.parse("bật sấy kính trước")?.command)
        assertEquals(VoiceCommand.Defrost(DefrostZone.REAR, false), VoiceCommandParser.parse("tắt sấy kính sau")?.command)
    }

    @Test fun parsesFanDirectionsWithoutFallingIntoWindowParser() {
        assertEquals(VoiceCommand.FanDirection(setOf(FanDirectionTarget.FACE)), VoiceCommandParser.parse("hướng gió vào mặt")?.command)
        assertEquals(VoiceCommand.FanDirection(setOf(FanDirectionTarget.FEET, FanDirectionTarget.WINDSHIELD)), VoiceCommandParser.parse("hướng gió kính và chân")?.command)
    }

    @Test fun windowAliasesResolveToCanonicalDriverWindow() {
        for (text in listOf("hạ kính tài", "hạ kiếng bên lái", "mở cửa kính ghế lái")) {
            val command = VoiceCommandParser.parse(text)?.command as VoiceCommand.Window
            assertEquals(setOf(WindowTarget.FRONT_LEFT), command.targets)
            assertEquals(WindowOperation.OPEN, command.operation)
        }
    }

    @Test fun windowPercentRejectsInvalidTargetsWithoutFallingBackToFullOpen() {
        for (text in listOf(
            "mở kính bên lái 101%",
            "mở kính bên lái 150 phần trăm",
            "mở kính bên lái 101",
            "đóng kính bên lái 150%",
            "mở kính bên lái -5%",
            "mở kính bên lái 50.5%",
            "mở kính bên lái 999999999999%"
        )) {
            assertNull(text, VoiceCommandParser.parse(text))
        }
    }

    @Test fun validWindowPercentAndWindowCountStayDistinct() {
        for (percent in listOf(0, 50, 100)) {
            assertEquals(
                VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.SET_PERCENT, percent),
                VoiceCommandParser.parse("mở kính bên lái $percent%")?.command
            )
        }
        assertEquals(
            VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT), WindowOperation.OPEN, 100),
            VoiceCommandParser.parse("mở 2 kính trước")?.command
        )
    }

    @Test fun openingYoutubeIsNotMisparsedAsSearch() {
        assertEquals(VoiceCommand.OpenApp(AppTarget.YOUTUBE), VoiceCommandParser.parse("mở youtube")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.YOUTUBE_MUSIC), VoiceCommandParser.parse("mở youtube music")?.command)
    }

    @Test fun parsesMediaSearchAndControls() {
        assertEquals(
            VoiceCommand.SearchMedia(MediaProvider.YOUTUBE, "sơn tùng", MediaContentType.VIDEO),
            VoiceCommandParser.parse("tìm sơn tùng trên youtube")?.command
        )
        assertEquals(
            VoiceCommand.SearchMedia(MediaProvider.SPOTIFY, "hello", MediaContentType.MUSIC),
            VoiceCommandParser.parse("nghe hello trên spotify")?.command
        )
        assertEquals(VoiceCommand.MediaControl(MediaAction.NEXT), VoiceCommandParser.parse("bài tiếp theo")?.command)
        assertEquals(VoiceCommand.MediaControl(MediaAction.PAUSE), VoiceCommandParser.parse("tạm dừng")?.command)
    }

    @Test fun manualBrightnessBoundsNeverFallIntoAnotherCommand() {
        for (percent in listOf(1, 50, 100)) {
            assertEquals(VoiceCommand.SetManualDisplayBrightness(percent),
                VoiceCommandParser.parse("đặt độ sáng màn hình $percent%")?.command)
        }
        for (phrase in listOf("đặt độ sáng màn hình 0%", "đặt độ sáng màn hình 101%",
            "đặt độ sáng màn hình -5%", "đặt độ sáng màn hình 50.5%",
            "đặt độ sáng màn hình 50")) {
            assertNull(phrase, VoiceCommandParser.parse(phrase))
        }
    }

    @Test fun displayBrightnessQueryIsSeparateFromVehicleTheme() {
        assertEquals(VoiceCommand.QueryDisplayBrightness, VoiceCommandParser.parse("kiểm tra độ sáng màn hình")?.command)
        assertEquals(VoiceCommand.QueryDisplayBrightness, VoiceCommandParser.parse("màn hình đang sáng bao nhiêu")?.command)
        assertNull(VoiceCommandParser.parse("tăng độ sáng màn hình"))
        assertEquals(VoiceCommand.SetDisplayTheme(DisplayThemeMode.DARK),
            VoiceCommandParser.parse("giao diện tối")?.command)
    }

    @Test fun wifiSsidQueriesAreReadOnly() {
        assertEquals(VoiceCommand.QueryWifiSsid, VoiceCommandParser.parse("tên mạng Wi-Fi")?.command)
        assertEquals(VoiceCommand.QueryWifiSsid, VoiceCommandParser.parse("wifi đang kết nối mạng nào")?.command)
        assertNull(VoiceCommandParser.parse("đổi tên mạng wifi"))
    }

    @Test fun wifiIpQueriesDoNotBecomeToggleCommands() {
        assertEquals(VoiceCommand.QueryWifiIpAddress, VoiceCommandParser.parse("địa chỉ IP của Wi-Fi")?.command)
        assertEquals(VoiceCommand.QueryWifiIpAddress, VoiceCommandParser.parse("kiểm tra ip wifi")?.command)
        assertNull(VoiceCommandParser.parse("đặt ip wifi 192.168.1.8"))
    }

    @Test fun wifiStateQueriesAreReadOnly() {
        assertEquals(VoiceCommand.QueryWifiState, VoiceCommandParser.parse("kiểm tra wifi")?.command)
        assertEquals(VoiceCommand.QueryWifiState, VoiceCommandParser.parse("wifi đang bật hay tắt")?.command)
        assertNull(VoiceCommandParser.parse("bật wifi"))
        assertNull(VoiceCommandParser.parse("tắt wifi"))
    }

    @Test fun windowingQueriesCannotLaunchOrSplitApps() {
        assertEquals(VoiceCommand.QueryWindowingCapability(WindowingMode.PIP),
            VoiceCommandParser.parse("kiểm tra PiP")?.command)
        assertEquals(VoiceCommand.QueryWindowingCapability(WindowingMode.FREEFORM),
            VoiceCommandParser.parse("xem freeform")?.command)
        assertEquals(VoiceCommand.QueryWindowingCapability(WindowingMode.SPLIT_SCREEN),
            VoiceCommandParser.parse("kiểm tra chia đôi màn hình")?.command)
        assertNull(VoiceCommandParser.parse("chia đôi màn hình youtube và maps"))
        assertNull(VoiceCommandParser.parse("mở pip youtube"))
        assertNull(VoiceCommandParser.parse("mở youtube pip"))
    }

    @Test fun sceneAvailabilityQuestionsNeverActivateScene() {
        assertEquals(VoiceCommand.QuerySceneCapability(SceneType.REST),
            VoiceCommandParser.parse("kiểm tra chế độ nghỉ")?.command)
        assertEquals(VoiceCommand.QuerySceneCapability(SceneType.CAMPING),
            VoiceCommandParser.parse("xe có chế độ cắm trại không")?.command)
        // Opening the OEM screen is allowed (5.5); nothing ever writes or toggles scene state.
        assertEquals(VoiceCommand.OpenSceneScreen(SceneType.CAMPING),
            VoiceCommandParser.parse("mở chế độ cắm trại")?.command)
        for (utterance in listOf("bật chế độ nghỉ", "tắt chế độ cắm trại", "tắt camping mode")) {
            assertNull(utterance, VoiceCommandParser.parse(utterance))
        }
    }

    @Test fun parsesNavigationProvidersAndDestination() {
        assertEquals(VoiceCommand.Navigate(NavigationProvider.WAZE, "sân bay"), VoiceCommandParser.parse("dẫn đường đến sân bay bằng waze")?.command)
        assertEquals(VoiceCommand.Navigate(NavigationProvider.AUTO, "123 nguyễn huệ"), VoiceCommandParser.parse("chỉ đường tới 123 nguyễn huệ")?.command)
        assertEquals(VoiceCommand.OpenApp(AppTarget.VIETMAP), VoiceCommandParser.parse("mở vietmap live")?.command)
    }
}
