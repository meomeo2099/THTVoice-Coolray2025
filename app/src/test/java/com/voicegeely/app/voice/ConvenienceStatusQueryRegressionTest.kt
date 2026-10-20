package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvenienceStatusQueryRegressionTest {
    @Test
    fun parserRecognizesReadOnlyConvenienceQueries() {
        val cases = mapOf(
            "trạng thái đèn nội thất khi mở cửa" to VehicleStatusQuery.INTERIOR_LIGHT_ON_DOOR_OPEN,
            "đèn nội thất khi mở cửa đang bật không" to VehicleStatusQuery.INTERIOR_LIGHT_ON_DOOR_OPEN,
            "thời lượng follow me home hiện tại" to VehicleStatusQuery.FOLLOW_ME_HOME,
            "follow-me-home đang đặt bao lâu" to VehicleStatusQuery.FOLLOW_ME_HOME,
            "trạng thái sạc không dây" to VehicleStatusQuery.WIRELESS_CHARGER,
            "sạc không dây đang bật không" to VehicleStatusQuery.WIRELESS_CHARGER,
            "chế độ giao diện hiện tại" to VehicleStatusQuery.DISPLAY_THEME,
            "giao diện màn hình đang ở chế độ nào" to VehicleStatusQuery.DISPLAY_THEME
        )
        cases.forEach { (text, expected) ->
            val parsed = VehicleStatusQueryParser.parse(text)?.command as VoiceCommand.QueryVehicleStatus
            assertEquals(expected, parsed.query)
        }
    }

    @Test
    fun parserDoesNotStealConvenienceActuatorCommands() {
        assertNull(VehicleStatusQueryParser.parse("bật đèn nội thất khi mở cửa"))
        assertNull(VehicleStatusQueryParser.parse("đặt follow me home 60 giây"))
        assertNull(VehicleStatusQueryParser.parse("bật sạc không dây"))
        assertNull(VehicleStatusQueryParser.parse("chuyển giao diện tối"))
    }
}
