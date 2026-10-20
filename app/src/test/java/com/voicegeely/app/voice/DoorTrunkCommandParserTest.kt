package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoorTrunkCommandParserTest {
    @Test
    fun parsesExplicitDoorLockAliases() {
        listOf(
            "khóa cửa",
            "khoá cửa",
            "khóa xe",
            "khóa cửa xe",
            "khóa toàn bộ cửa",
            "khóa tất cả cửa xe"
        ).forEach { text ->
            assertEquals(
                "$text should lock",
                VoiceCommand.SetDoorLock(locked = true),
                DoorTrunkCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun parsesExplicitDoorUnlockAliases() {
        listOf(
            "mở khóa",
            "mở khoá",
            "mở khóa cửa",
            "mở khóa xe",
            "mở khóa cửa xe",
            "mở khóa toàn bộ cửa",
            "mở khóa tất cả cửa xe"
        ).forEach { text ->
            assertEquals(
                "$text should unlock",
                VoiceCommand.SetDoorLock(locked = false),
                DoorTrunkCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun parsesOnlyExplicitFrontTrunkAliases() {
        listOf(
            "mở cốp trước",
            "mở cốp phía trước",
            "mở cốp xe đằng trước",
            "mở capo",
            "mở nắp capo",
            "mở ca pô",
            "mở nắp ca pô"
        ).forEach { text ->
            assertEquals(
                "$text should open front trunk",
                VoiceCommand.OpenFrontTrunk,
                DoorTrunkCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun parsesExplicitRearTrunkAliasesThroughFamily() {
        listOf(
            "mở cốp sau",
            "mở cốp phía sau"
        ).forEach { text ->
            assertEquals(
                "$text should unlock rear trunk access",
                VoiceCommand.UnlockRearTrunk,
                DoorTrunkCommandParser.parse(text)?.command
            )
        }
    }

    @Test
    fun ambiguousOrDifferentCommandsFailClosed() {
        listOf(
            "mở cửa",
            "mở cửa sổ",
            "mở cửa kính",
            "mở kính bên lái",
            "khóa trẻ em",
            "khóa cửa sổ",
            "mở cốp",
            "đóng cốp trước"
        ).forEach { text ->
            assertNull("$text must not be claimed", DoorTrunkCommandParser.parse(text))
        }
    }
}
