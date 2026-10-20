package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CabinTemperatureQueryTest {
    private val cabin = VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.CABIN_TEMPERATURE)

    @Test
    fun referencePhrasesParseAsCabinQuery() {
        listOf(
            "nhiệt độ trong xe bao nhiêu",
            "trong xe bao nhiêu độ",
            "nhiệt độ cabin bao nhiêu",
            "trong cabin bao nhiêu độ",
            "nhiệt độ trong cabin thế nào",
            "trong xe đang bao nhiêu độ",
            "kiểm tra giúp tôi nhiệt độ trong xe",
            "cho tôi biết nhiệt độ trong xe",
            "cho tôi hỏi nhiệt độ trong xe"
        ).forEach { text ->
            assertEquals(text, cabin, VehicleStatusQueryParser.parse(text)?.command)
        }
    }

    @Test
    fun temperatureCommandsAndOtherQueriesAreNotCabinQuery() {
        listOf("tăng nhiệt độ trong xe", "đặt nhiệt độ trong xe 22 độ", "nhiệt độ ngoài xe", "nhiệt độ điều hòa").forEach { text ->
            assertTrue(text, VehicleStatusQueryParser.parse(text)?.command != cabin)
        }
        assertEquals(
            VoiceCommand.QueryVehicleStatus(VehicleStatusQuery.OUTSIDE_TEMPERATURE),
            VehicleStatusQueryParser.parse("nhiệt độ ngoài xe")?.command
        )
    }
}
