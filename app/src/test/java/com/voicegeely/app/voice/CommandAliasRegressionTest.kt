package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandAliasRegressionTest {
    @Test fun passengerWindowAliasesDoNotCollapseIntoDriverSide() {
        assertEquals(
            VoiceCommand.Window(
                setOf(WindowTarget.FRONT_RIGHT),
                WindowOperation.SET_PERCENT,
                30
            ),
            VoiceCommandParser.parse("hạ cửa kính bên tài phụ 30 phần trăm")?.command
        )
    }

    @Test fun pairedRearAndFrontWindowPhrasesKeepBothTargets() {
        assertEquals(
            VoiceCommand.Window(
                setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT),
                WindowOperation.SET_PERCENT,
                20
            ),
            VoiceCommandParser.parse("hạ kính phía sau bên trái và phải 20 phần trăm")?.command
        )
        assertEquals(
            VoiceCommand.Window(
                setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT),
                WindowOperation.CLOSE,
                0
            ),
            VoiceCommandParser.parse("đóng kính trái và phải phía trước")?.command
        )
    }

    @Test fun halfAndAllWindowAliasesMapToCanonicalOperations() {
        assertEquals(
            VoiceCommand.Window(
                setOf(WindowTarget.FRONT_LEFT),
                WindowOperation.SET_PERCENT,
                50
            ),
            VoiceCommandParser.parse("hạ kính bên lái một nửa")?.command
        )
        assertEquals(
            VoiceCommand.Window(setOf(WindowTarget.ALL), WindowOperation.CLOSE, 0),
            VoiceCommandParser.parse("đóng hết cửa kính")?.command
        )
    }

    @Test fun sideOnlyWindowMeansFrontAndBareWindowFailsClosed() {
        assertEquals(
            VoiceCommand.Window(setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN, 100),
            VoiceCommandParser.parse("hạ kính bên trái")?.command
        )
        assertNull(VoiceCommandParser.parse("mở kính"))
    }

    @Test fun naturalFanAliasesUseExistingRelativeFanCommands() {
        assertEquals(
            VoiceCommand.AdjustFanSpeed(1),
            VoiceCommandParser.parse("gió mạnh hơn")?.command
        )
        assertEquals(
            VoiceCommand.AdjustFanSpeed(-1),
            VoiceCommandParser.parse("hạ gió")?.command
        )
    }

    @Test fun driveModeAliasesCollapseToTypedModes() {
        assertEquals(
            VoiceCommand.SetDriveMode(DriveMode.COMFORT),
            VoiceCommandParser.parse("chuyển sang normal")?.command
        )
        assertEquals(
            VoiceCommand.SetDriveMode(DriveMode.COMFORT),
            VoiceCommandParser.parse("chế độ lái êm ái")?.command
        )
        assertEquals(
            VoiceCommand.SetDriveMode(DriveMode.SPORT),
            VoiceCommandParser.parse("chuyển sang sport")?.command
        )
    }

    @Test fun regenerationNumericAndStrengthAliasesCollapseToTypedLevels() {
        assertEquals(
            VoiceCommand.SetRegenLevel(RegenLevel.HIGH),
            VoiceCommandParser.parse("hồi năng lượng số ba")?.command
        )
        assertEquals(
            VoiceCommand.SetRegenLevel(RegenLevel.MEDIUM),
            VoiceCommandParser.parse("hồi năng lượng cấp hai")?.command
        )
        assertEquals(
            VoiceCommand.SetRegenLevel(RegenLevel.LOW),
            VoiceCommandParser.parse("hồi năng lượng nhỏ nhất")?.command
        )
    }

    @Test fun navigationIntentAliasesPreserveProviderAndDestination() {
        assertEquals(
            VoiceCommand.Navigate(NavigationProvider.WAZE, "sân bay"),
            VoiceCommandParser.parse("dùng waze tìm đường đến sân bay")?.command
        )
        assertEquals(
            VoiceCommand.Navigate(NavigationProvider.AUTO, "hà nội"),
            VoiceCommandParser.parse("đưa tôi tới hà nội")?.command
        )
    }
}
