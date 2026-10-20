package com.voicegeely.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleStatusQueryParserTest {
    @Test
    fun parsesSupportedReadOnlyQueries() {
        val cases = mapOf(
            "tốc độ xe hiện tại" to VehicleStatusQuery.SPEED,
            "đang ở số nào" to VehicleStatusQuery.GEAR,
            "pin còn bao nhiêu" to VehicleStatusQuery.BATTERY_SOC,
            "quãng đường còn lại" to VehicleStatusQuery.RANGE,
            "odo hiện tại" to VehicleStatusQuery.ODOMETER,
            "trip hiện tại" to VehicleStatusQuery.TRIP_DISTANCE,
            "nhiệt độ ngoài xe" to VehicleStatusQuery.OUTSIDE_TEMPERATURE,
            "cửa nào đang mở" to VehicleStatusQuery.DOOR_STATUS,
            "xe đã khóa cửa chưa" to VehicleStatusQuery.DOOR_LOCK_STATUS,
            "xe đang sạc không" to VehicleStatusQuery.CHARGE_STATUS,
            "dòng sạc bao nhiêu" to VehicleStatusQuery.CHARGE_CURRENT,
            "điện áp sạc bao nhiêu" to VehicleStatusQuery.CHARGE_VOLTAGE,
            "giới hạn dòng sạc hiện tại" to VehicleStatusQuery.CHARGE_CURRENT_LIMIT,
            "dòng sạc tối đa đang đặt bao nhiêu ampe" to VehicleStatusQuery.CHARGE_CURRENT_LIMIT,
            "trạng thái tự giảm gạt mưa khi đỗ" to VehicleStatusQuery.PARKING_WIPER_AUTO_REDUCTION,
            "tự giảm gạt mưa khi đỗ đang bật không" to VehicleStatusQuery.PARKING_WIPER_AUTO_REDUCTION,
            "thời lượng chế độ đỗ hiện tại" to VehicleStatusQuery.PARKING_MODE_DURATION,
            "chế độ đỗ đang đặt bao lâu" to VehicleStatusQuery.PARKING_MODE_DURATION,
            "trạng thái đèn viền" to VehicleStatusQuery.AMBIENT_LIGHT_POWER,
            "đèn viền đang bật không" to VehicleStatusQuery.AMBIENT_LIGHT_POWER,
            "độ sáng đèn viền hiện tại" to VehicleStatusQuery.AMBIENT_BRIGHTNESS,
            "đèn viền đang sáng mức mấy" to VehicleStatusQuery.AMBIENT_BRIGHTNESS,
            "màu đèn viền hiện tại" to VehicleStatusQuery.AMBIENT_COLOR,
            "đèn viền đang màu gì" to VehicleStatusQuery.AMBIENT_COLOR,
            "ghế phụ có người không" to VehicleStatusQuery.PASSENGER_OCCUPANCY,
            "dây an toàn tài xế" to VehicleStatusQuery.DRIVER_SEATBELT,
            "dây an toàn ghế phụ" to VehicleStatusQuery.PASSENGER_SEATBELT,
            "điều hòa đang bao nhiêu" to VehicleStatusQuery.CLIMATE_TEMPERATURE,
            "quạt mức mấy" to VehicleStatusQuery.FAN_SPEED,
            "chế độ lái hiện tại" to VehicleStatusQuery.DRIVE_MODE,
            "hồi năng lượng mức mấy" to VehicleStatusQuery.REGEN_LEVEL,
            "trạng thái ga tự động" to VehicleStatusQuery.CRUISE_STATUS,
            "kiểm tra trạng thái cruise control" to VehicleStatusQuery.CRUISE_STATUS,
            "ga tự động đang hoạt động không" to VehicleStatusQuery.CRUISE_STATUS,
            "cảm giác phanh hiện tại" to VehicleStatusQuery.BRAKE_PEDAL_FEEL,
            "bàn đạp phanh đang ở chế độ nào" to VehicleStatusQuery.BRAKE_PEDAL_FEEL,
            "trạng thái dừng êm" to VehicleStatusQuery.COMFORTABLE_STOP_STATUS,
            "dừng êm đang bật không" to VehicleStatusQuery.COMFORTABLE_STOP_STATUS
        )

        cases.forEach { (text, expected) ->
            val command = VehicleStatusQueryParser.parse(text)?.command as VoiceCommand.QueryVehicleStatus
            assertEquals(expected, command.query)
        }
    }

    @Test
    fun doesNotStealActuatorCommands() {
        assertNull(VehicleStatusQueryParser.parse("chỉnh nhiệt độ 22 độ"))
        assertNull(VehicleStatusQueryParser.parse("tăng quạt 2 mức"))
        assertNull(VehicleStatusQueryParser.parse("chuyển sang chế độ sport"))
        assertNull(VehicleStatusQueryParser.parse("mở kính bên lái"))
        assertNull(VehicleStatusQueryParser.parse("bật ga tự động"))
        assertNull(VehicleStatusQueryParser.parse("tắt cruise control"))
        assertNull(VehicleStatusQueryParser.parse("chuyển cảm giác phanh sang sport"))
        assertNull(VehicleStatusQueryParser.parse("bật dừng êm"))
        assertNull(VehicleStatusQueryParser.parse("đặt giới hạn dòng sạc 16 ampe"))
        assertNull(VehicleStatusQueryParser.parse("bật tự giảm gạt mưa khi đỗ"))
        assertNull(VehicleStatusQueryParser.parse("đặt chế độ đỗ 2 giờ"))
        assertNull(VehicleStatusQueryParser.parse("bật đèn viền"))
        assertNull(VehicleStatusQueryParser.parse("đặt độ sáng đèn viền mức 10"))
        assertNull(VehicleStatusQueryParser.parse("đổi đèn viền sang màu đỏ"))
    }
}
