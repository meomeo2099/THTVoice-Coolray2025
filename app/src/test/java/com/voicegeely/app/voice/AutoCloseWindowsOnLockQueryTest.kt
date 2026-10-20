package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCloseWindowsOnLockQueryTest {
    private val query = VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.AUTO_CLOSE_WINDOWS_ON_LOCK)

    @Test
    fun phrasesParse() {
        listOf(
            "tự đóng kính khi khóa xe",
            "trạng thái tự đóng kính khi khóa xe",
            "kiểm tra tự động đóng kính khi khóa",
            "tự đóng kính khi khóa xe có bật không",
            "tự đóng cửa sổ khi khoá xe đang bật không"
        ).forEach { text -> assertEquals(text, query, VehicleStatusQueryParser.parse(text)?.command) }
    }

    @Test
    fun doorLockAndWindowCommandsAreNotCaptured() {
        assertEquals(
            VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.DOOR_LOCK_STATUS),
            VehicleStatusQueryParser.parse("đã khóa xe chưa")?.command
        )
        listOf("đóng kính", "đóng tất cả kính").forEach { text ->
            assertTrue(text, VehicleStatusQueryParser.parse(text)?.command != query)
        }
    }
}
