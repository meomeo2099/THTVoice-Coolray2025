package com.voicegeely.app.voice

/**
 * Compatibility pre-parser used before the generic command parser.
 * Keeps high-confidence read-only vehicle queries plus narrowly scoped Android
 * settings/vehicle commands that must not be shadowed by broader phrases.
 */
object VehicleStatusQueryParser {
    /** Owner 16/10/2026: "tình trạng xe", "kiểm tra xe", "xe sao rồi"… (whole sentence only). */
    private val CAR_SUMMARY = Regex(
        "^(?:(?:kiểm tra|xem|báo cáo|cho biết|cho tôi biết|đọc) )?(?:tình trạng|tình hình|trạng thái)(?: chung)?(?: của)? xe(?: hiện tại| bây giờ| thế nào| ra sao| sao rồi| như thế nào)?$" +
            "|^(?:kiểm tra|báo cáo) (?:nhanh )?xe$|^xe (?:thế nào|ra sao|sao rồi|ổn không|ổn chưa)(?: rồi)?$"
    )
    fun parse(text: String): ParsedVoiceCommand? {
        DisplayBrightnessModeVoiceParser.parse(text)?.let { return it }
        ParkingContinuousVoiceParser.parse(text)?.let { return it }
        ChargeControlVoiceParser.parse(text)?.let { return it }

        val normalized = VoiceTextNormalizer.normalize(text)
        if (normalized.isBlank()) return null

        val query = when {
            CAR_SUMMARY.matches(normalized) -> VehicleStatusQuery.CAR_SUMMARY
            // REF-WIDGET metrics (Coolray, 09/10/2026). Service before RANGE ("còn bao nhiêu cây số nữa thì bảo dưỡng").
            Regex("bảo dưỡng|bảo trì định kỳ|thay dầu định kỳ").containsMatchIn(normalized) &&
                Regex("bao giờ|khi nào|bao lâu|bao nhiêu|mấy ngày|lịch|còn|tới hạn|đến hạn|đến kỳ|kiểm tra").containsMatchIn(normalized) -> VehicleStatusQuery.SERVICE_DUE
            Regex("tiêu hao (?:nhiên liệu|xăng)|tiêu thụ (?:nhiên liệu|xăng)|hao xăng|ăn xăng|tốn (?:bao nhiêu |nhiều )?xăng|bao nhiêu lít (?:trên|một) (?:100|trăm)").containsMatchIn(normalized) -> VehicleStatusQuery.AVERAGE_FUEL_CONSUMPTION
            Regex("(?:xăng|nhiên liệu)(?: trong bình)? còn (?:bao nhiêu|nhiều không|mấy)|còn (?:bao nhiêu|mấy vạch) (?:xăng|nhiên liệu)|mức (?:xăng|nhiên liệu)|bình xăng còn|(?:hết|sắp hết) xăng chưa|có cần đổ xăng").containsMatchIn(normalized) -> VehicleStatusQuery.FUEL_LEVEL
            Regex("nhiệt độ (?:nước làm mát|nước mát|động cơ|máy)|nước làm mát(?: đang)? (?:bao nhiêu|mấy) độ|máy (?:có )?nóng (?:không|0|quá không)").containsMatchIn(normalized) -> VehicleStatusQuery.COOLANT_TEMPERATURE
            Regex("điện áp (?:ắc quy|ác quy|acquy|bình|bình điện)|(?:ắc quy|ác quy|acquy|bình điện) (?:còn |đang )?(?:bao nhiêu|mấy) vôn|(?:ắc quy|ác quy|acquy|bình điện) (?:có )?(?:yếu|khỏe|ổn) (?:không|0)|kiểm tra (?:ắc quy|ác quy|acquy|bình điện)").containsMatchIn(normalized) -> VehicleStatusQuery.BATTERY_VOLTAGE
            Regex("áp suất (?:lốp|bánh|vỏ)|(?:lốp|bánh xe|vỏ xe) (?:có )?(?:đủ hơi|non hơi|thiếu hơi|căng không)|kiểm tra (?:lốp|hơi lốp|áp suất)|hơi lốp").containsMatchIn(normalized) -> VehicleStatusQuery.TIRE_PRESSURE
            Regex("tốc độ xe(?: hiện tại)?|tốc độ hiện tại của xe|xe đang chạy bao nhiêu|xe chạy bao nhiêu|đang chạy bao nhiêu").containsMatchIn(normalized) -> VehicleStatusQuery.SPEED
            Regex("đang ở số (?:nào|gì)|đang số (?:nào|gì)|vị trí số hiện tại|cần số hiện tại").containsMatchIn(normalized) -> VehicleStatusQuery.GEAR
            Regex("pin còn bao nhiêu|pin xe còn bao nhiêu|phần trăm pin|mức pin hiện tại|dung lượng pin hiện tại|soc hiện tại|battery soc").containsMatchIn(normalized) -> VehicleStatusQuery.BATTERY_SOC
            Regex("quãng đường còn lại|còn đi được bao nhiêu|xe còn đi được bao nhiêu|tầm hoạt động còn lại|range hiện tại|còn bao nhiêu cây số").containsMatchIn(normalized) -> VehicleStatusQuery.RANGE
            Regex("odo(?: hiện tại)?|công tơ mét|tổng quãng đường|xe đã đi bao nhiêu|xe đi được tổng bao nhiêu").containsMatchIn(normalized) -> VehicleStatusQuery.ODOMETER
            Regex("trip(?: hiện tại)?|quãng đường chuyến đi|chuyến này đi bao nhiêu|quãng đường hành trình hiện tại").containsMatchIn(normalized) -> VehicleStatusQuery.TRIP_DISTANCE
            Regex("nhiệt độ ngoài xe|nhiệt độ bên ngoài|bên ngoài bao nhiêu độ|ngoài trời bao nhiêu độ").containsMatchIn(normalized) -> VehicleStatusQuery.OUTSIDE_TEMPERATURE
            Regex("cửa nào đang mở|có cửa nào mở (?:không|0)|trạng thái cửa|các cửa đã đóng chưa|cửa xe đóng hết chưa").containsMatchIn(normalized) -> VehicleStatusQuery.DOOR_STATUS
            Regex("xe đã khóa cửa chưa|cửa đã khóa chưa|trạng thái khóa cửa|cửa nào chưa khóa|đã khóa xe chưa").containsMatchIn(normalized) -> VehicleStatusQuery.DOOR_LOCK_STATUS
            Regex("^(?:kiểm tra |xem )?(?:trạng thái sạc không dây|sạc không dây đang (?:bật|tắt) (?:không|0))$").matches(normalized) -> VehicleStatusQuery.WIRELESS_CHARGER
            !normalized.contains("sạc không dây") && Regex("xe đang sạc (?:không|0)|đang sạc (?:không|0)|trạng thái sạc|xe có đang sạc|sạc đang chạy (?:không|0)").containsMatchIn(normalized) -> VehicleStatusQuery.CHARGE_STATUS
            Regex("^(?:kiểm tra |xem )?(?:giới hạn dòng sạc(?: hiện tại)?|dòng sạc tối đa đang đặt(?: là)? bao nhiêu(?: ampe)?|xe đang giới hạn dòng sạc bao nhiêu(?: ampe)?)$").matches(normalized) -> VehicleStatusQuery.CHARGE_CURRENT_LIMIT
            Regex("dòng sạc bao nhiêu|dòng điện sạc|đang sạc bao nhiêu ampe|cường độ dòng sạc").containsMatchIn(normalized) -> VehicleStatusQuery.CHARGE_CURRENT
            Regex("điện áp sạc bao nhiêu|điện áp khi sạc|đang sạc bao nhiêu vôn|điện thế sạc").containsMatchIn(normalized) -> VehicleStatusQuery.CHARGE_VOLTAGE
            Regex("ghế phụ có người (?:không|0)|ghế hành khách có người (?:không|0)|ghế trước bên phụ có người|trạng thái ghế phụ").containsMatchIn(normalized) -> VehicleStatusQuery.PASSENGER_OCCUPANCY
            Regex("dây an toàn tài xế|dây an toàn bên lái|tài xế đã cài dây an toàn chưa").containsMatchIn(normalized) -> VehicleStatusQuery.DRIVER_SEATBELT
            Regex("dây an toàn ghế phụ|dây an toàn hành khách|ghế phụ đã cài dây an toàn chưa").containsMatchIn(normalized) -> VehicleStatusQuery.PASSENGER_SEATBELT
            Regex("^(?:kiểm tra |xem )?(?:trạng thái )?tự (?:động )?đóng (?:kính|cửa sổ)(?: xe)? khi (?:khóa|khoá)(?: xe| cửa)?(?: (?:đang|có) (?:bật|hoạt động))?(?: (?:không|chưa|0))?$").matches(normalized) -> VehicleStatusQuery.AUTO_CLOSE_WINDOWS_ON_LOCK
            !Regex("tăng|giảm|chỉnh|đặt|hạ|nâng|\\d").containsMatchIn(normalized) &&
                Regex("nhiệt độ (?:trong xe|trong cabin|cabin)|trong (?:xe|cabin)(?: đang)? bao nhiêu độ|trong (?:xe|cabin)(?: đang)? mấy độ").containsMatchIn(normalized) -> VehicleStatusQuery.CABIN_TEMPERATURE
            Regex("nhiệt độ điều hòa|điều hòa đang bao nhiêu|điều hòa bao nhiêu độ|nhiệt độ đang đặt|đang để bao nhiêu độ").containsMatchIn(normalized) -> VehicleStatusQuery.CLIMATE_TEMPERATURE
            Regex("quạt mức mấy|quạt đang mức mấy|mức quạt hiện tại|tốc độ quạt hiện tại|quạt đang ở mức (?:nào|mấy)|quạt đang bao nhiêu").containsMatchIn(normalized) -> VehicleStatusQuery.FAN_SPEED
            Regex("chế độ lái hiện tại|đang ở chế độ lái (?:nào|gì)|đang chạy chế độ (?:nào|gì)|drive mode hiện tại").containsMatchIn(normalized) -> VehicleStatusQuery.DRIVE_MODE
            Regex("^(?:kiểm tra |xem )?(?:trạng thái đèn viền|đèn viền (?:có |đang |có đang )?(?:bật|sáng|mở) (?:không|0|chưa))$").matches(normalized) -> VehicleStatusQuery.AMBIENT_LIGHT_POWER
            Regex("^(?:kiểm tra |xem )?(?:độ sáng đèn viền(?: hiện tại)?|đèn viền đang sáng mức (?:mấy|nào))$").matches(normalized) -> VehicleStatusQuery.AMBIENT_BRIGHTNESS
            Regex("^(?:kiểm tra |xem )?(?:màu đèn viền(?: hiện tại)?|đèn viền đang (?:là )?màu (?:gì|nào))$").matches(normalized) -> VehicleStatusQuery.AMBIENT_COLOR
            Regex("^(?:kiểm tra |xem )?(?:trạng thái đèn nội thất khi mở cửa|đèn nội thất khi mở cửa đang (?:bật|tắt) (?:không|0))$").matches(normalized) -> VehicleStatusQuery.INTERIOR_LIGHT_ON_DOOR_OPEN
            Regex("^(?:kiểm tra |xem )?(?:trạng thái follow[- ]?me[- ]?home|thời lượng follow[- ]?me[- ]?home(?: hiện tại)?|follow[- ]?me[- ]?home đang (?:đặt )?(?:bao lâu|mấy giây))$").matches(normalized) -> VehicleStatusQuery.FOLLOW_ME_HOME
            Regex("^(?:kiểm tra |xem )?(?:chế độ giao diện(?: hiện tại)?|giao diện màn hình đang (?:ở )?chế độ (?:nào|gì)|theme màn hình hiện tại)$").matches(normalized) -> VehicleStatusQuery.DISPLAY_THEME
            Regex("^(?:kiểm tra |xem )?(?:trạng thái tự giảm gạt mưa khi đỗ|tự giảm gạt mưa khi đỗ đang (?:bật|hoạt động) (?:không|0))$").matches(normalized) -> VehicleStatusQuery.PARKING_WIPER_AUTO_REDUCTION
            Regex("^(?:kiểm tra |xem )?(?:thời lượng chế độ đỗ(?: hiện tại)?|chế độ đỗ đang (?:đặt )?(?:bao lâu|mấy giờ)|trạng thái thời lượng đỗ)$").matches(normalized) -> VehicleStatusQuery.PARKING_MODE_DURATION
            Regex("^(?:kiểm tra |xem )?(?:cảm giác (?:bàn đạp )?phanh(?: hiện tại)?|(?:bàn đạp phanh|phanh) đang ở chế độ (?:nào|gì)|chế độ bàn đạp phanh hiện tại)$").matches(normalized) -> VehicleStatusQuery.BRAKE_PEDAL_FEEL
            Regex("^(?:kiểm tra |xem )?(?:trạng thái (?:chế độ )?dừng êm|(?:chế độ )?dừng êm đang (?:bật|hoạt động) (?:không|0))$").matches(normalized) -> VehicleStatusQuery.COMFORTABLE_STOP_STATUS
            Regex("^(?:kiểm tra |xem )?(?:trạng thái (?:ga tự động|cruise control)|(?:ga tự động|cruise control) đang (?:hoạt động|bật) (?:không|0))$").matches(normalized) -> VehicleStatusQuery.CRUISE_STATUS
            Regex("mức hồi năng lượng hiện tại|hồi năng lượng mức (?:mấy|nào)|regen hiện tại|phanh tái sinh hiện tại|mức tái sinh hiện tại").containsMatchIn(normalized) -> VehicleStatusQuery.REGEN_LEVEL
            else -> return null
        }

        return ParsedVoiceCommand(
            originalText = text,
            normalizedText = normalized,
            command = VoiceCommand.QueryVehicleStatus(query)
        )
    }
}
