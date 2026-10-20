package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowParserRegressionTest {
    @Test fun ambiguousWindowCommandsFailClosed() {
        assertNull(VoiceCommandParser.parse("mở kính"))
        assertNull(VoiceCommandParser.parse("kéo kính xuống"))
        assertNull(VoiceCommandParser.parse("dừng kính"))
    }

    @Test fun sideWithoutFrontOrRearMeansFrontWindow() {
        assertEquals(setOf(WindowTarget.FRONT_LEFT), (VoiceCommandParser.parse("mở kính bên trái")?.command as VoiceCommand.Window).targets)
        assertEquals(setOf(WindowTarget.FRONT_RIGHT), (VoiceCommandParser.parse("mở kính bên phải")?.command as VoiceCommand.Window).targets)
        assertEquals(setOf(WindowTarget.REAR_LEFT), (VoiceCommandParser.parse("mở kính sau bên trái")?.command as VoiceCommand.Window).targets)
    }

    @Test fun resolvesIndividualWindowsDeterministically() {
        assertWindow("mở kính bên lái", setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN)
        assertWindow("hạ kính tài", setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN)
        assertWindow("đóng kính bên phụ", setOf(WindowTarget.FRONT_RIGHT), WindowOperation.CLOSE)
        assertWindow("mở kính sau bên lái", setOf(WindowTarget.REAR_LEFT), WindowOperation.OPEN)
        assertWindow("đóng kính sau bên phụ", setOf(WindowTarget.REAR_RIGHT), WindowOperation.CLOSE)
    }

    @Test fun resolvesFrontRearPairsAndAll() {
        assertWindow(
            "mở hai kính trước",
            setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT),
            WindowOperation.OPEN
        )
        assertWindow(
            "mở 2 kính trước",
            setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT),
            WindowOperation.OPEN
        )
        assertWindow(
            "đóng hai kính sau",
            setOf(WindowTarget.REAR_LEFT, WindowTarget.REAR_RIGHT),
            WindowOperation.CLOSE
        )
        assertWindow("mở tất cả kính", setOf(WindowTarget.ALL), WindowOperation.OPEN)
    }

    @Test fun windowCountNeverBecomesPositionPercent() {
        val pair = VoiceCommandParser.parse("mở 2 kính trước")?.command as VoiceCommand.Window
        assertEquals(WindowOperation.OPEN, pair.operation)
        assertEquals(100, pair.percent)

        val explicitPercent = VoiceCommandParser.parse("mở kính trước 2 phần trăm")?.command as VoiceCommand.Window
        assertEquals(setOf(WindowTarget.FRONT_LEFT, WindowTarget.FRONT_RIGHT), explicitPercent.targets)
        assertEquals(WindowOperation.SET_PERCENT, explicitPercent.operation)
        assertEquals(2, explicitPercent.percent)
    }

    @Test fun parsesPercentAndStopWithExplicitTarget() {
        val half = VoiceCommandParser.parse("hạ kính bên lái 50 phần trăm")?.command as VoiceCommand.Window
        assertEquals(setOf(WindowTarget.FRONT_LEFT), half.targets)
        assertEquals(WindowOperation.SET_PERCENT, half.operation)
        assertEquals(50, half.percent)

        val passenger = VoiceCommandParser.parse("đưa kính bên phụ về 30 phần trăm")?.command as VoiceCommand.Window
        assertEquals(setOf(WindowTarget.FRONT_RIGHT), passenger.targets)
        assertEquals(WindowOperation.SET_PERCENT, passenger.operation)
        assertEquals(30, passenger.percent)

        val stop = VoiceCommandParser.parse("dừng kính sau bên phụ")?.command as VoiceCommand.Window
        assertEquals(setOf(WindowTarget.REAR_RIGHT), stop.targets)
        assertEquals(WindowOperation.STOP, stop.operation)
        assertNull(stop.percent)
    }

    @Test fun windowAliasesKeepCanonicalTarget() {
        assertWindow("hạ kiếng bên lái", setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN)
        assertWindow("mở cửa cánh ghế lái", setOf(WindowTarget.FRONT_LEFT), WindowOperation.OPEN)
        assertWindow("đóng kính phụ", setOf(WindowTarget.FRONT_RIGHT), WindowOperation.CLOSE)
    }

    private fun assertWindow(
        text: String,
        targets: Set<WindowTarget>,
        operation: WindowOperation
    ) {
        val command = VoiceCommandParser.parse(text)?.command as VoiceCommand.Window
        assertEquals(targets, command.targets)
        assertEquals(operation, command.operation)
    }
}
